package com.example.motiondetector

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.motiondetector.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private val logAdapter = DetectionLogAdapter()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val db by lazy { AppDatabase.getInstance(applicationContext) }

    private var objectDetector: ObjectDetector? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var isRunning = false

    private var personPresent = false
    private var vehiclePresent = false
    private var personHitStreak = 0
    private var personMissStreak = 0
    private var vehicleHitStreak = 0
    private var vehicleMissStreak = 0
    private var personCount = 0
    private var vehicleCount = 0

    // --- Video clip recording state ---
    private var recorder: Recorder? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var isRecordingClip = false
    private val stopRecordingRunnable = Runnable { stopVideoRecording() }
    private var recordingTickRunnable: Runnable? = null

    companion object {
        // Raised from 0.55 - fewer low-confidence false positives (e.g. objects
        // misread as "person"), at the cost of being slightly less sensitive.
        private const val SCORE_THRESHOLD = 0.65f
        private const val MAX_RESULTS = 10
        private const val MODEL_FILE = "model.tflite"
        private const val CLIP_DURATION_MS = 10_000L

        // A detection must hold for this many consecutive analyzed frames
        // before it's treated as a real, new event. This filters out the
        // one-frame flickers (motion blur, odd lighting, a box briefly
        // resembling a person) that cause false "person detected" events.
        private const val CONFIRM_HITS = 3
        // Frames of absence required before we consider the object gone,
        // so a detection doesn't flicker on/off and spam new events.
        private const val CONFIRM_MISSES = 2

        // COCO label -> Korean display name for the classes we care about.
        private val VEHICLE_LABELS = mapOf(
            "car" to "자동차",
            "truck" to "트럭",
            "bus" to "버스",
            "motorcycle" to "오토바이",
            "bicycle" to "자전거"
        )
    }

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) { // API 28 and below
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            arrayOf(Manifest.permission.CAMERA)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            if (allGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "카메라 및 저장소 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.detectionList.layoutManager = LinearLayoutManager(this)
        binding.detectionList.adapter = logAdapter
        refreshListSummary()

        binding.startBtn.setOnClickListener {
            if (isRunning) stopCamera() else requestCameraAndStart()
        }

        binding.flipBtn.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            if (isRunning) bindCameraUseCases()
        }

        binding.clearBtn.setOnClickListener {
            logAdapter.clear()
            personCount = 0
            vehicleCount = 0
            binding.personCountChip.text = "사람 0"
            binding.vehicleCountChip.text = "차량 0"
            refreshListSummary()
        }

        binding.historyBtn.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    private fun requestCameraAndStart() {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(requiredPermissions())
        }
    }

    private fun loadDetectorIfNeeded(): Boolean {
        if (objectDetector != null) return true
        return try {
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setNumThreads(4).build())
                .setMaxResults(MAX_RESULTS)
                .setScoreThreshold(SCORE_THRESHOLD)
                .build()
            objectDetector = ObjectDetector.createFromFileAndOptions(this, MODEL_FILE, options)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            showOverlayMessage(
                "모델 파일을 불러오지 못했습니다.\n" +
                    "app/src/main/assets/$MODEL_FILE 파일이 있는지 확인해주세요.\n" +
                    "(README.md의 모델 다운로드 안내 참고)"
            )
            false
        }
    }

    private fun startCamera() {
        if (!loadDetectorIfNeeded()) return

        binding.startBtn.text = "준비 중..."
        binding.startBtn.isEnabled = false

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            bindCameraUseCases()

            isRunning = true
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            binding.startBtn.text = "카메라 정지"
            binding.startBtn.isEnabled = true
            binding.startBtn.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.red)
            binding.overlayMsg.visibility = android.view.View.GONE
            binding.liveBadge.visibility = android.view.View.VISIBLE
            binding.countsRow.visibility = android.view.View.VISIBLE
            binding.flipBtn.visibility = android.view.View.VISIBLE
            setStatus("감지 중", R.color.green)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy -> analyzeFrame(imageProxy) }
            }

        // Build (or reuse) the Recorder that backs the 10-second clip feature.
        if (recorder == null) {
            val qualitySelector = QualitySelector.from(
                Quality.HD,
                FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
            )
            recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
        }
        val videoCaptureUseCase = VideoCapture.withOutput(recorder!!)
        videoCapture = videoCaptureUseCase

        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        try {
            camera = provider.bindToLifecycle(
                this, selector, preview, imageAnalysis, videoCaptureUseCase
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "카메라를 시작할 수 없습니다: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopCamera() {
        isRunning = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopVideoRecording()
        cameraProvider?.unbindAll()
        binding.overlayView.clear()
        binding.overlayMsg.visibility = android.view.View.VISIBLE
        binding.overlayMsg.text = "카메라를 시작하려면 아래 버튼을 눌러주세요"
        binding.liveBadge.visibility = android.view.View.GONE
        binding.countsRow.visibility = android.view.View.GONE
        binding.flipBtn.visibility = android.view.View.GONE
        binding.startBtn.text = "카메라 시작"
        binding.startBtn.backgroundTintList = ContextCompat.getColorStateList(this, R.color.green)
        setStatus("대기 중", R.color.text_muted)
        personPresent = false
        vehiclePresent = false
        personHitStreak = 0
        personMissStreak = 0
        vehicleHitStreak = 0
        vehicleMissStreak = 0
    }

    private fun showOverlayMessage(message: String) {
        runOnUiThread {
            binding.overlayMsg.visibility = android.view.View.VISIBLE
            binding.overlayMsg.text = message
            setStatus("오류", R.color.red)
        }
    }

    private fun setStatus(text: String, colorRes: Int) {
        binding.statusText.text = text
        binding.statusDot.setBackgroundColor(ContextCompat.getColor(this, colorRes))
    }

    /** Runs on the cameraExecutor background thread. */
    private fun analyzeFrame(imageProxy: ImageProxy) {
        val detector = objectDetector
        if (detector == null) {
            imageProxy.close()
            return
        }

        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val mirror = lensFacing == CameraSelector.LENS_FACING_FRONT
            val prepared = rotateAndMirrorBitmap(bitmap, rotationDegrees, mirror)

            val tensorImage = TensorImage.fromBitmap(prepared)
            val results = detector.detect(tensorImage)

            val boxes = mutableListOf<DetectionOverlayView.Box>()
            var bestPersonScore = -1f
            var bestVehicleScore = -1f
            var bestVehicleLabel = "차량"

            for (detection in results) {
                val category = detection.categories.firstOrNull() ?: continue
                val label = category.label?.lowercase() ?: continue
                val score = category.score

                val isPerson = label == "person"
                val vehicleKorean = VEHICLE_LABELS[label]
                val isVehicle = vehicleKorean != null
                if (!isPerson && !isVehicle) continue
                if (score < SCORE_THRESHOLD) continue

                boxes.add(
                    DetectionOverlayView.Box(
                        rect = RectF(detection.boundingBox),
                        label = if (isPerson) "사람" else (vehicleKorean ?: "차량"),
                        score = score,
                        isPerson = isPerson
                    )
                )

                if (isPerson && score > bestPersonScore) {
                    bestPersonScore = score
                } else if (isVehicle && score > bestVehicleScore) {
                    bestVehicleScore = score
                    bestVehicleLabel = vehicleKorean ?: "차량"
                }
            }

            runOnUiThread {
                binding.overlayView.setResults(boxes, prepared.width, prepared.height)
                handleDetectionState(bestPersonScore, bestVehicleScore, bestVehicleLabel)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Runs on the main thread. Confirms a detection only after it holds for
     * [CONFIRM_HITS] consecutive frames (filters single-frame false positives),
     * and clears it only after [CONFIRM_MISSES] consecutive absent frames
     * (avoids flicker spamming new events).
     */
    private fun handleDetectionState(personScore: Float, vehicleScore: Float, vehicleLabel: String) {
        val hasPersonThisFrame = personScore >= SCORE_THRESHOLD
        val hasVehicleThisFrame = vehicleScore >= SCORE_THRESHOLD
        var newEventFired = false

        // --- Person ---
        if (hasPersonThisFrame) {
            personHitStreak++
            personMissStreak = 0
        } else {
            personMissStreak++
            personHitStreak = 0
        }
        if (!personPresent && personHitStreak >= CONFIRM_HITS) {
            personPresent = true
            personCount++
            binding.personCountChip.text = "사람 $personCount"
            val record = DetectionRecord(DetectionType.PERSON, "사람", personScore)
            logAdapter.addRecord(record)
            saveRecordToDb(record)
            refreshListSummary()
            newEventFired = true
        } else if (personPresent && personMissStreak >= CONFIRM_MISSES) {
            personPresent = false
        }

        // --- Vehicle ---
        if (hasVehicleThisFrame) {
            vehicleHitStreak++
            vehicleMissStreak = 0
        } else {
            vehicleMissStreak++
            vehicleHitStreak = 0
        }
        if (!vehiclePresent && vehicleHitStreak >= CONFIRM_HITS) {
            vehiclePresent = true
            vehicleCount++
            binding.vehicleCountChip.text = "차량 $vehicleCount"
            val record = DetectionRecord(DetectionType.VEHICLE, vehicleLabel, vehicleScore)
            logAdapter.addRecord(record)
            saveRecordToDb(record)
            refreshListSummary()
            newEventFired = true
        } else if (vehiclePresent && vehicleMissStreak >= CONFIRM_MISSES) {
            vehiclePresent = false
        }

        if (newEventFired && binding.saveVideoCheckbox.isChecked && !isRecordingClip) {
            startVideoRecording()
        }
    }

    private fun saveRecordToDb(record: DetectionRecord) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.detectionDao().insert(record)
        }
    }

    private fun refreshListSummary() {
        val count = logAdapter.size()
        binding.listSummary.text = "${count}건"
        binding.emptyText.visibility = if (count == 0) android.view.View.VISIBLE else android.view.View.GONE
        binding.detectionList.visibility = if (count == 0) android.view.View.GONE else android.view.View.VISIBLE
    }

    // --- Video clip recording -------------------------------------------------

    private fun startVideoRecording() {
        val videoCaptureUseCase = videoCapture ?: return
        if (isRecordingClip) return

        val fileName = "MotionDetector_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date()) + ".mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MotionDetector")
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues)
            .build()

        isRecordingClip = true
        binding.recordingBadge.visibility = android.view.View.VISIBLE
        binding.flipBtn.isEnabled = false
        startRecordingCountdown()

        activeRecording = videoCaptureUseCase.output
            .prepareRecording(this, outputOptions)
            .start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Finalize -> {
                        isRecordingClip = false
                        activeRecording = null
                        binding.recordingBadge.visibility = android.view.View.GONE
                        binding.flipBtn.isEnabled = true
                        recordingTickRunnable?.let { mainHandler.removeCallbacks(it) }
                        if (event.hasError()) {
                            Toast.makeText(
                                this,
                                "영상 저장에 실패했습니다: ${event.cause?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(this, "영상이 저장되었습니다: $fileName", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {
                        // Started / Status / Pause / Resume events - no action needed.
                    }
                }
            }

        mainHandler.removeCallbacks(stopRecordingRunnable)
        mainHandler.postDelayed(stopRecordingRunnable, CLIP_DURATION_MS)
    }

    private fun startRecordingCountdown() {
        val startedAt = System.currentTimeMillis()
        recordingTickRunnable?.let { mainHandler.removeCallbacks(it) }
        val tick = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startedAt
                val remainingSec = ((CLIP_DURATION_MS - elapsed) / 1000L).coerceAtLeast(0L)
                binding.recordingTimeText.text = "REC 0:${remainingSec.toString().padStart(2, '0')}"
                if (isRecordingClip && remainingSec > 0) {
                    mainHandler.postDelayed(this, 250L)
                }
            }
        }
        recordingTickRunnable = tick
        mainHandler.post(tick)
    }

    private fun stopVideoRecording() {
        mainHandler.removeCallbacks(stopRecordingRunnable)
        activeRecording?.stop()
        // isRecordingClip / badge visibility are reset inside the Finalize event above.
        // If there was no active recording, make sure state doesn't get stuck.
        if (activeRecording == null) {
            isRecordingClip = false
            binding.recordingBadge.visibility = android.view.View.GONE
            binding.flipBtn.isEnabled = true
        }
    }

    /**
     * Converts an [ImageProxy] captured with OUTPUT_IMAGE_FORMAT_RGBA_8888 into a [Bitmap].
     * The plane's row stride can be larger than width * 4 bytes, so extra padding columns
     * are cropped off after copying the buffer into a Bitmap.
     */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        return if (rowPadding == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        }
    }

    private fun rotateAndMirrorBitmap(source: Bitmap, rotationDegrees: Int, mirror: Boolean): Bitmap {
        val matrix = Matrix()
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }
        if (mirror) {
            matrix.postScale(-1f, 1f)
        }
        if (matrix.isIdentity) return source
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVideoRecording()
        cameraExecutor.shutdown()
        objectDetector?.close()
    }
}
