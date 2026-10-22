package com.example.label

import com.countryatv.core.LabelSpec
import com.countryatv.core.MonoImage

// What a built-in printer driver must provide. Rule for every driver: report what the printer
// actually said. Never assume a print worked; a driver that can't read the printer's status back
// is not good enough for InScan.
interface LabelPrinter {
    // e.g. "Brother PT-P910BT"; recorded with each tag.
    val model: String
    // The label stock loaded in the printer, read from the printer where it can tell us.
    suspend fun status(): PrinterStatus
    suspend fun print(image: MonoImage, copies: Int = 1): PrintOutcome
}

sealed class PrinterStatus {
    data class Ready(val loaded: LabelSpec?) : PrinterStatus()
    // Out of labels, cover open, battery flat, not paired, out of range... in plain words.
    data class NotReady(val reason: String) : PrinterStatus()
}

sealed class PrintOutcome {
    object Printed : PrintOutcome()
    data class Failed(val reason: String) : PrintOutcome()
}
