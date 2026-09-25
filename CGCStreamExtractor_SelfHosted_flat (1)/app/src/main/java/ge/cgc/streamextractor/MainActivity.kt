package ge.cgc.streamextractor

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_CAPTURE = 5001
        private const val REQUEST_NOTIFICATIONS = 5002
    }

    private lateinit var publicBaseInput: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Notifications.createChannel(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
        }

        val title = TextView(this).apply {
            text = "CGCStreamExtractor"
            textSize = 24f
            setPadding(24, 24, 24, 8)
        }

        val help = TextView(this).apply {
            text = "Mirror mode hosts the HLS stream directly from this Android emulator. " +
                "Enter the public URL/base address supplied by your online emulator provider " +
                "only if it exposes an incoming HTTP port to this app."
            textSize = 15f
            setPadding(24, 8, 24, 12)
        }

        publicBaseInput = EditText(this).apply {
            hint = "Public base URL (optional)"
            textSize = 16f
            setSingleLine(true)
            setPadding(24, 8, 24, 8)
        }

        status = TextView(this).apply {
            text = "Ready."
            textSize = 16f
            setPadding(24, 12, 24, 12)
        }

        val start = Button(this).apply {
            text = "Mirror"
            setOnClickListener { askStartMirror() }
        }

        val stop = Button(this).apply {
            text = "Stop mirror"
            setOnClickListener {
                stopService(Intent(this@MainActivity, MirrorService::class.java))
                status.text = "Mirror stopped."
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(help)
            addView(publicBaseInput)
            addView(status)
            addView(start)
            addView(stop)
        }

        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun askStartMirror() {
        AlertDialog.Builder(this)
            .setTitle("Start mirror?")
            .setMessage(
                "The app will ask Android for screen-capture permission. " +
                "After you approve it, there is a 5-second countdown before mirroring begins."
            )
            .setNegativeButton("No", null)
            .setPositiveButton("Yes") { _, _ -> requestScreenCapture() }
            .show()
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return

        if (resultCode != RESULT_OK || data == null) {
            status.text = "Screen capture permission was not granted."
            return
        }

        status.text = "Starting mirror in 5 seconds…"

        object : CountDownTimer(5000, 1000) {
            override fun onTick(remaining: Long) {
                status.text = "Starting mirror in ${(remaining + 999L) / 1000L}…"
            }

            override fun onFinish() {
                val base = publicBaseInput.text.toString().trim()

                val serviceIntent = Intent(
                    this@MainActivity,
                    MirrorService::class.java
                ).apply {
                    putExtra(MirrorService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(MirrorService.EXTRA_RESULT_DATA, data)
                    putExtra(MirrorService.EXTRA_PUBLIC_BASE, base)
                }

                ContextCompat.startForegroundService(
                    this@MainActivity,
                    serviceIntent
                )
                status.text = "Mirror service started."
            }
        }.start()
    }
}
