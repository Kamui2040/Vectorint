package io.github.kamui2040.vectorint.receipt

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.IOException

internal class PlayReceiptScanner(
    private val activity: AppCompatActivity,
    private val onOutcome: (ReceiptScanOutcome) -> Unit,
) : ReceiptScanner {
    private val launcher =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> recognize(result.data)
                Activity.RESULT_CANCELED -> onOutcome(ReceiptScanOutcome.Cancelled)
                else -> onOutcome(ReceiptScanOutcome.Failed)
            }
        }

    override fun launch() {
        try {
            GmsDocumentScanning
                .getClient(
                    GmsDocumentScannerOptions
                        .Builder()
                        .setGalleryImportAllowed(false)
                        .setPageLimit(1)
                        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                        .build(),
                ).getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                }.addOnFailureListener { error ->
                    onOutcome(
                        if (error is MlKitException &&
                            error.errorCode == MlKitException.UNSUPPORTED
                        ) {
                            ReceiptScanOutcome.Unavailable
                        } else {
                            ReceiptScanOutcome.Failed
                        },
                    )
                }
        } catch (_: RuntimeException) {
            onOutcome(ReceiptScanOutcome.Unavailable)
        }
    }

    private fun recognize(resultIntent: Intent?) {
        val pageUri =
            GmsDocumentScanningResult
                .fromActivityResultIntent(resultIntent)
                ?.pages
                ?.singleOrNull()
                ?.imageUri
        if (pageUri == null) {
            onOutcome(ReceiptScanOutcome.Failed)
            return
        }

        val image =
            try {
                InputImage.fromFilePath(activity, pageUri)
            } catch (_: IOException) {
                clearPrivateCacheFile(pageUri)
                onOutcome(ReceiptScanOutcome.Failed)
                return
            }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer
            .process(image)
            .addOnCompleteListener { task ->
                recognizer.close()
                val receiptCleared = clearPrivateCacheFile(pageUri)
                if (task.isSuccessful && receiptCleared) {
                    val fragments =
                        task.result.textBlocks.flatMap { block ->
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
                    onOutcome(
                        ReceiptScanOutcome.RecognizedText(
                            ReceiptTextRowAssembler.assemble(fragments, task.result.text),
                        ),
                    )
                } else {
                    onOutcome(ReceiptScanOutcome.Failed)
                }
            }
    }

    private fun clearPrivateCacheFile(uri: Uri): Boolean {
        val path = uri.path ?: return false
        return try {
            val cacheRoot = activity.cacheDir.canonicalFile
            val candidate = File(path).canonicalFile
            val cachePrefix = cacheRoot.path + File.separator
            if (candidate.path != cacheRoot.path && !candidate.path.startsWith(cachePrefix)) return false
            if (!candidate.exists() || candidate.delete()) return true

            candidate.outputStream().use { }
            candidate.delete()
            !candidate.exists() || candidate.length() == 0L
        } catch (_: IOException) {
            false
        }
    }
}
