package io.github.kamui2040.vectorint.receipt

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import io.github.kamui2040.vectorint.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayReceiptScannerSupportTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val store = ReceiptCaptureFileStore(context)

    @After
    fun cleanReceiptCaptures() {
        store.clearOrphans(null)
    }

    @Test
    fun `capture uses a private content URI with camera grants`() {
        val authority = "${context.packageName}.receipt-files"
        val provider =
            context.packageManager.resolveContentProvider(
                authority,
                PackageManager.GET_META_DATA,
            )
        val uri = Uri.parse("content://$authority/receipt_captures/receipt.jpg")
        val intent = ActivityResultContracts.TakePicture().createIntent(context, uri)

        assertNotNull(provider)
        assertFalse(provider!!.exported)
        assertTrue(provider.grantUriPermissions)
        assertEquals(R.xml.receipt_file_paths, provider.metaData.getInt("android.support.FILE_PROVIDER_PATHS"))
        assertEquals("content", uri.scheme)
        assertEquals(authority, uri.authority)
        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, intent.action)
        assertEquals(uri, intent.outputUri())
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        assertFalse(uri.toString().contains(context.cacheDir.path))
    }

    @Test
    fun `capture file restoration accepts only an existing generated file`() {
        val file = store.createFile()

        assertEquals(file.canonicalFile, store.restore(file.name))
        assertNull(store.restore("../${file.name}"))
        assertNull(store.restore("receipt-not-a-uuid.jpg"))
        assertTrue(store.clear(file))
        assertNull(store.restore(file.name))
    }

    @Test
    fun `pending capture state round trips camera and recognition stages`() {
        val file = store.createFile()

        CaptureStage.entries.forEach { stage ->
            val original = PendingReceiptCapture(file, stage)
            assertEquals(original, PendingReceiptCapture.fromBundle(original.toBundle(), store))
        }
    }

    @Test
    fun `invalid restored stage fails closed`() {
        val file = store.createFile()
        val state = PendingReceiptCapture(file, CaptureStage.RECOGNIZING).toBundle()
        state.putString("stage", "UNKNOWN")

        assertNull(PendingReceiptCapture.fromBundle(state, store))
    }

    @Test
    fun `empty and oversized captures are rejected`() {
        val empty = store.createFile()
        assertFalse(store.isUsableImage(empty))

        empty.outputStream().use { it.write(byteArrayOf(1)) }
        assertTrue(store.isUsableImage(empty))
        RandomAccessFile(empty, "rw").use { it.setLength(64L * 1024L * 1024L + 1L) }
        assertFalse(store.isUsableImage(empty))
    }

    @Test
    fun `orphan cleanup preserves only the active capture`() {
        val active = store.createFile()
        val orphan = store.createFile()

        store.clearOrphans(active.name)

        assertTrue(active.exists())
        assertFalse(orphan.exists())
    }

    @Test
    fun `valid image decodes and malformed image fails closed`() {
        val valid = store.createFile()
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        valid.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()

        val decoded = ReceiptImageDecoder.decode(valid)
        assertNotNull(decoded)
        decoded?.close()

        val malformed = store.createFile()
        malformed.writeText("not an image")
        assertNull(ReceiptImageDecoder.decode(malformed))
    }

    @Suppress("DEPRECATION")
    private fun Intent.outputUri(): Uri? = getParcelableExtra(MediaStore.EXTRA_OUTPUT)
}
