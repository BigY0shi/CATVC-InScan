package com.example.data

import android.content.Context

// The dealership's own details, printed as the seller on every document. Entered in Settings,
// never defaulted: until it is complete, documents refuse to generate.
data class DealerProfile(
    val legalName: String = "",
    val dealerNumber: String = "",
    val address: String = "",
    val phone: String = ""
) {
    fun missingFields(): List<String> = buildList {
        if (legalName.isBlank()) add("dealership legal name")
        if (dealerNumber.isBlank()) add("Oregon dealer number")
        if (address.isBlank()) add("dealership address")
    }

    val isComplete: Boolean get() = missingFields().isEmpty()
}

class DealerProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dealer_profile", Context.MODE_PRIVATE)

    fun load(): DealerProfile = DealerProfile(
        legalName = prefs.getString(KEY_NAME, "") ?: "",
        dealerNumber = prefs.getString(KEY_NUMBER, "") ?: "",
        address = prefs.getString(KEY_ADDRESS, "") ?: "",
        phone = prefs.getString(KEY_PHONE, "") ?: ""
    )

    fun save(profile: DealerProfile) {
        prefs.edit()
            .putString(KEY_NAME, profile.legalName.trim())
            .putString(KEY_NUMBER, profile.dealerNumber.trim())
            .putString(KEY_ADDRESS, profile.address.trim())
            .putString(KEY_PHONE, profile.phone.trim())
            .apply()
    }

    private companion object {
        const val KEY_NAME = "legal_name"
        const val KEY_NUMBER = "dealer_number"
        const val KEY_ADDRESS = "address"
        const val KEY_PHONE = "phone"
    }
}
