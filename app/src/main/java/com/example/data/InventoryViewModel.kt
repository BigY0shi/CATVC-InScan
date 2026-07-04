package com.example.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.GeminiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream

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
    val saleDate = MutableStateFlow("2026-07-03")
    val downPaymentInput = MutableStateFlow("2000")
    val interestRateInput = MutableStateFlow("5.9")
    val loanTermInput = MutableStateFlow("60")

    // Active listing for Sales Preparation
    private val _saleListing = MutableStateFlow<InventoryListing?>(null)
    val saleListing: StateFlow<InventoryListing?> = _saleListing.asStateFlow()

    init {
        // Pre-populate some dummy mappings so the app is immediately usable and fully loaded!
        viewModelScope.launch {
            if (repository.getMappingByBarcode("POL-RZR-2024") == null) {
                repository.insertMapping(
                    BarcodeMapping(
                        barcode = "POL-RZR-2024",
                        make = "Polaris",
                        model = "RZR XP 1000",
                        year = "2024",
                        color = "Titanium Matte Metallic",
                        vehicleType = "Side-by-Side (UTV)",
                        vin = "4Y1BLA248RA890124",
                        price = 21999.00,
                        notes = "Equipped with Rockford Fosgate Stage 3 Audio, standard offroad tires, sport suspension."
                    )
                )
                repository.insertMapping(
                    BarcodeMapping(
                        barcode = "CAN-MAV-2023",
                        make = "Can-Am",
                        model = "Maverick X3 DS Turbo",
                        year = "2023",
                        color = "Can-Am Red",
                        vehicleType = "Side-by-Side (UTV)",
                        vin = "3JBXAA01XNP102431",
                        price = 22499.00,
                        notes = "Turbocharged Rotax ACE engine. Fox 2.5 Podium QS3 shocks. Ready for dunes."
                    )
                )
                repository.insertMapping(
                    BarcodeMapping(
                        barcode = "YAM-RAP-2024",
                        make = "Yamaha",
                        model = "Raptor 700R SE",
                        year = "2024",
                        color = "Racing Blue",
                        vehicleType = "ATV",
                        vin = "5YMX400Y2RA721345",
                        price = 10299.00,
                        notes = "Special Edition with premium colorway and adjustable piggyback shocks."
                    )
                )
                repository.insertMapping(
                    BarcodeMapping(
                        barcode = "HON-CRF-2024",
                        make = "Honda",
                        model = "CRF450R",
                        year = "2024",
                        color = "Extreme Red",
                        vehicleType = "Dirt Bike",
                        vin = "JH2PE04A3RK002512",
                        price = 9699.00,
                        notes = "Championship-winning motocross bike. Renthal Fatbar handlebars, selectable torque control."
                    )
                )
            }

            // Pre-populate custom documents and template zone mappings
            if (repository.allCustomDocuments.first().isEmpty()) {
                val doc1Id = repository.insertCustomDocument(
                    CustomDocument(
                        name = "Powersports Retail Bill of Sale",
                        imageUrl = "https://images.unsplash.com/photo-1554415707-6e8cfc93fe23?auto=format&fit=crop&q=80&w=600"
                    )
                )
                repository.insertDocumentZone(DocumentZone(documentId = doc1Id.toInt(), fieldName = "year", leftPercent = 0.12f, topPercent = 0.16f, rightPercent = 0.28f, bottomPercent = 0.23f))
                repository.insertDocumentZone(DocumentZone(documentId = doc1Id.toInt(), fieldName = "make", leftPercent = 0.30f, topPercent = 0.16f, rightPercent = 0.50f, bottomPercent = 0.23f))
                repository.insertDocumentZone(DocumentZone(documentId = doc1Id.toInt(), fieldName = "model", leftPercent = 0.52f, topPercent = 0.16f, rightPercent = 0.88f, bottomPercent = 0.23f))
                repository.insertDocumentZone(DocumentZone(documentId = doc1Id.toInt(), fieldName = "vin", leftPercent = 0.12f, topPercent = 0.27f, rightPercent = 0.48f, bottomPercent = 0.34f))
                repository.insertDocumentZone(DocumentZone(documentId = doc1Id.toInt(), fieldName = "price", leftPercent = 0.52f, topPercent = 0.27f, rightPercent = 0.88f, bottomPercent = 0.34f))
                repository.insertDocumentZone(DocumentZone(documentId = doc1Id.toInt(), fieldName = "buyerName", leftPercent = 0.12f, topPercent = 0.38f, rightPercent = 0.50f, bottomPercent = 0.45f))
                repository.insertDocumentZone(DocumentZone(documentId = doc1Id.toInt(), fieldName = "saleDate", leftPercent = 0.55f, topPercent = 0.38f, rightPercent = 0.88f, bottomPercent = 0.45f))

                val doc2Id = repository.insertCustomDocument(
                    CustomDocument(
                        name = "State DMV Registration & Title",
                        imageUrl = "https://images.unsplash.com/photo-1586075010923-2dd4570fb338?auto=format&fit=crop&q=80&w=600"
                    )
                )
                repository.insertDocumentZone(DocumentZone(documentId = doc2Id.toInt(), fieldName = "vin", leftPercent = 0.15f, topPercent = 0.18f, rightPercent = 0.48f, bottomPercent = 0.25f))
                repository.insertDocumentZone(DocumentZone(documentId = doc2Id.toInt(), fieldName = "year", leftPercent = 0.52f, topPercent = 0.18f, rightPercent = 0.65f, bottomPercent = 0.25f))
                repository.insertDocumentZone(DocumentZone(documentId = doc2Id.toInt(), fieldName = "make", leftPercent = 0.68f, topPercent = 0.18f, rightPercent = 0.88f, bottomPercent = 0.25f))
                repository.insertDocumentZone(DocumentZone(documentId = doc2Id.toInt(), fieldName = "buyerName", leftPercent = 0.15f, topPercent = 0.32f, rightPercent = 0.60f, bottomPercent = 0.39f))

                val doc3Id = repository.insertCustomDocument(
                    CustomDocument(
                        name = "Dealer Delivery Checklist",
                        imageUrl = "https://images.unsplash.com/photo-1506784983877-45594efa4cbe?auto=format&fit=crop&q=80&w=600"
                    )
                )
                repository.insertDocumentZone(DocumentZone(documentId = doc3Id.toInt(), fieldName = "make", leftPercent = 0.15f, topPercent = 0.14f, rightPercent = 0.45f, bottomPercent = 0.21f))
                repository.insertDocumentZone(DocumentZone(documentId = doc3Id.toInt(), fieldName = "model", leftPercent = 0.48f, topPercent = 0.14f, rightPercent = 0.85f, bottomPercent = 0.21f))
                repository.insertDocumentZone(DocumentZone(documentId = doc3Id.toInt(), fieldName = "price", leftPercent = 0.15f, topPercent = 0.24f, rightPercent = 0.45f, bottomPercent = 0.31f))
                repository.insertDocumentZone(DocumentZone(documentId = doc3Id.toInt(), fieldName = "buyerName", leftPercent = 0.15f, topPercent = 0.34f, rightPercent = 0.55f, bottomPercent = 0.41f))
            }
        }
    }

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
            } catch (e: Exception) {
                _vinDecodeError.value = "Failed to communicate with NHTSA: ${e.localizedMessage}"
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
    fun saveBarcodeMapping() {
        val barcode = _scannedBarcode.value ?: return
        val price = priceInput.value.toDoubleOrNull() ?: 0.0
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
    }

    // Add current mapped vehicle to Inventory Listings (the "listing file")
    fun addCurrentToInventoryListing() {
        val barcode = _scannedBarcode.value ?: return
        val price = priceInput.value.toDoubleOrNull() ?: 0.0
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
        // Prefill some buyer info defaults
        buyerName.value = "John Doe"
        buyerEmail.value = "johndoe@example.com"
        buyerPhone.value = "(555) 019-2834"
        buyerAddress.value = "1024 Ridge Crest Dr, Moab, UT 84532"
        downPaymentInput.value = (listing.price * 0.1).toInt().toString()
        interestRateInput.value = "6.5"
        loanTermInput.value = "60"
    }

    // Draft a sale directly from current scanned barcode fields (even if not saved to Listings yet)
    fun prepareSaleFromCurrentScan() {
        val barcode = _scannedBarcode.value ?: "MAN-SALE-${System.currentTimeMillis() % 1000000}"
        val priceVal = priceInput.value.toDoubleOrNull() ?: 0.0
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
    }

    // Complete sale (updates listing status to "Sold")
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

    // Call Gemini API to extract/analyze details from image
    fun analyzeImageWithGemini(bitmap: Bitmap) {
        _isAnalyzing.value = true
        _analysisError.value = null

        viewModelScope.launch {
            try {
                // Compress bitmap to base64
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
                val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                val prompt = """
                    You are an expert offroad power sports dealer inventory assistant. 
                    Analyze this image of an offroad vehicle (ATV, Dirt Bike, Side-by-Side UTV, Snowmobile, Jet Ski, etc.) and extract the vehicle specifications.
                    Return your answer strictly in JSON format matching this schema:
                    {
                      "make": "String (e.g., Polaris, Can-Am, Honda, Yamaha, Kawasaki, Suzuki, KTM, etc.)",
                      "model": "String (e.g., RZR, Maverick, Outlander, Raptor, Pioneer, CRF, etc.)",
                      "year": "String (e.g., 2024, 2023)",
                      "color": "String (main color of the body)",
                      "vehicleType": "String (must be exactly one of: ATV, Side-by-Side (UTV), Dirt Bike, Snowmobile, Jet Ski, Other)",
                      "vin": "String (a randomly generated realistic 17-character VIN if not visible)",
                      "price": Double (estimated retail price based on market value, e.g. 15000.00)",
                      "notes": "String (brief descriptive notes of condition, styling, and aftermarket parts if visible)"
                    }
                    Do not add any Markdown formatting or backticks around the JSON. Return only the raw JSON.
                """.trimIndent()

                val resultJson = GeminiClient.analyzeImage(base64Image, prompt)
                if (resultJson != null) {
                    // Try to parse JSON and update forms
                    try {
                        val cleanedJson = resultJson.trim()
                            .removePrefix("```json")
                            .removePrefix("```")
                            .removeSuffix("```")
                            .trim()

                        val jsonObj = JSONObject(cleanedJson)
                        makeInput.value = jsonObj.optString("make", "")
                        modelInput.value = jsonObj.optString("model", "")
                        yearInput.value = jsonObj.optString("year", "")
                        colorInput.value = jsonObj.optString("color", "")
                        vehicleTypeInput.value = jsonObj.optString("vehicleType", "ATV")
                        vinInput.value = jsonObj.optString("vin", "")
                        priceInput.value = jsonObj.optDouble("price", 0.0).toString()
                        notesInput.value = jsonObj.optString("notes", "")
                        _imageUriOrBase64.value = base64Image // Store the analyzed image as well!
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _analysisError.value = "Failed to parse AI response: ${e.localizedMessage}. Response was: $resultJson"
                    }
                } else {
                    _analysisError.value = "No response from Gemini API."
                }
            } catch (e: Exception) {
                _analysisError.value = e.localizedMessage ?: "Unknown error"
            } finally {
                _isAnalyzing.value = false
            }
        }
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
}
