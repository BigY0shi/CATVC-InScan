package com.example.data

import android.app.Application
import com.countryatv.core.ItemId
import com.countryatv.core.Money
import com.countryatv.core.MoneyResult
import com.countryatv.core.UnitStatus
import com.countryatv.core.UnitType
import com.countryatv.core.Vin
import com.countryatv.core.VinCheck
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.shop.ConnectionState
import com.example.shop.ItemEdits
import com.example.shop.LegacyPlan
import com.example.shop.NewUnit
import com.example.shop.ShopClient
import com.example.shop.ShopResult
import com.example.shop.ShopSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

class InventoryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = BarcodeRepository(database.barcodeDao())
    private val dealerProfileStore = DealerProfileStore(application)
    private val shopDao = database.shopDao()
    private val shop = ShopClient(application)
    private val sync = ShopSync(shopDao, shop)

    private val _dealerProfile = MutableStateFlow(dealerProfileStore.load())
    val dealerProfile: StateFlow<DealerProfile> = _dealerProfile.asStateFlow()

    fun saveDealerProfile(profile: DealerProfile) {
        dealerProfileStore.save(profile)
        _dealerProfile.value = dealerProfileStore.load()
    }

    val allMappings: StateFlow<List<BarcodeMapping>> = repository.allMappings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Shop system: connection, sync, and this phone's copy of the inventory ----

    val connection: StateFlow<ConnectionState> = shop.state

    private val shopUnits: StateFlow<List<ShopUnitEntity>> = shopDao.units()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // The listing file now shows the shop system's items (this phone's copy). Retired items are gone
    // from the shop floor, so they're not listed. The screens keep using InventoryListing; its id is
    // the item's small local number and its "barcode" is the item's shop-system ID.
    val allListings: StateFlow<List<InventoryListing>> = shopUnits
        .map { units -> units.filter { it.status != UnitStatus.RETIRED.name }.map { it.toListing() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val outbox: StateFlow<List<OutboxEntry>> = shopDao.outboxFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Why the last attempt to reach the shop system stopped (no signal, needs sign-in). Null when it worked.
    private val _syncProblem = MutableStateFlow<String?>(null)
    val syncProblem: StateFlow<String?> = _syncProblem.asStateFlow()

    // A one-off message for the employee (shown once, then cleared with consumeNotice).
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    fun consumeNotice() { _notice.value = null }

    fun addressProblem(url: String): String? = shop.problemWithAddress(url)

    // Returns null when this phone is now set up, otherwise what went wrong in plain words.
    suspend fun connect(serverUrl: String, setupCode: String, phoneLabel: String): String? =
        when (val r = shop.enroll(serverUrl, setupCode, phoneLabel)) {
            is ShopResult.Ok -> null
            is ShopResult.Refused -> r.message
            is ShopResult.NotSignedIn -> r.message
            is ShopResult.Unreachable -> r.message
        }

    suspend fun employeeNames(): ShopResult<List<String>> = shop.employeeNames()

    suspend fun signIn(name: String, pin: String): String? =
        when (val r = shop.signIn(name, pin)) {
            is ShopResult.Ok -> { syncSoon(); null }
            is ShopResult.Refused -> r.message
            is ShopResult.NotSignedIn -> r.message
            is ShopResult.Unreachable -> r.message
        }

    fun signOut() = shop.signOut()

    fun syncSoon() {
        viewModelScope.launch { runSync() }
    }

    // One round: send what's waiting, oldest first, then fetch the shop system's latest copy.
    suspend fun runSync() {
        if (!connection.value.enrolled || connection.value.signedInAs == null) return
        val report = sync.syncNow()
        _syncProblem.value = report.stoppedBecause
        if (report.overlapped.isNotEmpty()) {
            _notice.value = "Someone else changed ${report.overlapped.size} of these items just before you. " +
                "Your change was saved on top of theirs; the earlier details are kept in the shop's history."
        }
    }

    // ---- Changes the shop system refused ----

    fun itemName(itemId: String): String =
        shopUnits.value.firstOrNull { it.id == itemId }?.let { listOfNotNull(it.year?.toString(), it.make, it.model).joinToString(" ") }
            ?: itemId

    fun retryChange(entry: OutboxEntry) {
        viewModelScope.launch { sync.retry(entry); runSync() }
    }

    // For a VIN that fails the check-digit test: the employee looked at the frame and it matches.
    fun retryWithVinMatchingFrame(entry: OutboxEntry) {
        viewModelScope.launch {
            val unit = sync.newUnitIn(entry)
            val edits = sync.editsIn(entry)
            when {
                unit != null -> sync.retry(entry, correctedUnit = unit.copy(vinFrameVerified = true))
                edits != null -> sync.retry(entry, correctedEdits = edits.copy(vinFrameVerified = true))
                else -> sync.retry(entry)
            }
            runSync()
        }
    }

    // Only from an explicit "Throw this change away" button, after the employee confirmed.
    fun discardChange(entry: OutboxEntry) {
        viewModelScope.launch { sync.discard(entry) }
    }

    // ---- Items logged on this phone before the shop system ----

    data class LegacyRow(val old: InventoryListing, val plan: LegacyPlan, val queuedAs: String?)

    val legacyRows: StateFlow<List<LegacyRow>> = combine(repository.allListings, shopDao.legacyUploads()) { olds, uploads ->
        val byLegacyId = uploads.associateBy { it.legacyId }
        olds.map { LegacyRow(it, LegacyPlan.of(it), byLegacyId[it.id]?.itemId) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Queues every old item that is complete enough. Items with gaps stay listed with their reasons.
    fun uploadLegacyItems() {
        viewModelScope.launch {
            database.withTransaction {
                for (row in legacyRows.value) {
                    val plan = row.plan as? LegacyPlan.Ready ?: continue
                    if (shopDao.legacyUploadFor(row.old.id) != null) continue
                    sync.logNew(plan.unit)
                    plan.statusSteps.forEach { sync.logStatus(plan.unit.id, it) }
                    shopDao.recordLegacyUpload(LegacyUpload(row.old.id, plan.unit.id, System.currentTimeMillis()))
                }
            }
            runSync()
        }
    }

    // An old item with gaps: open it in the entry form so the employee can complete it. Saving it
    // logs it in the shop system and marks the old item as uploaded.
    private var fixingLegacyId: Int? = null
    private var fixingLegacySteps: List<UnitStatus> = emptyList()

    fun openLegacyInForm(old: InventoryListing) {
        clearScan()
        _scannedBarcode.value = old.barcode
        makeInput.value = old.make
        modelInput.value = old.model
        yearInput.value = old.year
        colorInput.value = old.color
        vehicleTypeInput.value = old.vehicleType
        vinInput.value = old.vin
        priceInput.value = if (old.price > 0) Money.format(kotlin.math.round(old.price * 100).toLong()).removePrefix("$").replace(",", "") else ""
        notesInput.value = old.notes
        _imageUriOrBase64.value = old.imageUrl
        fixingLegacyId = old.id
        // Keep the status it had on this phone (Incoming, In Stock or Sold).
        fixingLegacySteps = when (old.status.trim().lowercase()) {
            "incoming" -> emptyList()
            "sold" -> listOf(UnitStatus.IN_STOCK, UnitStatus.SOLD)
            else -> listOf(UnitStatus.IN_STOCK)
        }
    }

    // ---- Removing an item from the listing file ----

    enum class Removal { NOT_SENT_YET, NEEDS_OWNER, OWNER_RETIRES }

    fun removalFor(listing: InventoryListing): Removal {
        val unit = shopUnits.value.firstOrNull { it.localId == listing.id }
        return when {
            unit == null || unit.version == 0 -> Removal.NOT_SENT_YET
            connection.value.role == "admin" -> Removal.OWNER_RETIRES
            else -> Removal.NEEDS_OWNER
        }
    }

    val allCustomDocuments: StateFlow<List<CustomDocument>> = repository.allCustomDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected document and active mapped zones
    private val _selectedDocumentId = MutableStateFlow<Int?>(null)
    val selectedDocumentId: StateFlow<Int?> = _selectedDocumentId.asStateFlow()

    private val _currentDocZones = MutableStateFlow<List<DocumentZone>>(emptyList())
    val currentDocZones: StateFlow<List<DocumentZone>> = _currentDocZones.asStateFlow()

    // Currently Scanned Barcode and State
    private val _scannedBarcode = MutableStateFlow<String?>(null)
    val scannedBarcode: StateFlow<String?> = _scannedBarcode.asStateFlow()

    // Loaded Mapping for Scanned Barcode (if any)
    private val _loadedMapping = MutableStateFlow<BarcodeMapping?>(null)
    val loadedMapping: StateFlow<BarcodeMapping?> = _loadedMapping.asStateFlow()

    // Form inputs for Manual Entry or Editing Mappings
    val makeInput = MutableStateFlow("")
    val modelInput = MutableStateFlow("")
    val yearInput = MutableStateFlow("")
    val colorInput = MutableStateFlow("")
    val vehicleTypeInput = MutableStateFlow("ATV") // ATV, UTV, Dirt Bike, Snowmobile, Jet Ski, Other
    val vinInput = MutableStateFlow("")
    val priceInput = MutableStateFlow("")
    val notesInput = MutableStateFlow("")
    private val _imageUriOrBase64 = MutableStateFlow<String?>(null)
    val imageUriOrBase64: StateFlow<String?> = _imageUriOrBase64.asStateFlow()

    // Loading status for Gemini API Analysis
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    // Loading status for NHTSA vPIC VIN decoding
    private val _isDecodingVin = MutableStateFlow(false)
    val isDecodingVin: StateFlow<Boolean> = _isDecodingVin.asStateFlow()

    private val _vinDecodeError = MutableStateFlow<String?>(null)
    val vinDecodeError: StateFlow<String?> = _vinDecodeError.asStateFlow()

    // Customer / Sale Pre-population Form
    val buyerName = MutableStateFlow("")
    val buyerEmail = MutableStateFlow("")
    val buyerPhone = MutableStateFlow("")
    val buyerAddress = MutableStateFlow("")
    val saleDate = MutableStateFlow(todayIso())
    val downPaymentInput = MutableStateFlow("")
    val interestRateInput = MutableStateFlow("")
    val loanTermInput = MutableStateFlow("")

    // Plain-language problem with the entry form, shown to the employee. Null when there is none.
    private val _formError = MutableStateFlow<String?>(null)
    val formError: StateFlow<String?> = _formError.asStateFlow()

    fun clearFormError() {
        _formError.value = null
    }

    // Returns the typed price in cents, or null after setting a plain message when it can't be used.
    private fun priceCentsOrError(): Long? =
        when (val result = Money.parseDollars(priceInput.value)) {
            is MoneyResult.Valid -> {
                _formError.value = null
                result.cents
            }
            is MoneyResult.Invalid -> {
                _formError.value = result.message
                null
            }
        }

    // When the scan matched an item already in the shop system, that item; the form then edits it.
    private val _scannedItem = MutableStateFlow<ShopUnitEntity?>(null)
    val scannedItem: StateFlow<ShopUnitEntity?> = _scannedItem.asStateFlow()

    // A note about the lookup itself, e.g. the shop system couldn't be checked.
    private val _lookupNote = MutableStateFlow<String?>(null)
    val lookupNote: StateFlow<String?> = _lookupNote.asStateFlow()

    // Ticked by the employee when a VIN fails the check-digit test but matches the frame exactly.
    val vinMatchesFrame = MutableStateFlow(false)

    // Checks the whole form. Returns the item to save, or null after setting a plain message.
    private fun formUnitOrError(id: String): NewUnit? {
        val cents = priceCentsOrError() ?: return null
        val missing = buildList {
            if (makeInput.value.isBlank()) add("make")
            if (modelInput.value.isBlank()) add("model")
        }
        if (missing.isNotEmpty()) {
            _formError.value = "Fill in the ${missing.joinToString(" and ")} first."
            return null
        }
        val yearText = yearInput.value.trim()
        val year = if (yearText.isEmpty()) null else yearText.toIntOrNull()?.takeIf { it in 1950..2100 } ?: run {
            _formError.value = "The year should be a 4-digit model year, like 2025."
            return null
        }
        val type = UnitType.fromLabel(vehicleTypeInput.value) ?: run {
            _formError.value = "Pick the vehicle type."
            return null
        }
        val vin = Vin.normalize(vinInput.value).ifEmpty { null }
        val check = vin?.let { Vin.check(it) }
        if (check is VinCheck.Fail && !vinMatchesFrame.value) {
            _formError.value = check.message
            return null
        }
        _formError.value = null
        return NewUnit(
            id = id, make = makeInput.value.trim(), model = modelInput.value.trim(), year = year,
            color = colorInput.value.trim(), unitType = type.label, vin = vin,
            vinFrameVerified = check is VinCheck.Fail, priceCents = cents, notes = notesInput.value.trim()
        )
    }

    private fun fillForm(unit: ShopUnitEntity) {
        makeInput.value = unit.make
        modelInput.value = unit.model
        yearInput.value = unit.year?.toString().orEmpty()
        colorInput.value = unit.color
        vehicleTypeInput.value = unit.unitType
        vinInput.value = unit.vin.orEmpty()
        priceInput.value = Money.format(unit.priceCents).removePrefix("$").replace(",", "")
        notesInput.value = unit.notes
        _imageUriOrBase64.value = null
        // Someone already confirmed this VIN against the frame; it stays confirmed while unchanged.
        vinMatchesFrame.value = unit.vinCheck == "fail"
    }

    // Active listing for Sales Preparation
    private val _saleListing = MutableStateFlow<InventoryListing?>(null)
    val saleListing: StateFlow<InventoryListing?> = _saleListing.asStateFlow()

    // Set the currently scanned barcode, find mapping, and populate forms
    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            _scannedBarcode.value = barcode
            fixingLegacyId = null
            _scannedItem.value = null
            _lookupNote.value = null
            _formError.value = null
            vinMatchesFrame.value = false

            // Our QR tag, an item ID or a VIN: this phone's copy first, then the shop system.
            val known = sync.findOnPhone(barcode) ?: when (val r = sync.findOnServer(barcode)) {
                is ShopResult.Ok -> r.value
                is ShopResult.Refused -> null
                is ShopResult.NotSignedIn -> { _lookupNote.value = r.message; null }
                is ShopResult.Unreachable -> {
                    _lookupNote.value = "Couldn't check the shop system: ${r.message} If this item was already logged on another phone, don't log it again."
                    null
                }
            }
            if (known != null) {
                _loadedMapping.value = null
                _scannedItem.value = known
                fillForm(known)
                return@launch
            }

            val mapping = repository.getMappingByBarcode(barcode)
            _loadedMapping.value = mapping

            if (mapping != null) {
                // Pre-populate fields for immediate view/edit
                makeInput.value = mapping.make
                modelInput.value = mapping.model
                yearInput.value = mapping.year
                colorInput.value = mapping.color
                vehicleTypeInput.value = mapping.vehicleType
                vinInput.value = mapping.vin
                priceInput.value = mapping.price.toString()
                notesInput.value = mapping.notes
                _imageUriOrBase64.value = mapping.imageUrl
            } else {
                // Clear fields for manual entry
                makeInput.value = ""
                modelInput.value = ""
                yearInput.value = ""
                colorInput.value = ""
                vehicleTypeInput.value = "Other"
                vinInput.value = ""
                priceInput.value = ""
                notesInput.value = ""
                _imageUriOrBase64.value = null

                // Auto-decode if scanned barcode looks like a 17-character VIN
                val isVinFormat = barcode.trim().length == 17 && barcode.trim().matches(Regex("[A-HJ-NPR-Z0-9a-hj-npr-z0-9]{17}"))
                if (isVinFormat) {
                    decodeVinWithNhtsa(barcode)
                }
            }
        }
    }

    // Call public NHTSA vPIC API to decode VIN
    fun decodeVinWithNhtsa(vin: String) {
        val cleanVin = vin.trim().uppercase()
        if (cleanVin.isBlank()) return

        _isDecodingVin.value = true
        _vinDecodeError.value = null

        viewModelScope.launch {
            try {
                val details = com.example.network.NhtsaClient.decodeVinDetails(cleanVin)
                if (details != null && !details.make.isNullOrBlank()) {
                    makeInput.value = details.make ?: ""
                    modelInput.value = details.model ?: ""
                    yearInput.value = details.modelYear ?: ""
                    vinInput.value = cleanVin

                    // Normalize vehicle type
                    val nhtsaType = (details.vehicleType ?: "").lowercase()
                    vehicleTypeInput.value = when {
                        nhtsaType.contains("atv") -> "ATV"
                        nhtsaType.contains("dirt bike") || nhtsaType.contains("motorcycle") -> "Dirt Bike"
                        nhtsaType.contains("snowmobile") -> "Snowmobile"
                        nhtsaType.contains("jet ski") || nhtsaType.contains("watercraft") -> "Jet Ski"
                        nhtsaType.contains("multipurpose passenger vehicle") || nhtsaType.contains("utv") || nhtsaType.contains("side-by-side") || nhtsaType.contains("recreational off-highway vehicle") -> "Side-by-Side (UTV)"
                        else -> "Other"
                    }

                    // Compose detailed notes from technical variables
                    val techNotes = buildString {
                        append("Decoded via NHTSA vPIC.")
                        if (!details.bodyClass.isNullOrBlank()) {
                            append("\nBody Class: ${details.bodyClass}")
                        }
                        if (!details.displacementCC.isNullOrBlank()) {
                            append("\nDisplacement: ${details.displacementCC} cc")
                        }
                        if (!details.engineHP.isNullOrBlank()) {
                            append("\nEngine Power: ${details.engineHP} HP")
                        }
                        if (!details.driveType.isNullOrBlank()) {
                            append("\nDrive Type: ${details.driveType}")
                        }
                    }
                    notesInput.value = techNotes
                    _vinDecodeError.value = null
                } else {
                    val errMsg = if (details != null && !details.errorText.isNullOrBlank()) {
                        "NHTSA couldn't decode this VIN. Check it against the frame, or type the details in by hand. (NHTSA's note: ${details.errorText})"
                    } else {
                        "NHTSA doesn't recognize this VIN. Check it against the frame, or type the details in by hand."
                    }
                    _vinDecodeError.value = errMsg
                }
            } catch (e: java.io.IOException) {
                _vinDecodeError.value = "Couldn't reach the NHTSA VIN lookup. Check the phone's internet connection and try again, or type the details in by hand."
            } catch (e: Exception) {
                _vinDecodeError.value = "The NHTSA VIN lookup sent back something unexpected. Type the details in by hand."
            } finally {
                _isDecodingVin.value = false
            }
        }
    }

    // Manually update inputs
    fun setImageBase64(base64: String?) {
        _imageUriOrBase64.value = base64
    }

    // Reset scan state
    fun clearScan() {
        _scannedBarcode.value = null
        _loadedMapping.value = null
        _scannedItem.value = null
        _lookupNote.value = null
        vinMatchesFrame.value = false
        fixingLegacyId = null
    }

    // Save Barcode Mapping (catalogs it for future use)
    // Returns false, with a plain message in formError, when the form can't be saved.
    fun saveBarcodeMapping(): Boolean {
        val barcode = _scannedBarcode.value ?: return false
        val price = priceCentsOrError()?.div(100.0) ?: return false
        val mapping = BarcodeMapping(
            barcode = barcode,
            make = makeInput.value,
            model = modelInput.value,
            year = yearInput.value,
            color = colorInput.value,
            vehicleType = vehicleTypeInput.value,
            vin = vinInput.value,
            price = price,
            notes = notesInput.value,
            imageUrl = _imageUriOrBase64.value
        )
        viewModelScope.launch {
            repository.insertMapping(mapping)
            _loadedMapping.value = mapping
        }
        return true
    }

    // Saves the form: a new item goes into the shop system (via this phone's queue) as In Stock,
    // as this button always did; a scanned item that's already logged gets the changed details.
    // Returns false, with a plain message in formError, when the form can't be saved.
    fun addCurrentToInventoryListing(): Boolean {
        if (_scannedBarcode.value == null) return false
        val existing = _scannedItem.value
        val unit = formUnitOrError(existing?.id ?: ItemId.new()) ?: return false
        viewModelScope.launch {
            if (existing != null) {
                val edits = editsBetween(existing, unit)
                if (edits != null) sync.logEdits(existing.id, edits)
            } else {
                val legacyId = fixingLegacyId
                val steps = if (legacyId != null) fixingLegacySteps else listOf(UnitStatus.IN_STOCK)
                database.withTransaction {
                    sync.logNew(unit)
                    steps.forEach { sync.logStatus(unit.id, it) }
                    if (legacyId != null && shopDao.legacyUploadFor(legacyId) == null) {
                        shopDao.recordLegacyUpload(LegacyUpload(legacyId, unit.id, System.currentTimeMillis()))
                    }
                }
                // Also keep the old barcode catalog entry, as before.
                saveBarcodeMapping()
            }
            clearScan()
            runSync()
        }
        return true
    }

    private fun editsBetween(before: ShopUnitEntity, after: NewUnit): ItemEdits? {
        val edits = ItemEdits(
            make = after.make.takeIf { it != before.make },
            model = after.model.takeIf { it != before.model },
            year = after.year.takeIf { it != before.year },
            color = after.color.takeIf { it != before.color },
            unitType = after.unitType.takeIf { it != before.unitType },
            vin = (after.vin ?: "").takeIf { it != (before.vin ?: "") },
            vinFrameVerified = after.vinFrameVerified,
            priceCents = after.priceCents.takeIf { it != before.priceCents },
            notes = after.notes.takeIf { it != before.notes }
        )
        return if (edits.copy(vinFrameVerified = false) == ItemEdits()) null else edits
    }

    // An item that never reached the shop system is simply removed from this phone.
    fun removeUnsentListing(listing: InventoryListing) {
        viewModelScope.launch {
            val unit = shopDao.unitByLocalId(listing.id) ?: return@launch
            if (unit.version != 0) return@launch
            shopDao.outbox().firstOrNull { it.itemId == unit.id && it.kind == ShopSync.KIND_CREATE }
                ?.let { sync.discard(it) }
        }
    }

    // Owner only, with a reason. The item stays in the shop's records as Retired.
    fun retireListing(listing: InventoryListing, reason: String) {
        viewModelScope.launch {
            val unit = shopDao.unitByLocalId(listing.id) ?: return@launch
            sync.logStatus(unit.id, UnitStatus.RETIRED, reason.trim())
            runSync()
        }
    }

    // Delete a barcode mapping
    fun deleteMapping(barcode: String) {
        viewModelScope.launch {
            repository.deleteMapping(barcode)
        }
    }

    // Prepare sale flow
    fun prepareSale(listing: InventoryListing) {
        _saleListing.value = listing
        // Every sale starts blank: buyer details and finance terms are typed in for this customer.
        buyerName.value = ""
        buyerEmail.value = ""
        buyerPhone.value = ""
        buyerAddress.value = ""
        saleDate.value = todayIso()
        downPaymentInput.value = ""
        interestRateInput.value = ""
        loanTermInput.value = ""
    }

    // Not yet in the shop system: the item to log when this drafted sale is completed.
    private var draftSaleUnit: NewUnit? = null

    // Draft a sale from the scan screen. A scanned item already in the shop system is sold as it is
    // recorded there; otherwise the form must be complete, and the item is logged when the sale completes.
    fun prepareSaleFromCurrentScan(): Boolean {
        _scannedItem.value?.let { known ->
            draftSaleUnit = null
            prepareSale(known.toListing())
            return true
        }
        val unit = formUnitOrError(ItemId.new()) ?: return false
        draftSaleUnit = unit
        val listing = InventoryListing(
            id = -1, // not in the shop system yet
            barcode = unit.id,
            make = unit.make,
            model = unit.model,
            year = unit.year?.toString().orEmpty(),
            color = unit.color,
            vehicleType = unit.unitType,
            vin = unit.vin.orEmpty(),
            price = unit.priceCents / 100.0,
            notes = unit.notes,
            imageUrl = _imageUriOrBase64.value,
            status = UnitStatus.IN_STOCK.label
        )
        prepareSale(listing)
        return true
    }

    // Leave the sale screen without changing the vehicle's status.
    fun cancelSale() {
        _saleListing.value = null
        draftSaleUnit = null
    }

    // Records the sale as a status change (In Stock → Sold). Only an explicit "Mark as sold" calls this.
    // INTERIM until M3, which replaces this with the full sale flow.
    fun completeSale() {
        val listing = _saleListing.value ?: return
        viewModelScope.launch {
            val id: String
            val current: UnitStatus
            if (listing.id == -1) {
                val unit = draftSaleUnit ?: return@launch
                sync.logNew(unit)
                id = unit.id
                current = UnitStatus.INCOMING
            } else {
                val unit = shopDao.unitByLocalId(listing.id) ?: return@launch
                id = unit.id
                current = UnitStatus.valueOf(unit.status)
            }
            when (current) {
                UnitStatus.INCOMING -> { sync.logStatus(id, UnitStatus.IN_STOCK); sync.logStatus(id, UnitStatus.SOLD) }
                UnitStatus.IN_STOCK -> sync.logStatus(id, UnitStatus.SOLD)
                UnitStatus.SOLD -> Unit
                else -> _notice.value = "This item is ${current.label}, so it can't be marked as sold."
            }
            _saleListing.value = null
            draftSaleUnit = null
            clearScan()
            runSync()
        }
    }

    // Photo autofill. The Gemini key must never ship inside the app, so this stays off until the
    // shop server can make the call on the phone's behalf (milestone M1). Nothing is filled in.
    @Suppress("UNUSED_PARAMETER")
    fun analyzeImageWithGemini(bitmap: Bitmap) {
        _isAnalyzing.value = false
        _analysisError.value = "Photo autofill is switched off until the shop server is set up. Type the details in for now."
    }

    // Custom document mapping actions
    fun selectDocument(docId: Int?) {
        _selectedDocumentId.value = docId
        if (docId != null) {
            viewModelScope.launch {
                repository.getZonesForDocument(docId).collect {
                    _currentDocZones.value = it
                }
            }
        } else {
            _currentDocZones.value = emptyList()
        }
    }

    fun addNewDocument(name: String, imageUrl: String) {
        viewModelScope.launch {
            val docId = repository.insertCustomDocument(CustomDocument(name = name, imageUrl = imageUrl))
            selectDocument(docId.toInt())
        }
    }

    fun deleteDocument(docId: Int) {
        viewModelScope.launch {
            repository.deleteCustomDocument(docId)
            repository.deleteZonesForDocument(docId)
            if (_selectedDocumentId.value == docId) {
                selectDocument(null)
            }
        }
    }

    fun addDocumentZone(fieldName: String, left: Float, top: Float, right: Float, bottom: Float) {
        val docId = _selectedDocumentId.value ?: return
        viewModelScope.launch {
            repository.insertDocumentZone(
                DocumentZone(
                    documentId = docId,
                    fieldName = fieldName,
                    leftPercent = left,
                    topPercent = top,
                    rightPercent = right,
                    bottomPercent = bottom
                )
            )
        }
    }

    fun deleteDocumentZone(zoneId: Int) {
        viewModelScope.launch {
            repository.deleteDocumentZone(zoneId)
        }
    }

    private fun ShopUnitEntity.toListing() = InventoryListing(
        id = localId,
        barcode = id,
        make = make,
        model = model,
        year = year?.toString().orEmpty(),
        color = color,
        vehicleType = unitType,
        vin = vin.orEmpty(),
        price = priceCents / 100.0,
        notes = notes,
        scanTimestamp = Instant.parse(updatedAt).toEpochMilli(),
        status = UnitStatus.entries.firstOrNull { it.name == status }?.label ?: status
    )

    private fun todayIso(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
}
