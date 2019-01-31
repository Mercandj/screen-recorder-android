package com.mercandalli.android.apps.screen_recorder.main

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.SparseIntArray
import android.view.Surface
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.snackbar.Snackbar
import com.mercandalli.android.apps.screen_recorder.R
import com.mercandalli.android.apps.screen_recorder.activity.ActivityExtension.bind

import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val startButton: Button by bind(R.id.activity_main_start)
    private val mediaProjectionManager by lazy { createMediaProjectionManager() }
    private val mediaRecorder = MediaRecorder()
    private val screenDensityDpi by lazy { createScreenDensityDpi() }
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaProjectionCallback: MediaProjectionCallback? = null
    private var isRecording = false

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (!hasPermissions(this, *permissions)) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION_KEY)
        }
        startButton.setOnClickListener { onToggleScreenShare() }
    }

    private fun createScreenDensityDpi(): Int {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        return metrics.densityDpi
    }

    public override fun onDestroy() {
        super.onDestroy()
        destroyMediaProjection()
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_CODE) {
            return
        }
        if (resultCode != Activity.RESULT_OK) {
            Toast.makeText(this, "Screen Cast Permission Denied", Toast.LENGTH_SHORT).show()
            isRecording = false
            syncStartButton()
            return
        }
        mediaProjectionCallback = MediaProjectionCallback()
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data!!)
        mediaProjection!!.registerCallback(mediaProjectionCallback, null)
        virtualDisplay = createVirtualDisplay()
        mediaRecorder.start()
        isRecording = true
        syncStartButton()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_PERMISSION_KEY -> {
                if (grantResults.isNotEmpty() && grantResults[0] + grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    onToggleScreenShare()
                    return
                }
                isRecording = false
                syncStartButton()
                Snackbar.make(
                    findViewById(android.R.id.content), "Please enable Microphone and Storage permissions.",
                    Snackbar.LENGTH_INDEFINITE
                ).setAction(
                    "ENABLE"
                ) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    intent.addCategory(Intent.CATEGORY_DEFAULT)
                    intent.data = Uri.parse("package:$packageName")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    startActivity(intent)
                }.show()
            }
        }
    }

    override fun onBackPressed() {
        if (isRecording) {
            Snackbar.make(
                findViewById(android.R.id.content), "Wanna Stop recording and exit?",
                Snackbar.LENGTH_INDEFINITE
            ).setAction(
                "Stop"
            ) {
                mediaRecorder.stop()
                mediaRecorder.reset()
                stopScreenSharing()
                finish()
            }.show()
        } else {
            finish()
        }
    }

    private fun syncStartButton() {
        startButton.text = if (isRecording) {
            "Stop Recording"
        } else {
            "Start Recording"
        }
    }

    private fun onToggleScreenShare() {
        if (!isRecording) {
            initRecorder()
            shareScreen()
        } else {
            mediaRecorder.stop()
            mediaRecorder.reset()
            stopScreenSharing()
        }
    }

    private fun shareScreen() {
        if (mediaProjection == null) {
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE)
            return
        }
        virtualDisplay = createVirtualDisplay()
        mediaRecorder.start()
        isRecording = true
        syncStartButton()
    }

    private fun createVirtualDisplay(): VirtualDisplay {
        return mediaProjection!!.createVirtualDisplay(
            "MainActivity",
            DISPLAY_WIDTH,
            DISPLAY_HEIGHT,
            screenDensityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder.surface,
            null,
            null
        )
    }

    private fun initRecorder() {
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4) //THREE_GPP
            mediaRecorder.setOutputFile(Environment.getExternalStorageDirectory().toString() + "/video.mp4")
            mediaRecorder.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT)
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            mediaRecorder.setVideoEncodingBitRate(512 * 1_000)
            mediaRecorder.setVideoFrameRate(16) // 30
            mediaRecorder.setVideoEncodingBitRate(3_000_000)
            val rotation = windowManager.defaultDisplay.rotation
            val orientation = ORIENTATIONS.get(rotation + 90)
            mediaRecorder.setOrientationHint(orientation)
            mediaRecorder.prepare()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun stopScreenSharing() {
        if (virtualDisplay == null) {
            return
        }
        virtualDisplay!!.release()
        destroyMediaProjection()
        isRecording = false
        syncStartButton()
    }

    private fun destroyMediaProjection() {
        if (mediaProjection != null) {
            mediaProjection!!.unregisterCallback(mediaProjectionCallback)
            mediaProjection!!.stop()
            mediaProjection = null
        }
    }

    private fun createMediaProjectionManager() = getSystemService(
        Context.MEDIA_PROJECTION_SERVICE
    ) as MediaProjectionManager

    private inner class MediaProjectionCallback : MediaProjection.Callback() {
        override fun onStop() {
            if (isRecording) {
                isRecording = false
                syncStartButton()
                mediaRecorder.stop()
                mediaRecorder.reset()
            }
            mediaProjection = null
            stopScreenSharing()
        }
    }

    companion object {

        private const val REQUEST_CODE = 1_000
        private const val DISPLAY_WIDTH = 720
        private const val DISPLAY_HEIGHT = 1_280
        private val ORIENTATIONS = SparseIntArray()
        private const val REQUEST_PERMISSION_KEY = 1
        private val permissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        private fun hasPermissions(context: Context?, vararg permissions: String): Boolean {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null) {
                for (permission in permissions) {
                    if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                        return false
                    }
                }
            }
            return true
        }
    }
}