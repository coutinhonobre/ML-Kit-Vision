package com.github.coutinhonobre.mlkitvision

import android.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.coutinhonobre.mlkitvision.GraphicOverlay.Graphic
import com.github.coutinhonobre.mlkitvision.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import java.io.InputStream
import java.util.PriorityQueue
import kotlin.math.max


class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {
    private val TAG = "MainActivity"

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private var mSelectedImage: Bitmap? = null

    // Max width (portrait mode)
    private var mImageMaxWidth: Int? = null

    // Max height (portrait mode)
    private var mImageMaxHeight: Int? = null

    /**
     * Number of results to show in the UI.
     */
    private val RESULTS_TO_SHOW = 3

    /**
     * Dimensions of inputs.
     */
    private val DIM_BATCH_SIZE = 1
    private val DIM_PIXEL_SIZE = 3
    private val DIM_IMG_SIZE_X = 224
    private val DIM_IMG_SIZE_Y = 224

    private val sortedLabels = PriorityQueue<Map.Entry<String, Float>>(
        RESULTS_TO_SHOW
    ) { o1, o2 -> o1!!.value!!.compareTo(o2!!.value!!) }

    /* Preallocated buffers for storing image data. */
    private val intValues = IntArray(DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        with(binding) {
            buttonText.setOnClickListener { runTextRecognition() }
            buttonFace.setOnClickListener { runFaceContourDetection() }
            val items = arrayOf("Test Image 1 (Text)", "Test Image 2 (Face)")
            val adapter = ArrayAdapter(this@MainActivity, R.layout.simple_spinner_dropdown_item, items)
            spinner.adapter = adapter
            spinner.onItemSelectedListener = this@MainActivity
        }
    }

    private fun runTextRecognition() {
        val image = InputImage.fromBitmap(mSelectedImage!!, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        with(binding) {
            buttonText.isEnabled = false
            recognizer.process(image)
                .addOnSuccessListener { texts ->
                    buttonText.isEnabled = true
                    processTextRecognitionResult(texts!!)
                }
                .addOnFailureListener { e -> // Task failed with an exception
                    buttonText.isEnabled = true
                    e.printStackTrace()
                }
        }
    }

    private fun processTextRecognitionResult(texts: Text) {
        val blocks = texts.textBlocks
        if (blocks.size == 0) {
            showToast("No text found")
            return
        }
        with(binding.graphicOverlay) {
            clear()
            for (i in blocks.indices) {
                val lines = blocks[i].lines
                for (j in lines.indices) {
                    val elements = lines[j].elements
                    for (k in elements.indices) {
                        val textGraphic: Graphic = TextGraphic(this, elements[k])
                        add(textGraphic)
                    }
                }
            }
        }
    }

    private fun runFaceContourDetection() {
        val image = InputImage.fromBitmap(mSelectedImage!!, 0)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()
        with(binding.buttonFace) {
            isEnabled = false
            val detector: FaceDetector = FaceDetection.getClient(options)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    isEnabled = true
                    processFaceContourDetectionResult(faces)
                }
                .addOnFailureListener { e -> // Task failed with an exception
                    isEnabled = true
                    e.printStackTrace()
                }
        }
    }

    private fun processFaceContourDetectionResult(faces: List<Face>) {
        // Task completed successfully
        if (faces.isEmpty()) {
            showToast("No face found")
            return
        }
        with(binding.graphicOverlay) {
            clear()
            for (i in faces.indices) {
                val face = faces[i]
                val faceGraphic = FaceContourGraphic(this)
                add(faceGraphic)
                faceGraphic.updateFace(face)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    // Functions for loading images from app assets.

    // Functions for loading images from app assets.
    // Returns max image width, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.
    private fun getImageMaxWidth(): Int {
        if (mImageMaxWidth == null) {
            // Calculate the max width in portrait mode. This is done lazily since we need to
            // wait for
            // a UI layout pass to get the right values. So delay it to first time image
            // rendering time.
            mImageMaxWidth = binding.imageView.width
        }
        return mImageMaxWidth ?: 0
    }

    // Returns max image height, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.
    private fun getImageMaxHeight(): Int {
        if (mImageMaxHeight == null) {
            // Calculate the max width in portrait mode. This is done lazily since we need to
            // wait for
            // a UI layout pass to get the right values. So delay it to first time image
            // rendering time.
            mImageMaxHeight = binding.imageView.height
        }
        return mImageMaxHeight ?: 0
    }

    // Gets the targeted width / height.
    private fun getTargetedWidthHeight(): Pair<Int, Int> {
        val targetWidth: Int
        val targetHeight: Int
        val maxWidthForPortraitMode = getImageMaxWidth()
        val maxHeightForPortraitMode = getImageMaxHeight()
        targetWidth = maxWidthForPortraitMode
        targetHeight = maxHeightForPortraitMode
        return Pair(targetWidth, targetHeight)
    }

    override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
        binding.graphicOverlay.clear()
        when (position) {
            0 -> mSelectedImage = getBitmapFromAsset(this, "Please_walk_on_the_grass.jpg")
            1 ->                 // Whatever you want to happen when the thrid item gets selected
                mSelectedImage = getBitmapFromAsset(this, "grace_hopper.jpg")
        }
        if (mSelectedImage != null) {
            // Get the dimensions of the View
            // POX4G21
            val (targetWidth, maxHeight) = getTargetedWidthHeight()

            // Determine how much to scale down the image
            mSelectedImage?.let { imgSeleted ->
                val scaleFactor = max(
                    imgSeleted.width.toFloat() / targetWidth.toFloat(),
                    imgSeleted.height.toFloat() / maxHeight.toFloat()
                )
                val resizedBitmap = Bitmap.createScaledBitmap(
                    imgSeleted,
                    (imgSeleted.width / scaleFactor).toInt(),
                    (imgSeleted.height / scaleFactor).toInt(),
                    true
                )
                binding.imageView.setImageBitmap(resizedBitmap)
                mSelectedImage = resizedBitmap
            }
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        // Do nothing
    }

    private fun getBitmapFromAsset(context: Context, filePath: String?): Bitmap? {
        val assetManager = context.assets
        val `is`: InputStream
        var bitmap: Bitmap? = null
        try {
            `is` = assetManager.open(filePath!!)
            bitmap = BitmapFactory.decodeStream(`is`)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return bitmap
    }
}