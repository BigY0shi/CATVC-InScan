package com.example.data

import com.countryatv.core.LabelContent

// What a read on the Scan screen (camera, typed, or a Bluetooth scanner) came to. The Scan
// screen turns each of these into its result card (scan-pin-ui-spec.md 2c).
sealed interface ScanLookup {
    // A unit on this phone or in the shop system.
    // [tagPayload] is set when our own QR tag for this unit was read (the only read that can move it
    // to In stock). [tagOutOfDate] means the tag's details no longer match the unit.
    // [checkedOnline] is false when the shop system couldn't be asked and the phone's copy is shown.
    data class Found(
        val unit: ShopUnitEntity,
        val tagPayload: String?,
        val tagOutOfDate: Boolean,
        val checkedOnline: Boolean,
    ) : ScanLookup

    // Our tag, but the unit isn't on this phone and the shop system doesn't have it (yet).
    data class TagOnly(val tag: LabelContent) : ScanLookup

    // The typed 8 characters match more than one unit. A clash is refused, never guessed.
    data object ShortIdClash : ScanLookup

    // Not on this phone and the shop system said it doesn't have it.
    data object NotFound : ScanLookup

    // Not on this phone and the shop system couldn't be reached.
    data object Unreachable : ScanLookup

    // The session ended (expired or revoked). Changes stay saved on the phone.
    data object NeedsSignIn : ScanLookup
}

// What Move to In stock came to.
sealed interface MoveResult {
    // [sent] is true when the shop system took it in this round; false when it waits on the phone.
    data class Moved(val unit: ShopUnitEntity, val sent: Boolean, val stillIncoming: Int) : MoveResult

    // The shop system turned the move down. The change waits in Needs attention.
    data class Refused(val unit: ShopUnitEntity, val message: String) : MoveResult
}
