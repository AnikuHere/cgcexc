package ge.cgc.streamextractor

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.Locale

/**
 * HLS segmenter backed by Android's MPEG-2 TS MediaMuxer.
 *
 * The encoder supplies H.264 access units. We start/rotate a .ts segment only
 * on keyframes, then publish a sliding HLS playlist.
 */
class TsHlsWriter(
    private val server: LocalHlsServer,
    private val cacheDir: File,
    private val targetDurationSeconds: Long = 2,
    private val windowSize: Int = 6
) {
    private data class Segment(
        val name: String,
        val duration: Double
    )

    private val segments = ArrayDeque<Segment>()
    private var segmentIndex = 0L

    private var muxer: MediaMuxer? = null
    private var videoTrack = -1
    private var format: MediaFormat? = null

    private var segmentStartPtsUs = -1L
    private var lastPtsUs = -1L
    private var wroteSample = false

    init {
        cacheDir.mkdirs()
    }

    fun setCodecConfig(outputFormat: MediaFormat) {
        format = outputFormat
    }

    fun onAccessUnit(
        data: ByteArray,
        ptsUs: Long,
        flags: Int
    ) {
        val isKeyFrame = (flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

        // HLS segments begin with a keyframe.
        if (muxer == null) {
            if (!isKeyFrame || format == null) return
            startSegment(ptsUs)
        } else if (
            isKeyFrame &&
            segmentStartPtsUs >= 0 &&
            ptsUs - segmentStartPtsUs >= targetDurationSeconds * 1_000_000L
        ) {
            finishSegment(ptsUs)
            startSegment(ptsUs)
        }

        val currentMuxer = muxer ?: return
        val info = MediaCodec.BufferInfo().apply {
            offset = 0
            size = data.size
            presentationTimeUs = ptsUs
            this.flags = flags
        }

        currentMuxer.writeSampleData(
            videoTrack,
            ByteBuffer.wrap(data),
            info
        )

        wroteSample = true
        lastPtsUs = ptsUs
    }

    fun stop() {
        if (muxer != null && wroteSample) {
            finishSegment(lastPtsUs)
        } else {
            releaseMuxer()
        }
    }

    private fun startSegment(startPtsUs: Long) {
        val codecFormat = format ?: return

        val name = "seg%06d.ts".format(segmentIndex++)
        val file = File(cacheDir, name)

        muxer = MediaMuxer(
            file.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_2_TS
        )

        videoTrack = muxer!!.addTrack(codecFormat)
        muxer!!.start()

        segmentStartPtsUs = startPtsUs
        lastPtsUs = startPtsUs
        wroteSample = false
    }

    private fun finishSegment(endPtsUs: Long) {
        val currentMuxer = muxer ?: return
        val name = "seg%06d.ts".format(segmentIndex - 1)
        val file = File(cacheDir, name)

        try {
            currentMuxer.stop()
        } catch (_: Throwable) {
            try { currentMuxer.release() } catch (_: Throwable) {}
            muxer = null
            videoTrack = -1
            file.delete()
            return
        }

        currentMuxer.release()
        muxer = null
        videoTrack = -1

        if (!file.exists() || file.length() == 0L) {
            file.delete()
            return
        }

        val duration = if (segmentStartPtsUs >= 0 && endPtsUs >= segmentStartPtsUs) {
            (endPtsUs - segmentStartPtsUs) / 1_000_000.0
        } else {
            0.001
        }

        val bytes = file.readBytes()
        file.delete()

        server.addSegment(name, bytes)
        segments.addLast(Segment(name, duration))

        while (segments.size > windowSize) {
            val old = segments.removeFirst()
            server.removeSegment(old.name)
        }

        publishPlaylist()
        segmentStartPtsUs = -1L
        lastPtsUs = -1L
        wroteSample = false
    }

    private fun releaseMuxer() {
        try { muxer?.stop() } catch (_: Throwable) {}
        try { muxer?.release() } catch (_: Throwable) {}
        muxer = null
        videoTrack = -1
    }

    private fun publishPlaylist() {
        val maxDuration = maxOf(
            targetDurationSeconds,
            kotlin.math.ceil(
                segments.maxOfOrNull { it.duration } ?: targetDurationSeconds.toDouble()
            ).toLong()
        )

        val mediaSequence =
            segments.firstOrNull()?.name?.filter { it.isDigit() }?.toLongOrNull() ?: 0L

        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        sb.append("#EXT-X-VERSION:3\n")
        sb.append("#EXT-X-TARGETDURATION:").append(maxDuration).append('\n')
        sb.append("#EXT-X-MEDIA-SEQUENCE:").append(mediaSequence).append('\n')
        sb.append("#EXT-X-INDEPENDENT-SEGMENTS\n")

        for (segment in segments) {
            sb.append("#EXTINF:")
                .append(String.format(Locale.US, "%.3f", segment.duration))
                .append(",\n")
            sb.append("/stream/").append(segment.name).append('\n')
        }

        server.setPlaylist(sb.toString())
    }
}
