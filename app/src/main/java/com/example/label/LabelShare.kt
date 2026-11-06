package com.example.label

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
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

    // One image per copy ("Box 1 of 2", "Box 2 of 2"). [picked], when given, hears back only when
    // the person picks an app in the chooser, so a cancelled share records nothing.
    fun intentFor(context: Context, labels: List<Bitmap>, itemId: String, picked: IntentSender?): Intent {
        require(labels.isNotEmpty())
        val dir = File(context.cacheDir, "labels").apply { mkdirs() }
        val uris = labels.mapIndexed { i, label ->
            val file = File(dir, if (labels.size == 1) "tag-$itemId.png" else "tag-$itemId-${i + 1}.png")
            file.outputStream().use { out ->
                check(label.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Couldn't save the label image." }
            }
            FileProvider.getUriForFile(context, "com.example.fileprovider", file)
        }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uris[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "image/png"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris)) }
        }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return if (picked != null) Intent.createChooser(send, "Print the label with…", picked) else Intent.createChooser(send, "Print the label with…")
    }
}
