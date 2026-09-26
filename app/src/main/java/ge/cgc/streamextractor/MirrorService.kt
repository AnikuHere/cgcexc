package ge.cgc.streamextractor

import android.app.Service
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Surface
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class MirrorService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_PUBLIC_BASE = "public_base"

        private const val SERVICE_NOTIFICATION_ID = 20
        private const val SERVER_PORT = 8080
        private const val WIDTH = 1280
        private const val HEIGHT = 720
        private const val FPS = 30
        private const val BITRATE = 2_500_000
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val running = AtomicBoolean(false)

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var server: LocalHlsServer? = null
    private var hls: TsHlsWriter? = null
    private var encoderJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)

        ServiceCompat.startForeground(
            this,
            SERVICE_NOTIFICATION_ID,
            Notifications.foreground(this),
            if (Build.VERSION.SDK_INT >= 29)
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            else 0
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running.get()) return START_NOT_STICKY

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0)
            ?: return START_NOT_STICKY

        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        } ?: return START_NOT_STICKY

        val publicBase = intent.getStringExtra(EXTRA_PUBLIC_BASE)?.trim().orEmpty()

        running.set(true)
        scope.launch {
            try {
                startMirror(resultCode, resultData, publicBase)
            } catch (t: Throwable) {
                Notifications.warning(
                    this@MirrorService,
                    "Mirror failed: ${t.message ?: "unknown error"}"
                )
            } finally {
                running.set(false)
                stopMirror()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun startMirror(
        resultCode: Int,
        resultData: Intent,
        publicBase: String
    ) {
        server = LocalHlsServer(SERVER_PORT)
        server!!.start()

        val localUrl = "http://127.0.0.1:$SERVER_PORT/stream/index.m3u8"
        val publicUrl = buildPublicUrl(publicBase)

        val manager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        projection = manager.getMediaProjection(resultCode, resultData)
        projection!!.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    running.set(false)
                }
            },
            null
        )

        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            WIDTH,
            HEIGHT
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        inputSurface = codec!!.createInputSurface()
        codec!!.start()

        hls = TsHlsWriter(server!!, cacheDir = File(cacheDir, "hls"))

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        getSystemService(android.view.WindowManager::class.java)
            .defaultDisplay.getRealMetrics(metrics)

        virtualDisplay = projection!!.createVirtualDisplay(
            "CGCStreamExtractorMirror",
            WIDTH,
            HEIGHT,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface,
            null,
            null
        )

        // Start encoding first. The first .ts segment can only exist after
        // MediaCodec has emitted a keyframe.
        encoderJob = scope.launch(Dispatchers.IO) {
            try {
                encodeLoop()
            } catch (_: CancellationException) {
            } catch (t: Throwable) {
                Notifications.warning(
                    this@MirrorService,
                    "Encoder stopped: ${t.message ?: "unknown error"}"
                )
                running.set(false)
            }
        }

        // Wait for a real playlist with at least one EXTINF/segment.
        val localReady = waitForPlaylist(localUrl, 20_000L)
        if (!localReady) {
            throw IllegalStateException("HLS playlist did not become ready")
        }

        // A public base is optional. If supplied, verify the public URL from
        // inside the emulator before including it in the notification.
        val verifiedPublic = if (publicUrl != null) {
            if (waitForPlaylist(publicUrl, 15_000L)) publicUrl else null
        } else null

        Notifications.ready(this, localUrl, verifiedPublic)

        encoderJob?.join()
    }

    private fun encodeLoop() {
        val info = MediaCodec.BufferInfo()

        while (running.get()) {
            when (val index = codec!!.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    hls!!.setCodecConfig(codec!!.outputFormat)
                }

                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                else -> if (index >= 0) {
                    val buffer = codec!!.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)

                        val data = ByteArray(info.size)
                        buffer.get(data)

                        hls!!.onAccessUnit(
                            data = data,
                            ptsUs = info.presentationTimeUs,
                            flags = info.flags
                        )
                    }

                    codec!!.releaseOutputBuffer(index, false)
                }
            }
        }
    }

    private fun waitForPlaylist(url: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline && running.get()) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 1500
                conn.readTimeout = 1500
                conn.requestMethod = "GET"
                conn.useCaches = false

                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    if (body.contains("#EXTM3U") && body.contains("#EXTINF:")) {
                        return true
                    }
                } else {
                    conn.disconnect()
                }
            } catch (_: Throwable) {
            }

            Thread.sleep(400)
        }
        return false
    }

    private fun buildPublicUrl(publicBase: String): String? {
        if (publicBase.isBlank()) return null
        return publicBase.trimEnd('/') + "/stream/index.m3u8"
    }

    private fun stopMirror() {
        encoderJob?.cancel()
        encoderJob = null

        try { hls?.stop() } catch (_: Throwable) {}
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        try { inputSurface?.release() } catch (_: Throwable) {}
        try { codec?.stop() } catch (_: Throwable) {}
        try { codec?.release() } catch (_: Throwable) {}
        try { projection?.stop() } catch (_: Throwable) {}
        try { server?.stop() } catch (_: Throwable) {}

        hls = null
        virtualDisplay = null
        inputSurface = null
        codec = null
        projection = null
        server = null
    }

    override fun onDestroy() {
        running.set(false)
        stopMirror()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
