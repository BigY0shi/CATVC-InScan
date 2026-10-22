package com.example.label

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File

// Until a printer is built in, a tag can be printed from the printer maker's own app: InScan
// saves the label as an image at the printer's exact size and hands it over.
object LabelShare {
    fun intentFor(context: Context, label: Bitmap, itemId: String): Intent {
        val dir = File(context.cacheDir, "labels").apply { mkdirs() }
        val file = File(dir, "tag-$itemId.png")
        file.outputStream().use { out ->
            check(label.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Couldn't save the label image." }
        }
        val uri = FileProvider.getUriForFile(context, "com.example.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Print the label with…")
    }
}
