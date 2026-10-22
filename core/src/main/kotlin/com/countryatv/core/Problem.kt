package com.countryatv.core

// Every error the shop system can report, in shop language. The code is for logs and the
// agent tools; the message is what an employee reads. No stack traces, class names or HTTP codes.
enum class Problem(val message: String) {
    NOT_SIGNED_IN("Sign in with your PIN first."),
    PHONE_NOT_ENROLLED("This phone isn't set up for the shop system yet. Ask the owner for a setup code."),
    SETUP_CODE_INVALID("That setup code didn't work. Codes work once and expire after a day. Ask the owner for a new one."),
    WRONG_PIN("That PIN didn't match. Try again."),
    PIN_LOCKED("Too many wrong PINs. Wait 5 minutes, then try again."),
    NOT_ALLOWED("Only the owner can do that."),
    ITEM_NOT_FOUND("No item with that tag or VIN in the shop system. Check the tag, or log it as new."),
    SHORT_ID_AMBIGUOUS("More than one item ends in those 8 characters. Scan the QR code or type the VIN instead."),
    TAG_NOT_RECORDED("No label has been recorded for this item yet. If another phone printed it, wait until that phone connects, then scan again."),
    ITEM_ID_INVALID("This phone sent an item ID the shop system can't use. Update the app and try again."),
    ITEM_ID_TAKEN("That item ID is already used for a different item. Nothing was saved. Try saving again."),
    MISSING_DETAILS("Some details are missing."),
    VIN_NOT_CONFIRMED("This VIN doesn't check out. Compare it to the frame and tick \"VIN matches the frame\" to save it anyway."),
    VIN_ALREADY_LOGGED("Another item already has this VIN. Open that item instead of logging it twice."),
    STATUS_CHANGE_NOT_ALLOWED("That change isn't allowed from the item's current status."),
    BAD_REQUEST("The app sent something the shop system couldn't read. Update the app and try again."),
    SERVER_ERROR("Something went wrong in the shop system, and nothing was saved. Try again. If it keeps happening, tell the owner.")
}
