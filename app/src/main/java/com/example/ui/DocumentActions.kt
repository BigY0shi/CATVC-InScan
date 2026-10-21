package com.example.ui

import android.content.Context
import com.example.data.DealerProfile
import com.example.data.InventoryListing
import com.example.utils.BuyerInfo
import com.example.utils.DocumentKind
import com.example.utils.DocumentPdfExporter
import com.example.utils.DocumentResult
import com.example.utils.FinanceTerms

// Document tabs on the sale screens: 0 Bill of Sale, 1 Finance Terms Summary, 2 Title Worksheet.
fun documentKindForTab(tab: Int): DocumentKind = when (tab) {
    1 -> DocumentKind.FINANCE_SUMMARY
    2 -> DocumentKind.TITLE_WORKSHEET
    else -> DocumentKind.BILL_OF_SALE
}

fun documentForTab(
    context: Context,
    tab: Int,
    dealer: DealerProfile,
    listing: InventoryListing,
    buyer: BuyerInfo,
    saleDate: String,
    finance: FinanceTerms,
    signaturePaths: List<android.graphics.Path>
): DocumentResult = DocumentPdfExporter.generate(
    context = context,
    kind = documentKindForTab(tab),
    dealer = dealer,
    listing = listing,
    buyer = buyer,
    saleDate = saleDate,
    finance = finance,
    signaturePaths = signaturePaths
)

// Each action reports only what actually happened.
fun reportExport(result: DocumentResult, showSnackbar: (String) -> Unit) {
    when (result) {
        is DocumentResult.Ready -> showSnackbar("Saved ${result.file.name}. Use Email or Print to send it on.")
        is DocumentResult.Refused -> showSnackbar(result.message)
        is DocumentResult.Failed -> showSnackbar(result.message)
    }
}

fun shareDocument(
    context: Context,
    result: DocumentResult,
    email: String,
    dealer: DealerProfile,
    showSnackbar: (String) -> Unit
) {
    when (result) {
        is DocumentResult.Ready -> {
            if (DocumentPdfExporter.sharePdfFile(context, result.file, email, dealer)) {
                showSnackbar("Opened the share menu for $email. The email goes out when you press Send in your mail app.")
            } else {
                showSnackbar("This phone has no app that can send the PDF. Install or sign in to an email app, then try again.")
            }
        }
        is DocumentResult.Refused -> showSnackbar(result.message)
        is DocumentResult.Failed -> showSnackbar(result.message)
    }
}

fun printDocument(context: Context, result: DocumentResult, showSnackbar: (String) -> Unit) {
    when (result) {
        is DocumentResult.Ready -> {
            if (DocumentPdfExporter.printPdf(context, result.file)) {
                showSnackbar("Opened Android's print screen. Pick the printer and copies there.")
            } else {
                showSnackbar("Android's print screen wouldn't open on this phone. Save the PDF and print it from another device.")
            }
        }
        is DocumentResult.Refused -> showSnackbar(result.message)
        is DocumentResult.Failed -> showSnackbar(result.message)
    }
}
