package com.example.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.data.DealerProfile
import com.example.data.InventoryListing
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale

data class BuyerInfo(
    val name: String,
    val phone: String,
    val address: String
)

data class FinanceTerms(
    val downPayment: String,
    val interestRate: String,
    val loanTermMonths: String
)

enum class DocumentKind(val title: String, val fileStem: String) {
    BILL_OF_SALE("Bill of Sale", "Bill_of_Sale"),
    FINANCE_SUMMARY("Finance Terms Summary", "Finance_Terms"),
    TITLE_WORKSHEET("Title & Registration Worksheet", "Title_Worksheet")
}

sealed class DocumentResult {
    data class Ready(val file: File) : DocumentResult()

    // The document was not made because something it needs is missing. Message is shown as-is.
    data class Refused(val message: String) : DocumentResult()

    // Writing the file failed. Message is shown as-is.
    data class Failed(val message: String) : DocumentResult()
}

object DocumentPdfExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    // Opens the phone's share menu with the PDF attached. Returns false if no app can take it.
    // Opening the menu is not sending: the employee still has to press Send in their mail app.
    fun sharePdfFile(context: Context, file: File, emailAddress: String, dealer: DealerProfile): Boolean {
        val uri = FileProvider.getUriForFile(context, "com.example.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
            putExtra(Intent.EXTRA_SUBJECT, "${dealer.legalName} sale documents")
            putExtra(Intent.EXTRA_TEXT, "Attached are your sale documents from ${dealer.legalName}.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(Intent.createChooser(intent, "Send the PDF with"))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    // Opens Android's print screen, where the employee picks a real printer and the copies.
    // Returns false if the print screen couldn't be opened.
    fun printPdf(context: Context, file: File): Boolean {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return false
        return try {
            printManager.print(file.nameWithoutExtension, PdfPrintAdapter(file), null)
            true
        } catch (e: IllegalStateException) {
            false
        }
    }

    // Lists what a document still needs, in words an employee would use. Empty means it can be made.
    fun missingForDocument(
        kind: DocumentKind,
        dealer: DealerProfile,
        listing: InventoryListing,
        buyer: BuyerInfo,
        finance: FinanceTerms?
    ): List<String> = buildList {
        addAll(dealer.missingFields())
        if (buyer.name.isBlank()) add("buyer's full name")
        if (kind != DocumentKind.FINANCE_SUMMARY && buyer.address.isBlank()) add("buyer's address")
        if (kind == DocumentKind.BILL_OF_SALE && buyer.phone.isBlank()) add("buyer's phone")
        if (listing.vin.isBlank()) add("vehicle VIN")
        if (listing.make.isBlank()) add("vehicle make")
        if (listing.model.isBlank()) add("vehicle model")
        if (listing.year.isBlank()) add("vehicle year")
        if (kind == DocumentKind.FINANCE_SUMMARY) {
            val terms = finance
            if (terms == null || terms.downPayment.toMoneyOrNull() == null) add("down payment")
            if (terms == null || terms.interestRate.toMoneyOrNull() == null) add("interest rate")
            if (terms == null || terms.loanTermMonths.trim().toIntOrNull()?.takeIf { it > 0 } == null) add("loan term in months")
        }
    }

    fun generate(
        context: Context,
        kind: DocumentKind,
        dealer: DealerProfile,
        listing: InventoryListing,
        buyer: BuyerInfo,
        saleDate: String,
        finance: FinanceTerms? = null,
        signaturePaths: List<android.graphics.Path>? = null
    ): DocumentResult {
        val missing = missingForDocument(kind, dealer, listing, buyer, finance)
        if (missing.isNotEmpty()) {
            return DocumentResult.Refused("The ${kind.title} can't be made yet. Still needed: ${missing.joinToString(", ")}.")
        }
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        when (kind) {
            DocumentKind.BILL_OF_SALE -> drawBillOfSale(page.canvas, dealer, listing, buyer, saleDate, signaturePaths)
            DocumentKind.FINANCE_SUMMARY -> drawFinanceSummary(page.canvas, dealer, listing, buyer, finance!!, signaturePaths)
            DocumentKind.TITLE_WORKSHEET -> drawTitleWorksheet(page.canvas, dealer, listing, buyer, saleDate)
        }
        document.finishPage(page)
        return savePdfFile(context, document, "${kind.fileStem}_${fileSafe(listing.vin)}.pdf")
    }

    // One PDF holding a Bill of Sale and a Title & Registration Worksheet for each vehicle.
    fun generateBulk(
        context: Context,
        dealer: DealerProfile,
        listings: List<InventoryListing>,
        buyer: BuyerInfo,
        saleDate: String,
        signaturePaths: List<android.graphics.Path>? = null
    ): DocumentResult {
        if (listings.isEmpty()) return DocumentResult.Refused("Select at least one vehicle first.")
        val problems = listings.mapNotNull { listing ->
            val missing = (missingForDocument(DocumentKind.BILL_OF_SALE, dealer, listing, buyer, null) +
                missingForDocument(DocumentKind.TITLE_WORKSHEET, dealer, listing, buyer, null)).distinct()
            if (missing.isEmpty()) null else "${listing.year} ${listing.make} ${listing.model}".trim() + ": " + missing.joinToString(", ")
        }
        if (problems.isNotEmpty()) {
            return DocumentResult.Refused("The documents can't be made yet. Still needed. ${problems.joinToString(". ")}.")
        }
        val document = PdfDocument()
        var pageNumber = 1
        listings.forEach { listing ->
            val bos = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create())
            drawBillOfSale(bos.canvas, dealer, listing, buyer, saleDate, signaturePaths)
            document.finishPage(bos)
            val title = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create())
            drawTitleWorksheet(title.canvas, dealer, listing, buyer, saleDate)
            document.finishPage(title)
        }
        return savePdfFile(context, document, "Sale_Documents_${System.currentTimeMillis()}.pdf")
    }

    private fun drawBillOfSale(
        canvas: Canvas,
        dealer: DealerProfile,
        listing: InventoryListing,
        buyer: BuyerInfo,
        saleDate: String,
        signaturePaths: List<android.graphics.Path>?
    ) {
        val paint = Paint()
        drawDocumentFrame(canvas, paint)
        var y = drawHeader(canvas, paint, "BILL OF SALE", dealer.legalName.uppercase(Locale.US), 60f) + 10f

        y = drawSectionHeader(canvas, paint, "1. DATE OF SALE", y)
        drawKeyValue(canvas, paint, "Date:", saleDate, 40f, y + 12f)
        y += 26f

        y = drawSectionHeader(canvas, paint, "2. SELLER", y)
        drawKeyValue(canvas, paint, "Dealer:", dealer.legalName, 40f, y + 12f)
        drawKeyValue(canvas, paint, "Dealer Number:", dealer.dealerNumber, 40f, y + 24f)
        drawKeyValue(canvas, paint, "Address:", dealer.address, 40f, y + 36f)
        y += 50f

        y = drawSectionHeader(canvas, paint, "3. BUYER", y)
        drawKeyValue(canvas, paint, "Full Name:", buyer.name, 40f, y + 12f)
        drawKeyValue(canvas, paint, "Phone:", buyer.phone, 40f, y + 24f)
        drawKeyValue(canvas, paint, "Address:", buyer.address, 40f, y + 36f)
        y += 50f

        y = drawSectionHeader(canvas, paint, "4. VEHICLE", y)
        drawKeyValue(canvas, paint, "Year Make Model:", "${listing.year} ${listing.make} ${listing.model}", 40f, y + 12f)
        drawKeyValue(canvas, paint, "Type:", listing.vehicleType, 40f, y + 24f)
        drawKeyValue(canvas, paint, "Color:", listing.color, 40f, y + 36f)
        drawKeyValue(canvas, paint, "VIN:", listing.vin, 40f, y + 48f)
        y += 62f

        // Only the sale price the employee entered. Taxes and fees are not calculated by the app.
        y = drawSectionHeader(canvas, paint, "5. PRICE", y)
        drawKeyValue(canvas, paint, "Sale Price:", currency(listing.price), 40f, y + 12f)
        y += 26f

        y = drawSectionHeader(canvas, paint, "6. SIGNATURES", y)
        drawSignatureBlock(canvas, paint, signaturePaths, "Dealer representative, ${dealer.legalName}", "Buyer: ${buyer.name}", y)
    }

    private fun drawFinanceSummary(
        canvas: Canvas,
        dealer: DealerProfile,
        listing: InventoryListing,
        buyer: BuyerInfo,
        terms: FinanceTerms,
        signaturePaths: List<android.graphics.Path>?
    ) {
        val paint = Paint()
        drawDocumentFrame(canvas, paint)
        var y = drawHeader(canvas, paint, "FINANCE TERMS SUMMARY", "PREPARED BY ${dealer.legalName.uppercase(Locale.US)}. NOT A LOAN CONTRACT.", 60f) + 10f

        val downPayment = terms.downPayment.toMoneyOrNull()!!
        val rate = terms.interestRate.toMoneyOrNull()!!
        val months = terms.loanTermMonths.trim().toInt()
        val principal = (listing.price - downPayment).coerceAtLeast(0.0)
        val monthlyRate = (rate / 100.0) / 12.0
        val monthlyPayment = if (monthlyRate > 0) {
            (principal * monthlyRate * Math.pow(1.0 + monthlyRate, months.toDouble())) /
                (Math.pow(1.0 + monthlyRate, months.toDouble()) - 1.0)
        } else {
            principal / months
        }

        y = drawSectionHeader(canvas, paint, "1. BUYER", y)
        drawKeyValue(canvas, paint, "Full Name:", buyer.name, 40f, y + 12f)
        y += 26f

        y = drawSectionHeader(canvas, paint, "2. VEHICLE", y)
        drawKeyValue(canvas, paint, "Year Make Model:", "${listing.year} ${listing.make} ${listing.model}", 40f, y + 12f)
        drawKeyValue(canvas, paint, "VIN:", listing.vin, 40f, y + 24f)
        y += 38f

        y = drawSectionHeader(canvas, paint, "3. TERMS AS ENTERED", y)
        drawKeyValue(canvas, paint, "Sale Price:", currency(listing.price), 40f, y + 12f)
        drawKeyValue(canvas, paint, "Down Payment:", currency(downPayment), 40f, y + 24f)
        drawKeyValue(canvas, paint, "Amount Financed:", currency(principal), 40f, y + 36f)
        drawKeyValue(canvas, paint, "Interest Rate (APR):", "${terms.interestRate.trim()}%", 40f, y + 48f)
        drawKeyValue(canvas, paint, "Term:", "$months months", 40f, y + 60f)
        drawKeyValue(canvas, paint, "Estimated Monthly Payment:", currency(monthlyPayment), 40f, y + 72f)
        y += 86f

        y = drawSectionHeader(canvas, paint, "4. SIGNATURES", y)
        drawSignatureBlock(canvas, paint, signaturePaths, "Dealer representative, ${dealer.legalName}", "Buyer: ${buyer.name}", y)
    }

    // A worksheet of the details to copy onto Oregon DMV Form 735-226. It is not the DMV form.
    private fun drawTitleWorksheet(
        canvas: Canvas,
        dealer: DealerProfile,
        listing: InventoryListing,
        buyer: BuyerInfo,
        saleDate: String
    ) {
        val paint = Paint()
        drawDocumentFrame(canvas, paint)
        var y = drawHeader(canvas, paint, "TITLE & REGISTRATION WORKSHEET", "FOR COPYING ONTO OREGON DMV FORM 735-226. THIS PAGE IS NOT THE DMV FORM.", 60f) + 10f

        y = drawSectionHeader(canvas, paint, "1. VEHICLE", y)
        drawKeyValue(canvas, paint, "Make:", listing.make, 40f, y + 12f)
        drawKeyValue(canvas, paint, "Model:", listing.model, 40f, y + 24f)
        drawKeyValue(canvas, paint, "Year:", listing.year, 40f, y + 36f)
        drawKeyValue(canvas, paint, "Color:", listing.color, 40f, y + 48f)
        drawKeyValue(canvas, paint, "Type:", listing.vehicleType, 40f, y + 60f)
        drawKeyValue(canvas, paint, "VIN:", listing.vin, 40f, y + 72f)
        y += 86f

        y = drawSectionHeader(canvas, paint, "2. NEW OWNER", y)
        drawKeyValue(canvas, paint, "Full Name:", buyer.name, 40f, y + 12f)
        drawKeyValue(canvas, paint, "Address:", buyer.address, 40f, y + 24f)
        y += 38f

        y = drawSectionHeader(canvas, paint, "3. SELLER", y)
        drawKeyValue(canvas, paint, "Dealer:", dealer.legalName, 40f, y + 12f)
        drawKeyValue(canvas, paint, "Dealer Number:", dealer.dealerNumber, 40f, y + 24f)
        drawKeyValue(canvas, paint, "Date of Sale:", saleDate, 40f, y + 36f)
    }

    private fun savePdfFile(context: Context, document: PdfDocument, filename: String): DocumentResult {
        // App-private storage: customer details never land in the phone's shared Downloads folder.
        val dir = File(context.filesDir, "documents")
        return try {
            if (!dir.exists() && !dir.mkdirs()) {
                return DocumentResult.Failed("The phone wouldn't let the app create a folder for documents. Check that the phone has free storage.")
            }
            val file = File(dir, filename)
            FileOutputStream(file).use { document.writeTo(it) }
            DocumentResult.Ready(file)
        } catch (e: IOException) {
            DocumentResult.Failed("The document couldn't be saved on the phone. Check that the phone has free storage and try again.")
        } finally {
            document.close()
        }
    }

    private fun currency(amount: Double): String = NumberFormat.getCurrencyInstance(Locale.US).format(amount)

    private fun String.toMoneyOrNull(): Double? =
        trim().removePrefix("$").replace(",", "").removeSuffix("%").trim().toDoubleOrNull()?.takeIf { it >= 0.0 }

    private fun fileSafe(value: String): String = value.replace(Regex("[^A-Za-z0-9_-]"), "")

    private fun drawDocumentFrame(canvas: Canvas, paint: Paint) {
        paint.apply {
            style = Paint.Style.STROKE
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        canvas.drawRect(25f, 25f, 570f, 817f, paint)
        paint.apply {
            color = Color.GRAY
            strokeWidth = 0.5f
        }
        canvas.drawRect(28f, 28f, 567f, 814f, paint)
    }

    private fun drawHeader(canvas: Canvas, paint: Paint, title: String, subtitle: String, y: Float): Float {
        paint.apply {
            style = Paint.Style.FILL
            color = Color.BLACK
            typeface = Typeface.create("serif", Typeface.BOLD)
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(title, 297.5f, y, paint)
        paint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9f
            color = Color.DKGRAY
        }
        canvas.drawText(subtitle, 297.5f, y + 15f, paint)
        paint.apply {
            color = Color.BLACK
            strokeWidth = 1.5f
        }
        canvas.drawLine(40f, y + 24f, 555f, y + 24f, paint)
        paint.strokeWidth = 0.5f
        canvas.drawLine(40f, y + 27f, 555f, y + 27f, paint)
        return y + 42f
    }

    private fun drawSectionHeader(canvas: Canvas, paint: Paint, title: String, y: Float): Float {
        paint.apply {
            style = Paint.Style.FILL
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textSize = 10f
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(title, 40f, y, paint)
        paint.apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        canvas.drawLine(40f, y + 4f, 555f, y + 4f, paint)
        return y + 16f
    }

    private fun drawKeyValue(canvas: Canvas, paint: Paint, key: String, value: String, x: Float, y: Float) {
        paint.apply {
            style = Paint.Style.FILL
            color = Color.GRAY
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 9f
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(key, x, y, paint)
        paint.apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9.5f
        }
        canvas.drawText(value, x + 130f, y, paint)
    }

    // Two signature lines. The dealer line is left blank for a real signature; only the buyer's
    // signature captured on the phone is drawn.
    private fun drawSignatureBlock(
        canvas: Canvas,
        paint: Paint,
        buyerSignature: List<android.graphics.Path>?,
        dealerLabel: String,
        buyerLabel: String,
        y: Float
    ) {
        paint.apply {
            color = Color.BLACK
            strokeWidth = 0.75f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(50f, y + 40f, 250f, y + 40f, paint)
        canvas.drawLine(340f, y + 40f, 540f, y + 40f, paint)
        paint.apply {
            style = Paint.Style.FILL
            color = Color.DKGRAY
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(dealerLabel, 150f, y + 52f, paint)
        canvas.drawText(buyerLabel, 440f, y + 52f, paint)

        if (buyerSignature.isNullOrEmpty()) return
        val bounds = RectF()
        val combined = android.graphics.Path()
        buyerSignature.forEach { combined.addPath(it) }
        combined.computeBounds(bounds, true)
        if (bounds.isEmpty) return

        val scale = minOf(170f / bounds.width(), 28f / bounds.height()).coerceAtMost(1.2f)
        val matrix = Matrix().apply {
            postTranslate(-bounds.left, -bounds.top)
            postScale(scale, scale)
            postTranslate(350f, y + 10f)
        }
        val drawn = android.graphics.Path(combined).apply { transform(matrix) }
        paint.apply {
            color = Color.parseColor("#0000FF")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(drawn, paint)
    }
}

class PdfPrintAdapter(private val pdfFile: File) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(pdfFile.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out android.print.PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        try {
            FileInputStream(pdfFile).use { input ->
                FileOutputStream(destination?.fileDescriptor).use { output -> input.copyTo(output) }
            }
            callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
        } catch (e: IOException) {
            // Shown by Android's own print screen.
            callback?.onWriteFailed("The document couldn't be sent to the printer. Try again.")
        }
    }
}
