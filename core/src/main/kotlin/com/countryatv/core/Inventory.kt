package com.countryatv.core

enum class UnitType(val label: String) {
    ATV("ATV"),
    UTV("Side-by-Side (UTV)"),
    DIRT_BIKE("Dirt Bike"),
    SNOWMOBILE("Snowmobile"),
    PWC("Jet Ski"),
    OTHER("Other");

    companion object {
        fun fromLabel(label: String): UnitType? = entries.firstOrNull { it.label.equals(label.trim(), ignoreCase = true) || it.name.equals(label.trim(), ignoreCase = true) }
    }
}

// Where an item is in its life. Only an explicit, labelled action changes it; navigation never does.
enum class UnitStatus(val label: String) {
    INCOMING("Incoming"),
    IN_STOCK("In Stock"),
    SALE_PENDING("Sale Pending"),
    SOLD("Sold"),
    RETIRED("Retired");

    fun canMoveTo(next: UnitStatus): Boolean = next in ALLOWED.getValue(this)

    private companion object {
        val ALLOWED = mapOf(
            INCOMING to setOf(IN_STOCK, RETIRED),
            IN_STOCK to setOf(SALE_PENDING, RETIRED),
            SALE_PENDING to setOf(IN_STOCK, SOLD),
            SOLD to setOf(IN_STOCK),
            RETIRED to emptySet()
        )
    }
}
