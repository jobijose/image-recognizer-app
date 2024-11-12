package com.android.facerecognizer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import com.android.facerecognizer.databinding.ActivityLiveCamBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.opencv.android.OpenCVLoader
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


typealias LumaListener = (luma: Double) -> Unit

class LiveCamActivity1 : AppCompatActivity() {
    private lateinit var viewBinding: ActivityLiveCamBinding
    private lateinit var openCVLoader: OpenCVLoader

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var imageCapture: ImageCapture? = null

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var bitmapBuffer: Bitmap

    private var mqttClient: MQTTClient? = null
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityLiveCamBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        var clientId = MqttClient.generateClientId()
//        mqttClient = MQTTClient(applicationContext, "tcp://192.168.2.5:1883", clientId)
//        mqttConnect()

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.videoCaptureButton.setOnClickListener { captureLiveStreaming() }

        cameraExecutor = Executors.newSingleThreadExecutor()

    }

    // Implements VideoCapture use case, including start and stop capturing.
/*
    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        viewBinding.videoCaptureButton.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@LiveCamActivity1,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        viewBinding.videoCaptureButton.apply {
                            text = getString(R.string.stop_capture)
                            isEnabled = true
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                        }
                        viewBinding.videoCaptureButton.apply {
                            text = getString(R.string.start_capture)
                            isEnabled = true
                        }
                    }
                }
            }
    }
*/

    private fun captureLiveStreaming() {

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)


        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }
//            val recorder = Recorder.Builder()
//                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
//                .build()
//            videoCapture = VideoCapture.withOutput(recorder)
            imageCapture = ImageCapture.Builder().build();
//            mqttConnect()
            val imageAnalysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
//            val previewReader = ImageReader.newInstance(
//                400, 400, ImageFormat.YUV_420_888, 2);
//            previewReader.setOnImageAvailableListener()

            imageAnalysis.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { image ->
                if (!::bitmapBuffer.isInitialized) {

                    bitmapBuffer = Bitmap.createBitmap(
                        image.width, image.height, Bitmap.Config.ARGB_8888)
                }

//                ImageUtils.imageToMat(image.image);
                image.use {
//                    val size = image.width * image.height * 2
//                    val buffer = ByteBuffer.allocateDirect(size)
//
//                    buffer.rewind()
//                    bitmapBuffer.copy(Bitmap.Config.ARGB_8888, true)
                    bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer)
//                    publish(bitmapBuffer)
                    makeHttpCall(bitmapBuffer)
                }
                image.close()
            })


            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider
                    .bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis)
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))

    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).toTypedArray()
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    private fun makeHttpCall(bitmap: Bitmap) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        run(stream.toByteArray())
    }

    private fun run(byteArray: ByteArray) {
        val requestBody: RequestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "filename.jpg",
                byteArray.toRequestBody("image/*jpg".toMediaTypeOrNull(), 0, byteArray.size))
//                RequestBody.create(MediaType.parse("image/*jpg"), byteArray))
            .build()
        val request = Request.Builder()
            .url("http://atom.local:9090/devices/images")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")

                    for ((name, value) in response.headers) {
                        println("$name: $value")
                    }

                    println(response.body!!.string())
                }
            }
        })

    }


    private fun publish(bitmap: Bitmap) {
        if(!mqttClient?.mqttClient!!.isConnected) {
            mqttConnect()
        }

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val byteArray = stream.toByteArray()

        mqttClient?.publish("stream/detection", byteArray, 1, false,
            object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    val msg ="Message published"
                    Log.d(this.javaClass.name, msg)

                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(this.javaClass.name, "Failed to publish message to topic")
                }
            })
//        bitmap.recycle()
    }

    private fun mqttConnect() {
        mqttClient?.connect("abc", "abc",
            object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(this.javaClass.name, "Connection success")

                    Toast.makeText(applicationContext, "MQTT Connection success", Toast.LENGTH_SHORT).show()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(this.javaClass.name, "Connection failure: ${exception}")

                    Toast.makeText(applicationContext, "MQTT Connection fails: ${exception.toString()}",
                        Toast.LENGTH_SHORT).show()
                    throw exception!!
                }
            },
            object : MqttCallback {
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val msg = "Receive message: ${message.toString()} from topic: $topic"
                    Log.d(this.javaClass.name, msg)

                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.d(this.javaClass.name, "Connection lost ${cause.toString()}")
                    throw cause!!
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    Log.d(this.javaClass.name, "Delivery complete")
                }
            })
        println( "hello "+ mqttClient?.mqttClient?.isConnected)
        if(mqttClient?.mqttClient!!.isConnected) {
            mqttClient?.subscribe("stream/detection", 1,
                object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d(this.javaClass.name, "Connection success")

                        Toast.makeText(
                            applicationContext,
                            "MQTT Connection success",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.d(this.javaClass.name, "Connection failure: ${exception.toString()}")

                        Toast.makeText(
                            applicationContext, "MQTT Connection fails: ${exception.toString()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
        }
    }

}