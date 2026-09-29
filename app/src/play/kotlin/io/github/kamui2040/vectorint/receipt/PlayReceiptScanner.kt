package io.github.kamui2040.vectorint.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

internal class PlayReceiptScanner(
    private val activity: AppCompatActivity,
    private val onOutcome: (ReceiptScanOutcome) -> Unit,
) : ReceiptScanner {
    private val fileStore = ReceiptCaptureFileStore(activity)
    private val restoredState = activity.savedStateRegistry.consumeRestoredStateForKey(SAVED_STATE_KEY)
    private var pendingCapture = PendingReceiptCapture.fromBundle(restoredState, fileStore)
    private var recognitionStarted = false
    private val launcher =
        activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
            handleCaptureResult(captured)
        }

    override val isInProgress: Boolean
        get() = pendingCapture?.stage != null && pendingCapture?.stage != CaptureStage.CLEANUP

    init {
        activity.savedStateRegistry.registerSavedStateProvider(SAVED_STATE_KEY) {
            pendingCapture?.toBundle() ?: Bundle()
        }
        fileStore.clearOrphans(pendingCapture?.file?.name)

        when {
            restoredState?.containsKey(STATE_FILE_NAME) == true && pendingCapture == null -> {
                onOutcome(ReceiptScanOutcome.Failed)
            }

            pendingCapture?.stage == CaptureStage.RECOGNIZING -> recognizePendingCapture()
            pendingCapture?.stage == CaptureStage.CLEANUP -> finish(ReceiptScanOutcome.Failed)
        }
    }

    override fun launch() {
        val existing = pendingCapture
        if (existing != null) {
            if (existing.stage == CaptureStage.CLEANUP) {
                finish(ReceiptScanOutcome.Failed)
            } else {
                onOutcome(ReceiptScanOutcome.Failed)
            }
            return
        }

        val capture =
            try {
                fileStore.createCapture()
            } catch (_: IOException) {
                onOutcome(ReceiptScanOutcome.Failed)
                return
            } catch (_: RuntimeException) {
                onOutcome(ReceiptScanOutcome.Failed)
                return
            }

        pendingCapture = PendingReceiptCapture(capture.file, CaptureStage.WAITING_FOR_CAMERA)
        try {
            launcher.launch(capture.uri)
        } catch (_: RuntimeException) {
            finish(ReceiptScanOutcome.Unavailable)
        }
    }

    private fun handleCaptureResult(captured: Boolean) {
        val capture = pendingCapture
        if (capture == null || capture.stage != CaptureStage.WAITING_FOR_CAMERA) {
            onOutcome(ReceiptScanOutcome.Failed)
            return
        }
        if (!captured) {
            finish(ReceiptScanOutcome.Cancelled)
            return
        }
        if (!fileStore.isUsableImage(capture.file)) {
            finish(ReceiptScanOutcome.Failed)
            return
        }

        pendingCapture = capture.copy(stage = CaptureStage.RECOGNIZING)
        recognizePendingCapture()
    }

    private fun recognizePendingCapture() {
        if (recognitionStarted) return
        val capture = pendingCapture
        if (capture == null || capture.stage != CaptureStage.RECOGNIZING) {
            onOutcome(ReceiptScanOutcome.Failed)
            return
        }
        if (!fileStore.isUsableImage(capture.file)) {
            finish(ReceiptScanOutcome.Failed)
            return
        }

        val decoded = ReceiptImageDecoder.decode(capture.file)
        if (decoded == null) {
            finish(ReceiptScanOutcome.Failed)
            return
        }
        val inputImage =
            try {
                InputImage.fromBitmap(decoded.bitmap, decoded.rotationDegrees)
            } catch (_: RuntimeException) {
                decoded.close()
                finish(ReceiptScanOutcome.Failed)
                return
            }
        recognitionStarted = true

        val recognizer =
            try {
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            } catch (_: RuntimeException) {
                decoded.close()
                recognitionStarted = false
                finish(ReceiptScanOutcome.Unavailable)
                return
            }
        val task =
            try {
                recognizer.process(inputImage)
            } catch (_: RuntimeException) {
                recognizer.close()
                decoded.close()
                recognitionStarted = false
                finish(ReceiptScanOutcome.Failed)
                return
            }

        task.addOnCompleteListener { completed ->
            recognizer.close()
            decoded.close()
            if (activity.isDestroyed) return@addOnCompleteListener
            recognitionStarted = false
            if (pendingCapture?.file?.name != capture.file.name) return@addOnCompleteListener
            if (!completed.isSuccessful) {
                finish(ReceiptScanOutcome.Failed)
                return@addOnCompleteListener
            }

            val fragments =
                completed.result.textBlocks.flatMap { block ->
                    block.lines.mapNotNull { line ->
                        val bounds = line.boundingBox ?: return@mapNotNull null
                        ReceiptTextFragment(
                            text = line.text,
                            left = bounds.left,
                            top = bounds.top,
                            right = bounds.right,
                            bottom = bounds.bottom,
                        )
                    }
                }
            finish(
                ReceiptScanOutcome.RecognizedText(
                    ReceiptTextRowAssembler.assemble(fragments, completed.result.text),
                ),
            )
        }
    }

    private fun finish(outcome: ReceiptScanOutcome) {
        val capture = pendingCapture
        if (capture == null) {
            onOutcome(outcome)
            return
        }
        if (fileStore.clear(capture.file)) {
            pendingCapture = null
            onOutcome(outcome)
        } else {
            pendingCapture = capture.copy(stage = CaptureStage.CLEANUP)
            onOutcome(ReceiptScanOutcome.Failed)
        }
    }

    private companion object {
        const val SAVED_STATE_KEY = "receipt_capture"
    }
}

internal data class PendingReceiptCapture(
    val file: File,
    val stage: CaptureStage,
) {
    fun toBundle(): Bundle =
        Bundle().apply {
            putString(STATE_FILE_NAME, file.name)
            putString(STATE_STAGE, stage.name)
        }

    companion object {
        fun fromBundle(
            bundle: Bundle?,
            fileStore: ReceiptCaptureFileStore,
        ): PendingReceiptCapture? {
            val fileName = bundle?.getString(STATE_FILE_NAME) ?: return null
            val stageName = bundle.getString(STATE_STAGE) ?: return null
            val stage = CaptureStage.entries.firstOrNull { it.name == stageName } ?: return null
            val file = fileStore.restore(fileName) ?: return null
            return PendingReceiptCapture(file, stage)
        }
    }
}

internal enum class CaptureStage {
    WAITING_FOR_CAMERA,
    RECOGNIZING,
    CLEANUP,
}

internal class ReceiptCaptureFileStore(
    private val context: Context,
) {
    private val directory = File(context.cacheDir, CAPTURE_DIRECTORY)

    fun createCapture(): ReceiptCapture {
        val file = createFile()
        return try {
            ReceiptCapture(file = file, uri = uriFor(file))
        } catch (error: RuntimeException) {
            clear(file)
            throw error
        }
    }

    internal fun createFile(): File {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create the receipt capture directory")
        }
        val file = File(directory, "$FILE_PREFIX${UUID.randomUUID()}$FILE_SUFFIX")
        if (!file.createNewFile()) throw IOException("Could not create a receipt capture file")
        return file
    }

    internal fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.receipt-files",
            validated(file) ?: throw IllegalArgumentException("Invalid receipt capture file"),
        )

    fun restore(fileName: String): File? {
        if (!FILE_NAME.matches(fileName)) return null
        return validated(File(directory, fileName))?.takeIf { it.exists() && it.isFile }
    }

    fun isUsableImage(file: File): Boolean {
        val candidate = validated(file) ?: return false
        return candidate.exists() && candidate.isFile && candidate.length() in 1..MAX_CAPTURE_BYTES
    }

    fun clear(file: File): Boolean {
        val candidate = validated(file) ?: return false
        return try {
            if (!candidate.exists() || candidate.delete()) return true
            FileOutputStream(candidate, false).channel.use { channel -> channel.truncate(0) }
            candidate.delete()
            !candidate.exists()
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun clearOrphans(preservedFileName: String?) {
        val files = directory.listFiles() ?: return
        files
            .filter { FILE_NAME.matches(it.name) && it.name != preservedFileName }
            .forEach(::clear)
    }

    private fun validated(file: File): File? =
        try {
            val root = directory.canonicalFile
            val candidate = file.canonicalFile
            candidate.takeIf { it.parentFile == root }
        } catch (_: IOException) {
            null
        }

    private companion object {
        const val CAPTURE_DIRECTORY = "receipt-captures"
        const val FILE_PREFIX = "receipt-"
        const val FILE_SUFFIX = ".jpg"
        val FILE_NAME = Regex("receipt-[0-9a-fA-F-]{36}\\.jpg")
        const val MAX_CAPTURE_BYTES = 64L * 1024L * 1024L
    }
}

internal data class ReceiptCapture(
    val file: File,
    val uri: Uri,
)

internal object ReceiptImageDecoder {
    fun decode(file: File): DecodedReceiptImage? {
        var decodedBitmap: Bitmap? = null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (
                bounds.outWidth / sampleSize > MAX_IMAGE_EDGE ||
                bounds.outHeight / sampleSize > MAX_IMAGE_EDGE ||
                bounds.outWidth.toLong() * bounds.outHeight.toLong() / sampleSize / sampleSize > MAX_IMAGE_PIXELS
            ) {
                sampleSize *= 2
            }
            val options =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
            decodedBitmap = bitmap
            val rotationDegrees = ExifInterface(file).rotationDegrees
            DecodedReceiptImage(
                bitmap = bitmap,
                rotationDegrees = rotationDegrees,
            )
        } catch (_: IOException) {
            decodedBitmap?.recycle()
            null
        } catch (_: IllegalArgumentException) {
            decodedBitmap?.recycle()
            null
        } catch (_: RuntimeException) {
            decodedBitmap?.recycle()
            null
        } catch (_: OutOfMemoryError) {
            decodedBitmap?.recycle()
            null
        }
    }

    private const val MAX_IMAGE_EDGE = 2560
    private const val MAX_IMAGE_PIXELS = 4_000_000L
}

internal data class DecodedReceiptImage(
    val bitmap: Bitmap,
    val rotationDegrees: Int,
) {
    fun close() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

private const val STATE_FILE_NAME = "file_name"
private const val STATE_STAGE = "stage"
