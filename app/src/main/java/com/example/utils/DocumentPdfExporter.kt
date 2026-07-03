package com.example.utils

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
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.data.InventoryListing
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale

object DocumentPdfExporter {

    fun sharePdfFile(context: Context, file: File, emailAddress: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "com.example.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
                putExtra(Intent.EXTRA_SUBJECT, "Moab Powersports Hub Contract Documents")
                putExtra(Intent.EXTRA_TEXT, "Attached please find the generated powersports vehicle transfer and sale contract documents from Moab Powersports Hub.")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Email Contract PDF via"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun printPdf(context: Context, file: File) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = "Moab Powersports Contract Document"
            printManager.print(jobName, PdfPrintAdapter(context, file), null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getAppDirectory(context: Context): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir.exists() || downloadsDir.mkdirs()) {
            return downloadsDir
        }
        return context.getExternalFilesDir(null) ?: context.filesDir
    }

    private fun drawDocumentFrame(canvas: Canvas, paint: Paint) {
        // Draw elegant thin border frame
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
        // Main Title
        paint.apply {
            style = Paint.Style.FILL
            color = Color.BLACK
            typeface = Typeface.create("serif", Typeface.BOLD)
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(title, 297.5f, y, paint)

        // Subtitle
        paint.apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9f
            color = Color.DKGRAY
        }
        canvas.drawText(subtitle, 297.5f, y + 15f, paint)

        // Triple styled lines below header
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
        
        // Underline
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
        canvas.drawText(value, x + 110f, y, paint)
    }

    private fun drawSignatureBlock(
        canvas: Canvas,
        paint: Paint,
        signaturePaths: List<android.graphics.Path>?,
        label: String,
        y: Float
    ): Float {
        // Draw Signature lines
        paint.apply {
            color = Color.BLACK
            strokeWidth = 0.75f
            style = Paint.Style.STROKE
        }
        
        // Seller Sign Line
        canvas.drawLine(50f, y + 40f, 250f, y + 40f, paint)
        
        // Buyer Sign Line
        canvas.drawLine(340f, y + 40f, 540f, y + 40f, paint)

        // Labels
        paint.apply {
            style = Paint.Style.FILL
            color = Color.DKGRAY
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Moab Powersports Representative", 150f, y + 52f, paint)
        canvas.drawText(label, 440f, y + 52f, paint)

        // Draw Representative Fake Signature
        paint.apply {
            color = Color.parseColor("#1B365D") // Navy ink
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            isAntiAlias = true
        }
        val repSigPath = android.graphics.Path().apply {
            moveTo(60f, y + 32f)
            quadTo(100f, y + 15f, 140f, y + 34f)
            quadTo(180f, y + 42f, 220f, y + 25f)
            moveTo(110f, y + 20f)
            lineTo(130f, y + 40f)
        }
        canvas.drawPath(repSigPath, paint)

        // Draw Buyer Real Signature
        if (signaturePaths != null && signaturePaths.isNotEmpty()) {
            val bounds = RectF()
            val pathCombined = android.graphics.Path()
            signaturePaths.forEach { pathCombined.addPath(it) }
            pathCombined.computeBounds(bounds, true)

            if (!bounds.isEmpty) {
                val targetLeft = 350f
                val targetTop = y + 10f
                val targetWidth = 170f
                val targetHeight = 28f
                
                val scaleX = targetWidth / bounds.width()
                val scaleY = targetHeight / bounds.height()
                val scale = minOf(scaleX, scaleY).coerceAtMost(1.2f)

                val matrix = Matrix()
                matrix.postTranslate(-bounds.left, -bounds.top)
                matrix.postScale(scale, scale)
                matrix.postTranslate(targetLeft, targetTop)

                val drawnPath = android.graphics.Path(pathCombined)
                drawnPath.transform(matrix)

                paint.apply {
                    color = Color.parseColor("#0000FF") // Pure Blue Ink
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                canvas.drawPath(drawnPath, paint)
            }
        }

        return y + 60f
    }

    private fun savePdfFile(document: PdfDocument, filename: String): File? {
        return try {
            val file = File(getAppDirectory(null ?: return null), filename)
            val outputStream = FileOutputStream(file)
            document.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            document.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            document.close()
            null
        }
    }

    fun generateBillOfSalePdf(
        context: Context,
        listing: InventoryListing,
        buyerName: String,
        buyerPhone: String,
        buyerAddress: String,
        saleDate: String,
        signaturePaths: List<android.graphics.Path>? = null
    ): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        drawDocumentFrame(canvas, paint)
        
        var y = 60f
        y = drawHeader(canvas, paint, "BILL OF SALE & TITLE TRANSFER", "DEALER RECORD - MOAB POWER SPORTS HUB", y)
        y += 10f

        // 1. Transaction Date
        y = drawSectionHeader(canvas, paint, "1. DECLARED TRANSACTION DATE", y)
        drawKeyValue(canvas, paint, "Date of Transfer:", saleDate, 40f, y + 12f)
        y += 26f

        // 2. Seller Info
        y = drawSectionHeader(canvas, paint, "2. SELLER INFORMATION", y)
        drawKeyValue(canvas, paint, "Dealer Name:", "Moab Off-Road Powersport Dealers LLC", 40f, y + 12f)
        drawKeyValue(canvas, paint, "License Number:", "Utah State License #UT-40912", 40f, y + 24f)
        y += 38f

        // 3. Buyer Info
        y = drawSectionHeader(canvas, paint, "3. BUYER CERTIFICATION", y)
        drawKeyValue(canvas, paint, "Buyer Full Name:", buyerName.ifBlank { "N/A" }, 40f, y + 12f)
        drawKeyValue(canvas, paint, "Phone Contact:", buyerPhone.ifBlank { "N/A" }, 40f, y + 24f)
        drawKeyValue(canvas, paint, "Primary Address:", buyerAddress.ifBlank { "N/A" }, 40f, y + 36f)
        y += 50f

        // 4. Vehicle Details
        y = drawSectionHeader(canvas, paint, "4. DESCRIBED POWERSPORT VEHICLE", y)
        drawKeyValue(canvas, paint, "Year Make Model:", "${listing.year} ${listing.make} ${listing.model}", 40f, y + 12f)
        drawKeyValue(canvas, paint, "Classification:", listing.vehicleType, 40f, y + 24f)
        drawKeyValue(canvas, paint, "Body Color:", listing.color, 40f, y + 36f)
        drawKeyValue(canvas, paint, "VIN/Serial ID:", listing.vin, 40f, y + 48f)
        y += 62f

        // 5. Price & Fees Summary
        y = drawSectionHeader(canvas, paint, "5. LIQUIDATED PRICE & FEES SUMMARY", y)
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
        
        drawKeyValue(canvas, paint, "Negotiated Price:", currencyFormat.format(listing.price), 40f, y + 12f)
        drawKeyValue(canvas, paint, "Sales Tax (6.5%):", currencyFormat.format(listing.price * 0.065), 40f, y + 24f)
        drawKeyValue(canvas, paint, "Delivery Fee:", currencyFormat.format(495.00), 40f, y + 36f)
        
        // Draw total
        paint.apply {
            color = Color.BLACK
            strokeWidth = 0.5f
        }
        canvas.drawLine(40f, y + 44f, 555f, y + 44f, paint)
        
        paint.apply {
            style = Paint.Style.FILL
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10f
        }
        canvas.drawText("TOTAL TRANSACTION VALUE:", 40f, y + 56f, paint)
        canvas.drawText(currencyFormat.format(listing.price * 1.065 + 495.00), 150f, y + 56f, paint)
        y += 72f

        // 6. Signature Block
        y = drawSectionHeader(canvas, paint, "6. AUTHORIZING SIGNATURES", y)
        drawSignatureBlock(canvas, paint, signaturePaths, "Buyer Signature (" + buyerName.ifBlank { "Draft" } + ")", y)

        document.finishPage(page)
        return savePdfFile(document, "Moab_Bill_Of_Sale_${listing.id}.pdf")
    }

    fun generateFinanceAgreementPdf(
        context: Context,
        listing: InventoryListing,
        buyerName: String,
        downPaymentStr: String,
        rateStr: String,
        termStr: String,
        signaturePaths: List<android.graphics.Path>? = null
    ): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        drawDocumentFrame(canvas, paint)
        
        var y = 60f
        y = drawHeader(canvas, paint, "FINANCIAL LEASE & CREDIT AGREEMENT", "DEBTOR CERTIFICATION - MOAB POWER SPORTS HUB", y)
        y += 10f

        // Calculations
        val downPayment = downPaymentStr.toDoubleOrNull() ?: 0.0
        val rate = rateStr.toDoubleOrNull() ?: 5.0
        val term = termStr.toIntOrNull() ?: 60
        val principal = (listing.price - downPayment).coerceAtLeast(0.0)
        val monthlyRate = (rate / 100.0) / 12.0
        val monthlyPayment = if (monthlyRate > 0) {
            (principal * monthlyRate * Math.pow(1.0 + monthlyRate, term.toDouble())) / (Math.pow(1.0 + monthlyRate, term.toDouble()) - 1.0)
        } else {
            principal / term
        }

        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

        // 1. Debtor info
        y = drawSectionHeader(canvas, paint, "1. DEBTOR SPECIFICATION", y)
        drawKeyValue(canvas, paint, "Debtor Full Name:", buyerName.ifBlank { "N/A" }, 40f, y + 12f)
        y += 26f

        // 2. Collateral security description
        y = drawSectionHeader(canvas, paint, "2. COLLATERAL SECURITY DESCRIPTION", y)
        drawKeyValue(canvas, paint, "Vehicle Spec:", "${listing.year} ${listing.make} ${listing.model}", 40f, y + 12f)
        drawKeyValue(canvas, paint, "Classification:", listing.vehicleType, 40f, y + 24f)
        drawKeyValue(canvas, paint, "VIN/Serial ID:", listing.vin, 40f, y + 36f)
        y += 50f

        // 3. Amortization parameters
        y = drawSectionHeader(canvas, paint, "3. AMORTIZATION SCHEDULE DETAILS", y)
        drawKeyValue(canvas, paint, "Vehicle MSRP:", currencyFormat.format(listing.price), 40f, y + 12f)
        drawKeyValue(canvas, paint, "Debtor Down Payment:", currencyFormat.format(downPayment), 40f, y + 24f)
        drawKeyValue(canvas, paint, "Financed Principal:", currencyFormat.format(principal), 40f, y + 36f)
        drawKeyValue(canvas, paint, "Interest Rate (APR):", "$rateStr%", 40f, y + 48f)
        drawKeyValue(canvas, paint, "Loan Term Period:", "$term Months", 40f, y + 60f)

        // Draw total
        paint.apply {
            color = Color.BLACK
            strokeWidth = 0.5f
        }
        canvas.drawLine(40f, y + 68f, 555f, y + 68f, paint)

        paint.apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#C2185B") // Dark Magenta for financial attention
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10f
        }
        canvas.drawText("CALCULATED MONTHLY INSTALLMENT PAYMENT:", 40f, y + 80f, paint)
        canvas.drawText(currencyFormat.format(monthlyPayment), 320f, y + 80f, paint)
        y += 96f

        // 4. Term agreements text
        y = drawSectionHeader(canvas, paint, "4. CREDIT TRANSFERS & TERMS", y)
        paint.apply {
            style = Paint.Style.FILL
            color = Color.DKGRAY
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 7.5f
        }
        val termsText = "The Debtor hereby promises to pay the Calculated Monthly Installment to the Creditor named above in consecutive monthly installments on the specified date of each month until the entire debt is cleared. Late fees of 5.0% apply after 10 days of grace period. Interest accrues continuously."
        canvas.drawText(termsText.substring(0, 85), 40f, y + 10f, paint)
        canvas.drawText(termsText.substring(85, 175), 40f, y + 18f, paint)
        canvas.drawText(termsText.substring(175), 40f, y + 26f, paint)
        y += 38f

        // 5. Signature Block
        y = drawSectionHeader(canvas, paint, "5. EXECUTED LIEN AUTHORIZATIONS", y)
        drawSignatureBlock(canvas, paint, signaturePaths, "Debtor Signature (" + buyerName.ifBlank { "Draft" } + ")", y)

        document.finishPage(page)
        return savePdfFile(document, "Moab_Finance_Agreement_${listing.id}.pdf")
    }

    fun generateDmvRegistrationPdf(
        context: Context,
        listing: InventoryListing,
        buyerName: String,
        buyerAddress: String,
        saleDate: String,
        signaturePaths: List<android.graphics.Path>? = null
    ): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        drawDocumentFrame(canvas, paint)
        
        var y = 60f
        y = drawHeader(canvas, paint, "UTAH DMV OFF-ROAD TITLE REGISTRATION", "STATE COMPLIANCE FILE - DIVISION OF MOTOR VEHICLES", y)
        y += 10f

        // 1. Classification
        y = drawSectionHeader(canvas, paint, "1. OFF-HIGHWAY VEHICLE (OHV) CATEGORY", y)
        drawKeyValue(canvas, paint, "Permit Registration Class:", listing.vehicleType, 40f, y + 12f)
        y += 26f

        // 2. Vehicle description
        y = drawSectionHeader(canvas, paint, "2. VEHICLE DESCRIPTION SPECIFICATIONS", y)
        drawKeyValue(canvas, paint, "Vehicle Manufacturer:", listing.make, 40f, y + 12f)
        drawKeyValue(canvas, paint, "Model Name/Number:", listing.model, 40f, y + 24f)
        drawKeyValue(canvas, paint, "Production Year:", listing.year, 40f, y + 36f)
        drawKeyValue(canvas, paint, "Body Trim Color:", listing.color, 40f, y + 48f)
        drawKeyValue(canvas, paint, "VIN/HULL Identifier:", listing.vin, 40f, y + 60f)
        y += 74f

        // 3. Owner info
        y = drawSectionHeader(canvas, paint, "3. REGISTERED OWNER DETAILS", y)
        drawKeyValue(canvas, paint, "Registered Owner:", buyerName.ifBlank { "N/A" }, 40f, y + 12f)
        drawKeyValue(canvas, paint, "Primary Address:", buyerAddress.ifBlank { "N/A" }, 40f, y + 24f)
        y += 38f

        // 4. Dealership verification
        y = drawSectionHeader(canvas, paint, "4. DEALERSHIP COMPLIANCE VERIFICATION", y)
        drawKeyValue(canvas, paint, "Verifying Representative:", "Moab Powersports Hub Authorized Officer", 40f, y + 12f)
        drawKeyValue(canvas, paint, "Emission standard certification:", "EPA Off-Road Certified & Exempt", 40f, y + 24f)
        y += 38f

        // 5. Signature Block
        y = drawSectionHeader(canvas, paint, "5. EXECUTED OFFICIAL OATH", y)
        drawSignatureBlock(canvas, paint, signaturePaths, "Owner Applicant Signature", y)

        document.finishPage(page)
        return savePdfFile(document, "Moab_DMV_Registration_${listing.id}.pdf")
    }

    fun generateBulkPdf(
        context: Context,
        listings: List<InventoryListing>,
        buyerName: String,
        buyerPhone: String,
        buyerAddress: String,
        saleDate: String,
        signaturePaths: List<android.graphics.Path>? = null
    ): File? {
        val document = PdfDocument()
        val paint = Paint()
        var pageCount = 1

        listings.forEach { listing ->
            // Page A: Bill of Sale
            val bosPageInfo = PdfDocument.PageInfo.Builder(595, 842, pageCount++).create()
            val bosPage = document.startPage(bosPageInfo)
            val bosCanvas = bosPage.canvas

            drawDocumentFrame(bosCanvas, paint)
            var yBos = 60f
            yBos = drawHeader(bosCanvas, paint, "BILL OF SALE & TITLE TRANSFER", "DEALER RECORD - MOAB POWER SPORTS HUB", yBos)
            yBos += 10f

            yBos = drawSectionHeader(bosCanvas, paint, "1. DECLARED TRANSACTION DATE", yBos)
            drawKeyValue(bosCanvas, paint, "Date of Transfer:", saleDate, 40f, yBos + 12f)
            yBos += 26f

            yBos = drawSectionHeader(bosCanvas, paint, "2. SELLER INFORMATION", yBos)
            drawKeyValue(bosCanvas, paint, "Dealer Name:", "Moab Off-Road Powersport Dealers LLC", 40f, yBos + 12f)
            drawKeyValue(bosCanvas, paint, "License Number:", "Utah State License #UT-40912", 40f, yBos + 24f)
            yBos += 38f

            yBos = drawSectionHeader(bosCanvas, paint, "3. BUYER CERTIFICATION", yBos)
            drawKeyValue(bosCanvas, paint, "Buyer Full Name:", buyerName.ifBlank { "N/A" }, 40f, yBos + 12f)
            drawKeyValue(bosCanvas, paint, "Phone Contact:", buyerPhone.ifBlank { "N/A" }, 40f, yBos + 24f)
            drawKeyValue(bosCanvas, paint, "Primary Address:", buyerAddress.ifBlank { "N/A" }, 40f, yBos + 36f)
            yBos += 50f

            yBos = drawSectionHeader(bosCanvas, paint, "4. DESCRIBED POWERSPORT VEHICLE", yBos)
            drawKeyValue(bosCanvas, paint, "Year Make Model:", "${listing.year} ${listing.make} ${listing.model}", 40f, yBos + 12f)
            drawKeyValue(bosCanvas, paint, "Classification:", listing.vehicleType, 40f, yBos + 24f)
            drawKeyValue(bosCanvas, paint, "Body Color:", listing.color, 40f, yBos + 36f)
            drawKeyValue(bosCanvas, paint, "VIN/Serial ID:", listing.vin, 40f, yBos + 48f)
            yBos += 62f

            yBos = drawSectionHeader(bosCanvas, paint, "5. LIQUIDATED PRICE & FEES SUMMARY", yBos)
            val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
            drawKeyValue(bosCanvas, paint, "Negotiated Price:", currencyFormat.format(listing.price), 40f, yBos + 12f)
            drawKeyValue(bosCanvas, paint, "Sales Tax (6.5%):", currencyFormat.format(listing.price * 0.065), 40f, yBos + 24f)
            drawKeyValue(bosCanvas, paint, "Delivery Fee:", currencyFormat.format(495.00), 40f, yBos + 36f)

            paint.apply {
                color = Color.BLACK
                strokeWidth = 0.5f
            }
            bosCanvas.drawLine(40f, yBos + 44f, 555f, yBos + 44f, paint)

            paint.apply {
                style = Paint.Style.FILL
                color = Color.BLACK
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 10f
            }
            bosCanvas.drawText("TOTAL TRANSACTION VALUE:", 40f, yBos + 56f, paint)
            bosCanvas.drawText(currencyFormat.format(listing.price * 1.065 + 495.00), 150f, yBos + 56f, paint)
            yBos += 72f

            yBos = drawSectionHeader(bosCanvas, paint, "6. AUTHORIZING SIGNATURES", yBos)
            drawSignatureBlock(bosCanvas, paint, signaturePaths, "Buyer Signature (" + buyerName.ifBlank { "Draft" } + ")", yBos)

            document.finishPage(bosPage)

            // Page B: DMV Title Registration
            val dmvPageInfo = PdfDocument.PageInfo.Builder(595, 842, pageCount++).create()
            val dmvPage = document.startPage(dmvPageInfo)
            val dmvCanvas = dmvPage.canvas

            drawDocumentFrame(dmvCanvas, paint)
            var yDmv = 60f
            yDmv = drawHeader(dmvCanvas, paint, "UTAH DMV OFF-ROAD TITLE REGISTRATION", "STATE COMPLIANCE FILE - DIVISION OF MOTOR VEHICLES", yDmv)
            yDmv += 10f

            yDmv = drawSectionHeader(dmvCanvas, paint, "1. OFF-HIGHWAY VEHICLE (OHV) CATEGORY", yDmv)
            drawKeyValue(dmvCanvas, paint, "Permit Registration Class:", listing.vehicleType, 40f, yDmv + 12f)
            yDmv += 26f

            yDmv = drawSectionHeader(dmvCanvas, paint, "2. VEHICLE DESCRIPTION SPECIFICATIONS", yDmv)
            drawKeyValue(dmvCanvas, paint, "Vehicle Manufacturer:", listing.make, 40f, yDmv + 12f)
            drawKeyValue(dmvCanvas, paint, "Model Name/Number:", listing.model, 40f, yDmv + 24f)
            drawKeyValue(dmvCanvas, paint, "Production Year:", listing.year, 40f, yDmv + 36f)
            drawKeyValue(dmvCanvas, paint, "Body Trim Color:", listing.color, 40f, yDmv + 48f)
            drawKeyValue(dmvCanvas, paint, "VIN/HULL Identifier:", listing.vin, 40f, yDmv + 60f)
            yDmv += 74f

            yDmv = drawSectionHeader(dmvCanvas, paint, "3. REGISTERED OWNER DETAILS", yDmv)
            drawKeyValue(dmvCanvas, paint, "Registered Owner:", buyerName.ifBlank { "N/A" }, 40f, yDmv + 12f)
            drawKeyValue(dmvCanvas, paint, "Primary Address:", buyerAddress.ifBlank { "N/A" }, 40f, yDmv + 24f)
            yDmv += 38f

            yDmv = drawSectionHeader(dmvCanvas, paint, "4. DEALERSHIP COMPLIANCE VERIFICATION", yDmv)
            drawKeyValue(dmvCanvas, paint, "Verifying Representative:", "Moab Powersports Hub Authorized Officer", 40f, yDmv + 12f)
            drawKeyValue(dmvCanvas, paint, "Emission standard certification:", "EPA Off-Road Certified & Exempt", 40f, yDmv + 24f)
            yDmv += 38f

            yDmv = drawSectionHeader(dmvCanvas, paint, "5. EXECUTED OFFICIAL OATH", yDmv)
            drawSignatureBlock(dmvCanvas, paint, signaturePaths, "Owner Applicant Signature", yDmv)

            document.finishPage(dmvPage)
        }

        return savePdfFile(document, "Moab_Bulk_Documents_${System.currentTimeMillis()}.pdf")
    }
}

class PdfPrintAdapter(private val context: Context, private val pdfFile: File) : PrintDocumentAdapter() {
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
        var input: FileInputStream? = null
        var output: FileOutputStream? = null
        try {
            input = FileInputStream(pdfFile)
            output = FileOutputStream(destination?.fileDescriptor)
            val buf = ByteArray(1024)
            var bytesRead: Int
            while (input.read(buf).also { bytesRead = it } > 0) {
                output.write(buf, 0, bytesRead)
            }
            callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.toString())
        } finally {
            try {
                input?.close()
                output?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}

