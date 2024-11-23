package com.android.facerecognizer

import android.Manifest
import android.content.Context
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
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.android.facerecognizer.databinding.ActivityLiveCamBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class LiveCamActivity : AppCompatActivity(), HostAddressDialog.HostAddressDialogListener {
    private lateinit var viewBinding: ActivityLiveCamBinding

    private var imageCapture: ImageCapture? = null

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var bitmapBuffer: Bitmap

    private val client = OkHttpClient()
    private lateinit var hostAddressDialog: HostAddressDialog

    private lateinit var hostAddressValue: String
    private var cameraFrequencyValue: Int = 5
    private var epoch = Instant.now().epochSecond

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityLiveCamBinding.inflate(layoutInflater)
        viewBinding.root.keepScreenOn = true
        setContentView(viewBinding.root)
        hostAddressDialog = HostAddressDialog()
        fetchHostAddress()
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.btnSettings.setOnClickListener { showDialog() }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun showDialog() {
        val bundle = Bundle()
        bundle.putString(HOST_ADDRESS, hostAddressValue)
        bundle.putInt(FREQUENCY_VALUE, cameraFrequencyValue)
        hostAddressDialog.arguments = bundle
        hostAddressDialog.show(supportFragmentManager, HOST_ADDRESS)
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

            imageCapture = ImageCapture.Builder().build();
            val imageAnalysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { image ->
                if (!::bitmapBuffer.isInitialized) {

                    bitmapBuffer = Bitmap.createBitmap(
                        image.width, image.height, Bitmap.Config.ARGB_8888)
                }

                image.use {
                    bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer)
                    makeHttpCallInScheduledFrequency(bitmapBuffer)
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

    private fun makeHttpCallInScheduledFrequency(bitmap: Bitmap) {
        val currEpoch = Instant.now().epochSecond
        if (currEpoch - epoch > cameraFrequencyValue) {
            makeHttpCall(bitmap)
            epoch = currEpoch
        }
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
        private const val TAG = "LiveCamActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).toTypedArray()
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
        internal const val HOST_ADDRESS = "HOST_ADDRESS"
        internal const val FREQUENCY_VALUE = "FREQUENCY_VALUE"
        private val HOST_ADDR_KEY = stringPreferencesKey(HOST_ADDRESS)
        private val FREQUENCY_KEY = intPreferencesKey(FREQUENCY_VALUE)
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
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
            .addFormDataPart("image", "live_img.jpg",
                byteArray.toRequestBody("image/*jpg".toMediaTypeOrNull(), 0, byteArray.size))
            .build()
        val request = Request.Builder()
            .url("$hostAddressValue/devices/images")
            .post(requestBody)
            .build()
        Log.d(TAG, "Calling url ${request.url}")
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {

                    if (!response.isSuccessful) throw IOException("Unexpected code $response")

                    for ((name, value) in response.headers) {
                        Log.i(TAG,"$name: $value")
                    }

                    Log.i(TAG, response.body!!.string())
                }
            }
        })

    }
    private fun fetchHostAddress() {
        runBlocking {
            hostAddressValue = dataStore.data
                .map { preferences ->
                    preferences[HOST_ADDR_KEY] ?: "http://"
                }.first()
            cameraFrequencyValue = dataStore.data
                .map { preferences ->
                    preferences[FREQUENCY_KEY] ?: 5
                }.first()
        }
    }

    override fun onSubmit(hostAddress: String, frequencyValue: Int) {
        Log.d(TAG, "Host address = $hostAddress; Frequency value = $frequencyValue")
        if (hostAddress.isNotEmpty()) {
            lifecycleScope.launch {
                dataStore.edit { settings ->
                    settings[HOST_ADDR_KEY] = hostAddress
                    settings[FREQUENCY_KEY] = frequencyValue
                }
                hostAddressValue = hostAddress
                cameraFrequencyValue = frequencyValue
            }
        }
    }
}