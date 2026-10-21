package com.example.data

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InventoryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = BarcodeRepository(database.barcodeDao())

    val allMappings: StateFlow<List<BarcodeMapping>> = repository.allMappings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allListings: StateFlow<List<InventoryListing>> = repository.allListings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // Returns the typed price, or null after setting a plain message when it can't be used.
    private fun priceOrError(): Double? =
        when (val result = PriceParser.parse(priceInput.value)) {
            is PriceResult.Valid -> {
                _formError.value = null
                result.dollars
            }
            is PriceResult.Invalid -> {
                _formError.value = result.message
                null
            }
        }

    // Active listing for Sales Preparation
    private val _saleListing = MutableStateFlow<InventoryListing?>(null)
    val saleListing: StateFlow<InventoryListing?> = _saleListing.asStateFlow()

    // Set the currently scanned barcode, find mapping, and populate forms
    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            _scannedBarcode.value = barcode
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
                        details.errorText
                    } else {
                        "NHTSA was unable to decode this VIN. Verify the characters and try again."
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
    }

    // Save Barcode Mapping (catalogs it for future use)
    // Returns false, with a plain message in formError, when the form can't be saved.
    fun saveBarcodeMapping(): Boolean {
        val barcode = _scannedBarcode.value ?: return false
        val price = priceOrError() ?: return false
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

    // Add current mapped vehicle to Inventory Listings (the "listing file")
    fun addCurrentToInventoryListing(): Boolean {
        val barcode = _scannedBarcode.value ?: return false
        val price = priceOrError() ?: return false
        val listing = InventoryListing(
            barcode = barcode,
            make = makeInput.value,
            model = modelInput.value,
            year = yearInput.value,
            color = colorInput.value,
            vehicleType = vehicleTypeInput.value,
            vin = vinInput.value,
            price = price,
            notes = notesInput.value,
            imageUrl = _imageUriOrBase64.value,
            status = "In Stock"
        )
        viewModelScope.launch {
            repository.insertListing(listing)
            // also make sure the barcode mapping is saved for future scans
            saveBarcodeMapping()
            clearScan()
        }
        return true
    }

    // Delete a listing
    fun deleteListing(id: Int) {
        viewModelScope.launch {
            repository.deleteListing(id)
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

    // Draft a sale directly from current scanned barcode fields (even if not saved to Listings yet)
    fun prepareSaleFromCurrentScan(): Boolean {
        val barcode = _scannedBarcode.value ?: "MAN-SALE-${System.currentTimeMillis() % 1000000}"
        val priceVal = priceOrError() ?: return false
        val listing = InventoryListing(
            id = -1, // Use -1 to flag direct scanned draft sale
            barcode = barcode,
            make = makeInput.value,
            model = modelInput.value,
            year = yearInput.value,
            color = colorInput.value,
            vehicleType = vehicleTypeInput.value,
            vin = vinInput.value,
            price = priceVal,
            notes = notesInput.value,
            imageUrl = _imageUriOrBase64.value,
            status = "In Stock"
        )
        prepareSale(listing)
        return true
    }

    // Leave the sale screen without changing the vehicle's status.
    fun cancelSale() {
        _saleListing.value = null
    }

    // Complete sale (updates listing status to "Sold"). Only an explicit "Mark as sold" action calls this.
    fun completeSale() {
        val listing = _saleListing.value ?: return
        viewModelScope.launch {
            if (listing.id == -1) {
                // Insert directly as Sold listing
                val soldListing = listing.copy(status = "Sold")
                repository.insertListing(soldListing)
                // Also auto-save/catalog the mapping for future ease
                saveBarcodeMapping()
            } else {
                repository.updateListingStatus(listing.id, "Sold")
            }
            _saleListing.value = null
            clearScan()
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

    private fun todayIso(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
}
