package com.example.ui

import android.Manifest
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.BarcodeMapping
import com.example.data.InventoryListing
import com.example.data.InventoryViewModel
import com.example.data.CustomDocument
import com.example.data.DocumentZone
import com.example.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: InventoryViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Scanner, 1: Inventory Listing, 2: Database Mappings
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val saleListing by viewModel.saleListing.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CountryOrange),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCodeScanner,
                                contentDescription = "Logo",
                                tint = BoneWhite,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Country ATV InScan",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp,
                                    fontFamily = FontFamily.SansSerif
                                ),
                                color = BoneWhite
                            )
                            Text(
                                text = "DEALER INVENTORY v4.2",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = CountryAmber
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CinematicBlack,
                    titleContentColor = BoneWhite
                ),
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(CountryOrange.copy(alpha = 0.15f))
                            .border(1.dp, CountryOrange.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Moab Hub #4",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = CountryOrange
                            )
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CinematicCharcoalDark,
                tonalElevation = 12.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                val navBarColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CountryOrange,
                    selectedTextColor = BoneWhite,
                    indicatorColor = CinematicCharcoalLight,
                    unselectedIconColor = MutedBone,
                    unselectedTextColor = MutedBone
                )

                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Rounded.QrCodeScanner else Icons.Outlined.QrCodeScanner,
                            contentDescription = "Scanner"
                        )
                    },
                    label = { Text("Scanner") },
                    colors = navBarColors,
                    modifier = Modifier.testTag("tab_scanner")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.AutoMirrored.Filled.ListAlt else Icons.AutoMirrored.Filled.ListAlt,
                            contentDescription = "Inventory Listing"
                        )
                    },
                    label = { Text("Listing File") },
                    colors = navBarColors,
                    modifier = Modifier.testTag("tab_listings")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 2) Icons.Rounded.Dns else Icons.Outlined.Dns,
                            contentDescription = "Catalog Database"
                        )
                    },
                    label = { Text("Catalog DB") },
                    colors = navBarColors,
                    modifier = Modifier.testTag("tab_catalog")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 3) Icons.Rounded.Description else Icons.Outlined.Description,
                            contentDescription = "Document Templates Mapper"
                        )
                    },
                    label = { Text("Doc Mapper") },
                    colors = navBarColors,
                    modifier = Modifier.testTag("tab_doc_mapper")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(animationSpec = tween(350, easing = EaseInOutCubic)) { width -> width } + fadeIn(animationSpec = tween(350)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(350, easing = EaseInOutCubic)) { width -> -width } + fadeOut(animationSpec = tween(350)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(350, easing = EaseInOutCubic)) { width -> -width } + fadeIn(animationSpec = tween(350)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(350, easing = EaseInOutCubic)) { width -> width } + fadeOut(animationSpec = tween(350)))
                    }.using(
                        SizeTransform(clip = false)
                    )
                },
                label = "TabScreenTransition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> ScannerScreen(
                        viewModel = viewModel,
                        onNavigateToTab = { selectedTab = it },
                        showSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                    1 -> InventoryListingsScreen(
                        viewModel = viewModel,
                        showSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                    2 -> BarcodeMappingsScreen(viewModel = viewModel)
                    3 -> DocumentTemplatesMapperScreen(
                        viewModel = viewModel,
                        showSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                }
            }

            // Overlay sales preparation screen when active
            AnimatedVisibility(
                visible = saleListing != null,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(250))
            ) {
                saleListing?.let { listing ->
                    SalesPreparationScreen(
                        viewModel = viewModel,
                        listing = listing,
                        onClose = { viewModel.cancelSale() }, // leaving never changes the vehicle status
                        showSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: InventoryViewModel,
    onNavigateToTab: (Int) -> Unit,
    showSnackbar: (String) -> Unit
) {
    val scannedBarcode by viewModel.scannedBarcode.collectAsState()
    val loadedMapping by viewModel.loadedMapping.collectAsState()

    val make by viewModel.makeInput.collectAsState()
    val model by viewModel.modelInput.collectAsState()
    val year by viewModel.yearInput.collectAsState()
    val color by viewModel.colorInput.collectAsState()
    val vehicleType by viewModel.vehicleTypeInput.collectAsState()
    val vin by viewModel.vinInput.collectAsState()
    val price by viewModel.priceInput.collectAsState()
    val notes by viewModel.notesInput.collectAsState()
    val imageStr by viewModel.imageUriOrBase64.collectAsState()

    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analysisError by viewModel.analysisError.collectAsState()
    val formError by viewModel.formError.collectAsState()

    val isDecodingVin by viewModel.isDecodingVin.collectAsState()
    val vinDecodeError by viewModel.vinDecodeError.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cameraFailed by remember { mutableStateOf(false) }
    // Kept for the frame animation; no step sets it, because scans come only from the camera or typed input.
    val isScanningActive = false
    val simulationProgress = 0f

    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )


    // Standard camera permission
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Manual photo picker (Real Device fallback)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                viewModel.analyzeImageWithGemini(bitmap)
            } catch (e: Exception) {
                showSnackbar("Couldn't open that photo. Try a different one.")
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Current Scan / Interactive Simulator Header
        item {
            NeumorphicCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    Text(
                        text = "1. INCOMING SCAN GATEWAY",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = CountryOrange,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scan or select a barcode of the incoming vehicle to lookup/map details.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedBone
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (scannedBarcode == null) {
                        // Scan State: Active Camera simulation & Decoders
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black)
                                .clickable {
                                    if (!cameraPermissionState.status.isGranted) {
                                        cameraPermissionState.launchPermissionRequest()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (cameraPermissionState.status.isGranted) {
                                if (!cameraFailed) {
                                    CameraPreviewScanner(
                                        onBarcodeDetected = { barcode ->
                                            viewModel.onBarcodeScanned(barcode)
                                            showSnackbar("Barcode scanned: $barcode")
                                        },
                                        onFailure = {
                                            cameraFailed = true
                                            showSnackbar("The camera scanner couldn't start. Type the barcode in below instead.")
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = "The camera scanner couldn't start. Type the barcode in below instead.",
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(24.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Tap here to allow the camera so you can scan barcodes.",
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(24.dp)
                                )
                            }

                            // Tech scanner matrix grid background overlay
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val numColumns = 10
                                val numRows = 6
                                val colWidth = size.width / numColumns
                                val rowHeight = size.height / numRows
                                for (i in 1 until numColumns) {
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.08f),
                                        start = Offset(x = i * colWidth, y = 0f),
                                        end = Offset(x = i * colWidth, y = size.height),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                                for (j in 1 until numRows) {
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.08f),
                                        start = Offset(x = 0f, y = j * rowHeight),
                                        end = Offset(x = size.width, y = j * rowHeight),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                            }

                            // Corner frame & laser scanner simulation with BoxWithConstraints
                            BoxWithConstraints(
                                modifier = Modifier
                                    .size(width = 220.dp, height = 130.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .border(
                                        BorderStroke(
                                            2.dp,
                                            if (isScanningActive) Color(0xFF10B981).copy(alpha = glowAlpha) else Color.White.copy(alpha = 0.4f)
                                        ),
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                val width = maxWidth
                                val height = maxHeight
                                
                                // Draw high-tech corner bracket accents
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val bracketLength = 20.dp.toPx()
                                    val strokeWidthVal = 4.dp.toPx()
                                    val colorVal = if (isScanningActive) Color(0xFF10B981) else Color(0xFF4F46E5)
                                    
                                    // Top-Left corner
                                    drawPath(
                                        path = Path().apply {
                                            moveTo(0f, bracketLength)
                                            lineTo(0f, 0f)
                                            lineTo(bracketLength, 0f)
                                        },
                                        color = colorVal,
                                        style = Stroke(width = strokeWidthVal)
                                    )
                                    
                                    // Top-Right corner
                                    drawPath(
                                        path = Path().apply {
                                            moveTo(size.width - bracketLength, 0f)
                                            lineTo(size.width, 0f)
                                            lineTo(size.width, bracketLength)
                                        },
                                        color = colorVal,
                                        style = Stroke(width = strokeWidthVal)
                                    )
                                    
                                    // Bottom-Left corner
                                    drawPath(
                                        path = Path().apply {
                                            moveTo(0f, size.height - bracketLength)
                                            lineTo(0f, size.height)
                                            lineTo(bracketLength, size.height)
                                        },
                                        color = colorVal,
                                        style = Stroke(width = strokeWidthVal)
                                    )
                                    
                                    // Bottom-Right corner
                                    drawPath(
                                        path = Path().apply {
                                            moveTo(size.width - bracketLength, size.height)
                                            lineTo(size.width, size.height)
                                            lineTo(size.width, size.height - bracketLength)
                                        },
                                        color = colorVal,
                                        style = Stroke(width = strokeWidthVal)
                                    )
                                }

                                // Moving laser line bounded exactly to height constraints!
                                val laserY = height * laserOffset
                                Box(
                                    modifier = Modifier
                                        .absoluteOffset(y = laserY)
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = if (isScanningActive) {
                                                    listOf(Color(0xFF10B981).copy(alpha = 0.2f), Color(0xFF10B981), Color(0xFF10B981).copy(alpha = 0.2f))
                                                } else {
                                                    listOf(Color(0xFFFF3B30).copy(alpha = 0.2f), Color(0xFFFF3B30), Color(0xFFFF3B30).copy(alpha = 0.2f))
                                                }
                                            )
                                        )
                                        .shadow(
                                            elevation = 6.dp,
                                            shape = RoundedCornerShape(2.dp),
                                            ambientColor = if (isScanningActive) Color(0xFF10B981) else Color(0xFFFF3B30),
                                            spotColor = if (isScanningActive) Color(0xFF10B981) else Color(0xFFFF3B30)
                                        )
                                )
                            }

                            // Caption overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.QrCodeScanner,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isScanningActive) "DECODING... ACTIVE CAR SCAN" else "ALIGN BARCODE WITHIN FRAME",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                            }

                            // Dynamic active decoding HUD screen cover
                            if (isScanningActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.75f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            progress = { simulationProgress },
                                            color = Color(0xFF10B981),
                                            strokeWidth = 4.dp,
                                            modifier = Modifier.size(50.dp)
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = "DECODING BARCODE...",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.5.sp
                                            ),
                                            color = Color(0xFF10B981)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${(simulationProgress * 100).toInt()}% COMPLETED",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }

                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Custom Manual Barcode Entry Field
                        Spacer(modifier = Modifier.height(16.dp))
                        var customBarcodeText by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customBarcodeText,
                                onValueChange = { customBarcodeText = it },
                                placeholder = { Text("Type the barcode number") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("custom_barcode_field"),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                )
                            )
                            Button(
                                onClick = {
                                    if (customBarcodeText.isNotBlank()) {
                                        viewModel.onBarcodeScanned(customBarcodeText.trim())
                                        showSnackbar("Looking up barcode ${customBarcodeText.trim()}")
                                        customBarcodeText = ""
                                    }
                                },
                                modifier = Modifier.testTag("simulate_custom_scan_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Scan")
                            }
                        }

                        // Fallback manual entry button if scanning fails completely
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                val manualBarcode = "MAN-${System.currentTimeMillis() % 1000000}"
                                viewModel.onBarcodeScanned(manualBarcode)
                                showSnackbar("No barcode on this one. Opened a blank form with ID $manualBarcode.")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("barcode_fail_manual_btn"),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = "Scan Failure",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Barcode Failed to Scan? Enter Manually",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                    } else {
                        // Scan State: Active
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCodeScanner,
                                    contentDescription = "Barcode",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Scanned Barcode",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = scannedBarcode ?: "",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.clearScan() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear scan"
                                )
                            }
                        }
                    }
                }
            }
        }

        // Vehicle Specifications Mapping / Listing Setup
        scannedBarcode?.let { barcode ->
            item {
                NeumorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2. VEHICLE CATALOG SPECS",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = CountryOrange,
                                    letterSpacing = 1.sp
                                )
                            )

                            if (loadedMapping != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SmoothGreen.copy(alpha = 0.15f))
                                        .border(1.dp, SmoothGreen.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "AUTO-POPULATED",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SmoothGreen
                                        )
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SmoothRed.copy(alpha = 0.15f))
                                        .border(1.dp, SmoothRed.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "UNMAPPED BARCODE",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SmoothRed
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Gemini API Image Analysis Section (Analyze Images)
                        if (loadedMapping == null) {
                            Text(
                                text = "This barcode isn't in the catalog yet. Fill in the details below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedBone
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Trigger Analysis Button
                            NeumorphicButton(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_extract_button"),
                                accentColor = CountryOrange
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = "AI",
                                        tint = BoneWhite
                                    )
                                    Text("Fill from a photo (needs the shop server)", color = BoneWhite, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Loading State during Gemini Image understanding
                        if (isAnalyzing) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Gemini-3.1-Pro Engine analyzing vehicle...",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Thinking deeply with high reasoning to extract exact specs (Make, Model, Year, VIN, Class, Market Price)...",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }

                        analysisError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        // Display selected or generated Image preview if available
                        imageStr?.let { base64 ->
                            val imageBytes = Base64.decode(base64, Base64.DEFAULT)
                            val decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            decodedImage?.let { bmp ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)))
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Vehicle Photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Black.copy(alpha = 0.6f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "CATALOG PHOTO",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        // Spec Input Fields
                        OutlinedTextField(
                            value = make,
                            onValueChange = { viewModel.makeInput.value = it },
                            label = { Text("Make (e.g. Polaris, Can-Am, Honda)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("make_field"),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = model,
                            onValueChange = { viewModel.modelInput.value = it },
                            label = { Text("Model (e.g. RZR XP 1000, Maverick)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("model_field"),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = year,
                                onValueChange = { viewModel.yearInput.value = it },
                                label = { Text("Year") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("year_field"),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            OutlinedTextField(
                                value = color,
                                onValueChange = { viewModel.colorInput.value = it },
                                label = { Text("Color") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("color_field"),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Vehicle Type Dropdown / Row Selector
                        Text(text = "Vehicle Type:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("ATV", "Side-by-Side (UTV)", "Dirt Bike", "Snowmobile", "Jet Ski", "Other").forEach { type ->
                                FilterChip(
                                    selected = vehicleType == type,
                                    onClick = { viewModel.vehicleTypeInput.value = type },
                                    label = { Text(type) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = vin,
                                onValueChange = { viewModel.vinInput.value = it },
                                label = { Text("VIN / Serial (17 digits)") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("vin_field"),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            
                            Button(
                                onClick = {
                                    if (vin.isNotBlank()) {
                                        viewModel.decodeVinWithNhtsa(vin)
                                    } else {
                                        showSnackbar("Please enter a VIN to decode first!")
                                    }
                                },
                                enabled = !isDecodingVin,
                                modifier = Modifier
                                    .testTag("decode_vin_button")
                                    .height(56.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                if (isDecodingVin) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = "Decode",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text("Decode", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                        }

                        if (vinDecodeError != null) {
                            Text(
                                text = vinDecodeError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = price,
                            onValueChange = { viewModel.priceInput.value = it },
                            label = { Text("MSRP / Dealership Retail Price ($)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("price_field"),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { viewModel.notesInput.value = it },
                            label = { Text("Condition / Upgrade Notes") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .testTag("notes_field"),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        formError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .testTag("form_error")
                            )
                        }

                        // Draft Sale Action Button (Direct from scan screen)
                        Button(
                            onClick = {
                                if (make.isBlank() || model.isBlank() || vin.isBlank()) {
                                    showSnackbar("Please fill out Make, Model, and VIN before drafting sale!")
                                } else {
                                    if (viewModel.prepareSaleFromCurrentScan()) {
                                        showSnackbar("Drafting sale for $year $make $model.")
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("scanner_draft_sale_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PointOfSale,
                                    contentDescription = "Draft Sale",
                                    tint = Color.White
                                )
                                Text(
                                    text = "Draft Sale (Pre-populate Contracts)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (viewModel.saveBarcodeMapping()) showSnackbar("Saved to the catalog.")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_mapping_only_btn"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Save Specs Only")
                            }

                            Button(
                                onClick = {
                                    if (viewModel.addCurrentToInventoryListing()) {
                                        showSnackbar("Added to the inventory list.")
                                        onNavigateToTab(1) // Navigate to Listing File
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("log_incoming_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add")
                                    Text("Log to Listing File", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryListingsScreen(
    viewModel: InventoryViewModel,
    showSnackbar: (String) -> Unit
) {
    val listings by viewModel.allListings.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var activeExportListing by remember { mutableStateOf<InventoryListing?>(null) }
    val selectedListingIds = remember { mutableStateMapOf<Int, Boolean>() }
    var activeBulkExport by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf("All") } // All, In Stock, Sold

    var showFilterPanel by remember { mutableStateOf(false) }
    var selectedMake by remember { mutableStateOf("All") }
    var selectedYear by remember { mutableStateOf("All") }
    var selectedColor by remember { mutableStateOf("All") }
    var selectedType by remember { mutableStateOf("All") }
    var sortBy by remember { mutableStateOf("Newest Added") } // Newest Added, Year (Newest), Year (Oldest), Price (Highest), Price (Lowest), Make (A-Z)

    val uniqueMakes = remember(listings) { listOf("All") + listings.map { it.make }.distinct().filter { it.isNotBlank() } }
    val uniqueYears = remember(listings) { listOf("All") + listings.map { it.year }.distinct().filter { it.isNotBlank() }.sortedDescending() }
    val uniqueColors = remember(listings) { listOf("All") + listings.map { it.color }.distinct().filter { it.isNotBlank() } }
    val uniqueTypes = remember(listings) { listOf("All") + listings.map { it.vehicleType }.distinct().filter { it.isNotBlank() } }

    val format = NumberFormat.getCurrencyInstance(Locale.US)

    val filteredListings = listings.filter { listing ->
        val matchesSearch = listing.make.contains(searchQuery, ignoreCase = true) ||
                listing.model.contains(searchQuery, ignoreCase = true) ||
                listing.barcode.contains(searchQuery, ignoreCase = true) ||
                listing.vin.contains(searchQuery, ignoreCase = true) ||
                listing.color.contains(searchQuery, ignoreCase = true) ||
                listing.notes.contains(searchQuery, ignoreCase = true)

        val matchesStatus = statusFilter == "All" || listing.status.equals(statusFilter, ignoreCase = true)
        val matchesMake = selectedMake == "All" || listing.make.equals(selectedMake, ignoreCase = true)
        val matchesYear = selectedYear == "All" || listing.year.equals(selectedYear, ignoreCase = true)
        val matchesColor = selectedColor == "All" || listing.color.equals(selectedColor, ignoreCase = true)
        val matchesType = selectedType == "All" || listing.vehicleType.equals(selectedType, ignoreCase = true)

        matchesSearch && matchesStatus && matchesMake && matchesYear && matchesColor && matchesType
    }.sortedWith { a, b ->
        when (sortBy) {
            "Year (Newest)" -> b.year.compareTo(a.year)
            "Year (Oldest)" -> a.year.compareTo(b.year)
            "Price (Highest)" -> b.price.compareTo(a.price)
            "Price (Lowest)" -> a.price.compareTo(b.price)
            "Make (A-Z)" -> a.make.compareTo(b.make, ignoreCase = true)
            else -> b.id.compareTo(a.id) // Newest Added (id descending)
        }
    }

    val totalValue = filteredListings.sumOf { it.price }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Stats Summary Bar
        NeumorphicCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TOTAL LISTING FILE VALUE",
                        style = MaterialTheme.typography.labelSmall,
                        color = CountryAmber,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = format.format(totalValue),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                        color = BoneWhite
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CountryOrange.copy(alpha = 0.15f))
                        .border(1.dp, CountryOrange.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${filteredListings.size} Items",
                        fontWeight = FontWeight.Bold,
                        color = CountryOrange
                    )
                }
            }
        }

        val selectedCount = filteredListings.count { selectedListingIds[it.id] == true }
        if (selectedCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            NeumorphicCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bulk_selection_bar"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "$selectedCount VEHICLES SELECTED",
                            style = MaterialTheme.typography.labelSmall,
                            color = CountryAmber,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ready for bulk document packaging",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedBone
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                selectedListingIds.clear()
                            }
                        ) {
                            Text("Clear", color = SmoothRed, fontWeight = FontWeight.Bold)
                        }

                        NeumorphicButton(
                            onClick = { activeBulkExport = true },
                            modifier = Modifier.testTag("bulk_export_btn"),
                            accentColor = CountryOrange
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(16.dp), tint = BoneWhite)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bulk Docs", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BoneWhite)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search bar & expand filters trigger
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search make, model, barcode, VIN...", color = MutedBone) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = CountryOrange) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = MutedBone)
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("inventory_search_field"),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = BoneWhite,
                    unfocusedTextColor = BoneWhite,
                    focusedContainerColor = CinematicCharcoalDark,
                    unfocusedContainerColor = CinematicCharcoalDark,
                    focusedBorderColor = CountryOrange,
                    unfocusedBorderColor = CinematicCharcoalLight
                )
            )

            // Filter Expand Button
            Box {
                val filterActive = selectedMake != "All" || selectedYear != "All" || selectedColor != "All" || selectedType != "All" || sortBy != "Newest Added"
                NeumorphicIconButton(
                    onClick = { showFilterPanel = !showFilterPanel },
                    modifier = Modifier.testTag("toggle_filters_btn"),
                    containerColor = if (filterActive) CountryOrange else CinematicCharcoalDark
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Toggle Advanced Filter Panel",
                        tint = if (filterActive) BoneWhite else MutedBone
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Collapsible Advanced Filtering & Sorting Options Panel
        AnimatedVisibility(
            visible = showFilterPanel,
            enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(animationSpec = tween(200))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .testTag("advanced_filter_panel"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ADVANCED FILTERS & SORTING",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Make Filter Selection
                    Column {
                        Text("Make:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uniqueMakes.forEach { makeItem ->
                                FilterChip(
                                    selected = selectedMake == makeItem,
                                    onClick = { selectedMake = makeItem },
                                    label = { Text(makeItem) },
                                    modifier = Modifier.testTag("make_filter_$makeItem")
                                )
                            }
                        }
                    }

                    // Year Filter Selection
                    Column {
                        Text("Year:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uniqueYears.forEach { yearItem ->
                                FilterChip(
                                    selected = selectedYear == yearItem,
                                    onClick = { selectedYear = yearItem },
                                    label = { Text(yearItem) },
                                    modifier = Modifier.testTag("year_filter_$yearItem")
                                )
                            }
                        }
                    }

                    // Color Filter Selection
                    Column {
                        Text("Body Color:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uniqueColors.forEach { colorItem ->
                                FilterChip(
                                    selected = selectedColor == colorItem,
                                    onClick = { selectedColor = colorItem },
                                    label = { Text(colorItem) },
                                    modifier = Modifier.testTag("color_filter_$colorItem")
                                )
                            }
                        }
                    }

                    // Sort By Selection
                    Column {
                        Text("Sort Listings By:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Newest Added", "Year (Newest)", "Year (Oldest)", "Price (Highest)", "Price (Lowest)", "Make (A-Z)").forEach { sortItem ->
                                FilterChip(
                                    selected = sortBy == sortItem,
                                    onClick = { sortBy = sortItem },
                                    label = { Text(sortItem) },
                                    modifier = Modifier.testTag("sort_by_$sortItem")
                                )
                            }
                        }
                    }

                    // Clear Filters Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                selectedMake = "All"
                                selectedYear = "All"
                                selectedColor = "All"
                                selectedType = "All"
                                sortBy = "Newest Added"
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Filters")
                        }
                    }
                }
            }
        }

        // Basic Quick Status Filters (In Stock vs Sold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "In Stock", "Sold").forEach { filter ->
                FilterChip(
                    selected = statusFilter == filter,
                    onClick = { statusFilter = filter },
                    label = { Text(filter) },
                    modifier = Modifier.testTag("filter_chip_$filter")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Select All / Selection status row
        if (filteredListings.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val allSelected = filteredListings.all { selectedListingIds[it.id] == true }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            val newChecked = !allSelected
                            filteredListings.forEach { selectedListingIds[it.id] = newChecked }
                        }
                        .testTag("select_all_container")
                ) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            filteredListings.forEach { selectedListingIds[it.id] = checked }
                        },
                        modifier = Modifier.testTag("select_all_checkbox")
                    )
                    Text(
                        text = if (allSelected) "Deselect All Filtered" else "Select All Filtered",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Text(
                    text = "${filteredListings.size} shown",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Listing Table / List
        if (filteredListings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Inventory,
                        contentDescription = "Empty",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No listings matching search.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredListings, key = { it.id }) { listing ->
                    ListingItemCard(
                        listing = listing,
                        format = format,
                        onDelete = {
                            viewModel.deleteListing(listing.id)
                            showSnackbar("Deleted listing ${listing.make} ${listing.model} from files.")
                        },
                        onPrepareSale = {
                            viewModel.prepareSale(listing)
                        },
                        onExportDocs = {
                            activeExportListing = listing
                        },
                        isSelected = selectedListingIds[listing.id] == true,
                        onSelectionChanged = { checked ->
                            selectedListingIds[listing.id] = checked
                        }
                    )
                }
            }
        }

        // Action Center Modal overlay for direct export from Listings view
        activeExportListing?.let { listing ->
            var docTab by remember { mutableStateOf(0) }
            var buyerName by remember { mutableStateOf("") }
            var buyerPhone by remember { mutableStateOf("") }
            var buyerAddress by remember { mutableStateOf("") }
            var buyerEmail by remember { mutableStateOf("") }
            var saleDate by remember { mutableStateOf("03/15/2026") }

            var downPayment by remember { mutableStateOf("1,500") }
            var interestRate by remember { mutableStateOf("6.5") }
            var loanTerm by remember { mutableStateOf("48") }

            val sigPaths = remember { mutableStateListOf<Path>() }
            var currentSigPath by remember { mutableStateOf<Path?>(null) }

            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            var showLocalEmailDialog by remember { mutableStateOf(false) }
            var localEmailInput by remember { mutableStateOf("") }
            var isLocalSendingEmail by remember { mutableStateOf(false) }

            var showLocalPrintDialog by remember { mutableStateOf(false) }
            var localSelectedPrinter by remember { mutableStateOf("Office Jet 5000 (Sales Room)") }
            var isLocalPrintingJob by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { activeExportListing = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .testTag("listings_export_dialog"),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("CONTRACT DOCUMENT ACTIONS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = { activeExportListing = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Generate, pre-populate, sign, email, or print legal documents directly for: ${listing.year} ${listing.make} ${listing.model}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )

                        // Document Select Tabs
                        TabRow(
                            selectedTabIndex = docTab,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Tab(selected = docTab == 0, onClick = { docTab = 0 }, text = { Text("Bill of Sale", fontSize = 11.sp, fontWeight = FontWeight.Bold) }, modifier = Modifier.testTag("tab_bos"))
                            Tab(selected = docTab == 1, onClick = { docTab = 1 }, text = { Text("Finance Agreement", fontSize = 11.sp, fontWeight = FontWeight.Bold) }, modifier = Modifier.testTag("tab_finance"))
                            Tab(selected = docTab == 2, onClick = { docTab = 2 }, text = { Text("DMV Registration", fontSize = 11.sp, fontWeight = FontWeight.Bold) }, modifier = Modifier.testTag("tab_dmv"))
                        }

                        // Buyer fields input
                        Text("1. PRE-POPULATE BUYER DETAILS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = buyerName,
                            onValueChange = { buyerName = it },
                            label = { Text("Buyer Name") },
                            modifier = Modifier.fillMaxWidth().testTag("buyer_name_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        if (docTab == 0 || docTab == 2) {
                            OutlinedTextField(
                                value = buyerPhone,
                                onValueChange = { buyerPhone = it },
                                label = { Text("Buyer Phone") },
                                modifier = Modifier.fillMaxWidth().testTag("buyer_phone_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            OutlinedTextField(
                                value = buyerAddress,
                                onValueChange = { buyerAddress = it },
                                label = { Text("Buyer Address") },
                                modifier = Modifier.fillMaxWidth().testTag("buyer_address_input"),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        if (docTab == 1) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = downPayment,
                                    onValueChange = { downPayment = it },
                                    label = { Text("Down Payment ($)") },
                                    modifier = Modifier.weight(1f).testTag("down_payment_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                OutlinedTextField(
                                    value = interestRate,
                                    onValueChange = { interestRate = it },
                                    label = { Text("Interest Rate (%)") },
                                    modifier = Modifier.weight(1f).testTag("rate_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        // Display pre-populated Document Template Preview
                        Text("2. LIVE DOCUMENT PREVIEW", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                when (docTab) {
                                    0 -> BillOfSaleDoc(listing, buyerName.ifBlank { "N/A" }, buyerPhone.ifBlank { "N/A" }, buyerAddress.ifBlank { "N/A" }, saleDate, format)
                                    1 -> FinanceDoc(listing, buyerName.ifBlank { "N/A" }, downPayment, interestRate, loanTerm, format)
                                    2 -> DmvDoc(listing, buyerName.ifBlank { "N/A" }, buyerAddress.ifBlank { "N/A" }, saleDate)
                                }
                            }
                        }

                        // Interactive signature pad
                        Text("3. INTERACTIVE BUYER SIGNATURE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(8.dp))
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val p = Path().apply { moveTo(offset.x, offset.y) }
                                                currentSigPath = p
                                                sigPaths.add(p)
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                currentSigPath?.lineTo(change.position.x, change.position.y)
                                                // Trigger recomposition
                                                val last = sigPaths.lastOrNull()
                                                if (last != null) {
                                                    sigPaths[sigPaths.size - 1] = Path().apply {
                                                        addPath(last)
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                currentSigPath = null
                                            }
                                        )
                                    }
                            ) {
                                sigPaths.forEach { path ->
                                    drawPath(
                                        path = path,
                                        color = Color.Black,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = 3.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }

                            if (sigPaths.isEmpty()) {
                                Text(
                                    text = "Authorized Signatory Field",
                                    modifier = Modifier.align(Alignment.Center),
                                    color = Color.Gray.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                IconButton(
                                    onClick = { sigPaths.clear() },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Clear Signature", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Dialog Actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val pdfFile = when (docTab) {
                                        0 -> com.example.utils.DocumentPdfExporter.generateBillOfSalePdf(
                                            context = context,
                                            listing = listing,
                                            buyerName = buyerName.ifBlank { "N/A" },
                                            buyerPhone = buyerPhone.ifBlank { "N/A" },
                                            buyerAddress = buyerAddress.ifBlank { "N/A" },
                                            saleDate = saleDate,
                                            signaturePaths = sigPaths.map { it.asAndroidPath() }
                                        )
                                        1 -> com.example.utils.DocumentPdfExporter.generateFinanceAgreementPdf(
                                            context = context,
                                            listing = listing,
                                            buyerName = buyerName.ifBlank { "N/A" },
                                            downPaymentStr = downPayment,
                                            rateStr = interestRate,
                                            termStr = loanTerm,
                                            signaturePaths = sigPaths.map { it.asAndroidPath() }
                                        )
                                        2 -> com.example.utils.DocumentPdfExporter.generateDmvRegistrationPdf(
                                            context = context,
                                            listing = listing,
                                            buyerName = buyerName.ifBlank { "N/A" },
                                            buyerAddress = buyerAddress.ifBlank { "N/A" },
                                            saleDate = saleDate,
                                            signaturePaths = sigPaths.map { it.asAndroidPath() }
                                        )
                                        else -> null
                                    }
                                    if (pdfFile != null) {
                                        showSnackbar("Successfully exported contract to: ${pdfFile.name}")
                                    } else {
                                        showSnackbar("Error generating document PDF")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.1f)
                                    .testTag("modal_download_pdf_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = "Download", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    showLocalEmailDialog = true
                                },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("modal_email_pdf_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(Icons.Rounded.Email, contentDescription = "Email", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Email", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    showLocalPrintDialog = true
                                },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("modal_print_pdf_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(Icons.Rounded.Print, contentDescription = "Print", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Print", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeExportListing = null }) {
                        Text("Done")
                    }
                }
            )

            // Email dialog inside modal
            if (showLocalEmailDialog) {
                val finalEmail = if (localEmailInput.isBlank()) buyerEmail.ifBlank { "johndoe@example.com" } else localEmailInput
                AlertDialog(
                    onDismissRequest = { if (!isLocalSendingEmail) showLocalEmailDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Email Contract PDF", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Send generated PDF to recipient's inbox.", style = MaterialTheme.typography.bodyMedium)
                            
                            OutlinedTextField(
                                value = finalEmail,
                                onValueChange = { localEmailInput = it },
                                label = { Text("Recipient Email") },
                                modifier = Modifier.fillMaxWidth().testTag("modal_email_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )

                            if (isLocalSendingEmail) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    Text("Encrypting attachment & sending...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                isLocalSendingEmail = true
                                scope.launch {
                                    kotlinx.coroutines.delay(1800)
                                    isLocalSendingEmail = false
                                    showLocalEmailDialog = false
                                    
                                    val pdfFile = when (docTab) {
                                        0 -> com.example.utils.DocumentPdfExporter.generateBillOfSalePdf(
                                            context = context,
                                            listing = listing,
                                            buyerName = buyerName.ifBlank { "N/A" },
                                            buyerPhone = buyerPhone.ifBlank { "N/A" },
                                            buyerAddress = buyerAddress.ifBlank { "N/A" },
                                            saleDate = saleDate,
                                            signaturePaths = sigPaths.map { it.asAndroidPath() }
                                        )
                                        1 -> com.example.utils.DocumentPdfExporter.generateFinanceAgreementPdf(
                                            context = context,
                                            listing = listing,
                                            buyerName = buyerName.ifBlank { "N/A" },
                                            downPaymentStr = downPayment,
                                            rateStr = interestRate,
                                            termStr = loanTerm,
                                            signaturePaths = sigPaths.map { it.asAndroidPath() }
                                        )
                                        2 -> com.example.utils.DocumentPdfExporter.generateDmvRegistrationPdf(
                                            context = context,
                                            listing = listing,
                                            buyerName = buyerName.ifBlank { "N/A" },
                                            buyerAddress = buyerAddress.ifBlank { "N/A" },
                                            saleDate = saleDate,
                                            signaturePaths = sigPaths.map { it.asAndroidPath() }
                                        )
                                        else -> null
                                    }
                                    if (pdfFile != null) {
                                        com.example.utils.DocumentPdfExporter.sharePdfFile(context, pdfFile, finalEmail)
                                        showSnackbar("Sent contract documents to: $finalEmail")
                                    } else {
                                        showSnackbar("Failed to generate PDF")
                                    }
                                }
                            },
                            enabled = !isLocalSendingEmail && finalEmail.isNotBlank(),
                            modifier = Modifier.testTag("modal_email_confirm_btn")
                        ) {
                            Text("Send & Share")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLocalEmailDialog = false }, enabled = !isLocalSendingEmail) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Print dialog inside modal
            if (showLocalPrintDialog) {
                var copies by remember { mutableStateOf(1) }
                AlertDialog(
                    onDismissRequest = { if (!isLocalPrintingJob) showLocalPrintDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Print Dealership Copy", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Select Local Printer:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .clickable {
                                        localSelectedPrinter = if (localSelectedPrinter.contains("Office Jet")) "Canon Pro (Front Desk)" else "Office Jet 5000 (Sales Room)"
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.Print, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Text(localSelectedPrinter, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Change printer")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Number of Copies:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    IconButton(onClick = { if (copies > 1) copies-- }, colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                    }
                                    Text(copies.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    IconButton(onClick = { copies++ }, colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            if (isLocalPrintingJob) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    Text("Connecting & spooling to local subnet...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                isLocalPrintingJob = true
                                scope.launch {
                                    kotlinx.coroutines.delay(1800)
                                    isLocalPrintingJob = false
                                    showLocalPrintDialog = false
                                    
                                    val pdfFile = when (docTab) {
                                        0 -> com.example.utils.DocumentPdfExporter.generateBillOfSalePdf(
                                            context = context,
                                            listing = listing,
                                            buyerName = buyerName.ifBlank { "N/A" },
                                            buyerPhone = buyerPhone.ifBlank { "N/A" },
                                            buyerAddress = buyerAddress.ifBlank { "N/A" },
                                            saleDate = saleDate,
                                            signaturePaths = sigPaths.map { it.asAndroidPath() }
                                        )
                                        1 -> com.example.utils.DocumentPdfExporter.generateFinanceAgreementPdf(
                                            context = context,
                                            listing = listing,
                                            buyerName = buyerName.ifBlank { "N/A" },
                                            downPaymentStr = downPayment,
                                            rateStr = interestRate,
                                            termStr = loanTerm,
                                            signaturePaths = sigPaths.map { it.asAndroidPath() }
                                        )
                                        2 -> com.example.utils.DocumentPdfExporter.generateDmvRegistrationPdf(
                                            context = context,
                                            listing = listing,
                                            buyerName = buyerName.ifBlank { "N/A" },
                                            buyerAddress = buyerAddress.ifBlank { "N/A" },
                                            saleDate = saleDate,
                                            signaturePaths = sigPaths.map { it.asAndroidPath() }
                                        )
                                        else -> null
                                    }
                                    if (pdfFile != null) {
                                        com.example.utils.DocumentPdfExporter.printPdf(context, pdfFile)
                                        showSnackbar("Successfully spooled job to $localSelectedPrinter ($copies copies)")
                                    } else {
                                        showSnackbar("Failed to generate PDF")
                                    }
                                }
                            },
                            enabled = !isLocalPrintingJob,
                            modifier = Modifier.testTag("modal_print_confirm_btn")
                        ) {
                            Text("Print Job")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLocalPrintDialog = false }, enabled = !isLocalPrintingJob) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        // Bulk Export Action Center Dialog
        if (activeBulkExport) {
            val selectedListings = remember(listings, selectedListingIds) {
                listings.filter { selectedListingIds[it.id] == true }
            }

            var buyerName by remember { mutableStateOf("") }
            var buyerPhone by remember { mutableStateOf("") }
            var buyerAddress by remember { mutableStateOf("") }
            var buyerEmail by remember { mutableStateOf("") }
            var saleDate by remember { mutableStateOf("03/15/2026") }

            val sigPaths = remember { mutableStateListOf<Path>() }
            var currentSigPath by remember { mutableStateOf<Path?>(null) }

            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            var showLocalEmailDialog by remember { mutableStateOf(false) }
            var localEmailInput by remember { mutableStateOf("") }
            var isLocalSendingEmail by remember { mutableStateOf(false) }

            var showLocalPrintDialog by remember { mutableStateOf(false) }
            var localSelectedPrinter by remember { mutableStateOf("Office Jet 5000 (Sales Room)") }
            var isLocalPrintingJob by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { activeBulkExport = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .testTag("bulk_export_dialog"),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Description, contentDescription = null, tint = CountryOrange)
                            Text("BULK CONTRACT COMPLIANCE GENERATOR", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = BoneWhite)
                        }
                        IconButton(onClick = { activeBulkExport = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MutedBone)
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Pre-populate, sign, email, or print legal sales and DMV registration transfer documents for all ${selectedListings.size} selected vehicles simultaneously.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedBone
                        )

                        // Visual list of selected vehicles
                        Text("SELECTED VEHICLES (${selectedListings.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = BoneWhite)
                        NeumorphicCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                selectedListings.forEach { vehicle ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${vehicle.year} ${vehicle.make} ${vehicle.model}",
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = BoneWhite
                                        )
                                        Text(
                                            text = format.format(vehicle.price),
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = CountryAmber,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Buyer fields input
                        Text("1. BULK BUYER DETAILS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = BoneWhite)
                        OutlinedTextField(
                            value = buyerName,
                            onValueChange = { buyerName = it },
                            label = { Text("Buyer Name") },
                            modifier = Modifier.fillMaxWidth().testTag("bulk_buyer_name_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = BoneWhite,
                                unfocusedTextColor = BoneWhite,
                                focusedContainerColor = CinematicCharcoalDark,
                                unfocusedContainerColor = CinematicCharcoalDark,
                                focusedBorderColor = CountryOrange,
                                unfocusedBorderColor = CinematicCharcoalLight,
                                focusedLabelColor = CountryOrange,
                                unfocusedLabelColor = MutedBone
                            )
                        )

                        OutlinedTextField(
                            value = buyerPhone,
                            onValueChange = { buyerPhone = it },
                            label = { Text("Buyer Phone") },
                            modifier = Modifier.fillMaxWidth().testTag("bulk_buyer_phone_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = BoneWhite,
                                unfocusedTextColor = BoneWhite,
                                focusedContainerColor = CinematicCharcoalDark,
                                unfocusedContainerColor = CinematicCharcoalDark,
                                focusedBorderColor = CountryOrange,
                                unfocusedBorderColor = CinematicCharcoalLight,
                                focusedLabelColor = CountryOrange,
                                unfocusedLabelColor = MutedBone
                            )
                        )

                        OutlinedTextField(
                            value = buyerAddress,
                            onValueChange = { buyerAddress = it },
                            label = { Text("Buyer Address") },
                            modifier = Modifier.fillMaxWidth().testTag("bulk_buyer_address_input"),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = BoneWhite,
                                unfocusedTextColor = BoneWhite,
                                focusedContainerColor = CinematicCharcoalDark,
                                unfocusedContainerColor = CinematicCharcoalDark,
                                focusedBorderColor = CountryOrange,
                                unfocusedBorderColor = CinematicCharcoalLight,
                                focusedLabelColor = CountryOrange,
                                unfocusedLabelColor = MutedBone
                            )
                        )

                        // Interactive signature pad
                        Text("2. INTERACTIVE BUYER SIGNATURE (Applies to all contracts)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = BoneWhite)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CinematicCharcoalDark)
                                .border(BorderStroke(1.5.dp, CountryOrange), RoundedCornerShape(8.dp))
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val p = Path().apply { moveTo(offset.x, offset.y) }
                                                currentSigPath = p
                                                sigPaths.add(p)
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                currentSigPath?.lineTo(change.position.x, change.position.y)
                                                // Trigger recomposition
                                                val last = sigPaths.lastOrNull()
                                                if (last != null) {
                                                    sigPaths[sigPaths.size - 1] = Path().apply {
                                                        addPath(last)
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                currentSigPath = null
                                            }
                                        )
                                    }
                            ) {
                                sigPaths.forEach { path ->
                                    drawPath(
                                        path = path,
                                        color = BoneWhite,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = 3.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }

                            if (sigPaths.isEmpty()) {
                                Text(
                                    text = "Authorized Signatory Field",
                                    modifier = Modifier.align(Alignment.Center),
                                    color = MutedBone.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                IconButton(
                                    onClick = { sigPaths.clear() },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Clear Signature", tint = SmoothRed)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Dialog Actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NeumorphicButton(
                                onClick = {
                                    val pdfFile = com.example.utils.DocumentPdfExporter.generateBulkPdf(
                                        context = context,
                                        listings = selectedListings,
                                        buyerName = buyerName.ifBlank { "N/A" },
                                        buyerPhone = buyerPhone.ifBlank { "N/A" },
                                        buyerAddress = buyerAddress.ifBlank { "N/A" },
                                        saleDate = saleDate,
                                        signaturePaths = sigPaths.map { it.asAndroidPath() }
                                    )
                                    if (pdfFile != null) {
                                        showSnackbar("Successfully exported bulk contract to: ${pdfFile.name}")
                                    } else {
                                        showSnackbar("Error generating bulk PDF")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.1f)
                                    .testTag("bulk_download_pdf_btn"),
                                accentColor = CountryAmber
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = "Download", modifier = Modifier.size(16.dp), tint = BoneWhite)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BoneWhite)
                            }

                            NeumorphicButton(
                                onClick = {
                                    showLocalEmailDialog = true
                                },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("bulk_email_pdf_btn"),
                                accentColor = CinematicCharcoalLight
                            ) {
                                Icon(Icons.Rounded.Email, contentDescription = "Email", modifier = Modifier.size(16.dp), tint = BoneWhite)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Email", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BoneWhite)
                            }

                            NeumorphicButton(
                                onClick = {
                                    showLocalPrintDialog = true
                                },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("bulk_print_pdf_btn"),
                                accentColor = CountryOrange
                            ) {
                                Icon(Icons.Rounded.Print, contentDescription = "Print", modifier = Modifier.size(16.dp), tint = BoneWhite)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Print", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BoneWhite)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeBulkExport = false }) {
                        Text("Done", color = CountryOrange, fontWeight = FontWeight.Bold)
                    }
                }
            )

            // Email dialog inside modal
            if (showLocalEmailDialog) {
                val finalEmail = if (localEmailInput.isBlank()) buyerEmail.ifBlank { "johndoe@example.com" } else localEmailInput
                AlertDialog(
                    onDismissRequest = { if (!isLocalSendingEmail) showLocalEmailDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Email Bulk Contract PDF", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Send generated bulk PDF to recipient's inbox.", style = MaterialTheme.typography.bodyMedium)
                            
                            OutlinedTextField(
                                value = finalEmail,
                                onValueChange = { localEmailInput = it },
                                label = { Text("Recipient Email") },
                                modifier = Modifier.fillMaxWidth().testTag("bulk_modal_email_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )

                            if (isLocalSendingEmail) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    Text("Encrypting attachment & sending...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                isLocalSendingEmail = true
                                scope.launch {
                                    kotlinx.coroutines.delay(1800)
                                    isLocalSendingEmail = false
                                    showLocalEmailDialog = false
                                    
                                    val pdfFile = com.example.utils.DocumentPdfExporter.generateBulkPdf(
                                        context = context,
                                        listings = selectedListings,
                                        buyerName = buyerName.ifBlank { "N/A" },
                                        buyerPhone = buyerPhone.ifBlank { "N/A" },
                                        buyerAddress = buyerAddress.ifBlank { "N/A" },
                                        saleDate = saleDate,
                                        signaturePaths = sigPaths.map { it.asAndroidPath() }
                                    )
                                    if (pdfFile != null) {
                                        com.example.utils.DocumentPdfExporter.sharePdfFile(context, pdfFile, finalEmail)
                                        showSnackbar("Sent bulk contract documents to: $finalEmail")
                                    } else {
                                        showSnackbar("Failed to generate bulk PDF")
                                    }
                                }
                            },
                            enabled = !isLocalSendingEmail && finalEmail.isNotBlank(),
                            modifier = Modifier.testTag("bulk_modal_email_confirm_btn")
                        ) {
                            Text("Send & Share")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLocalEmailDialog = false }, enabled = !isLocalSendingEmail) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Print dialog inside modal
            if (showLocalPrintDialog) {
                var copies by remember { mutableStateOf(1) }
                AlertDialog(
                    onDismissRequest = { if (!isLocalPrintingJob) showLocalPrintDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Print Bulk Dealership Copies", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Select Local Printer:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .clickable {
                                        localSelectedPrinter = if (localSelectedPrinter.contains("Office Jet")) "Canon Pro (Front Desk)" else "Office Jet 5000 (Sales Room)"
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.Print, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Text(localSelectedPrinter, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Change printer")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Number of Copies:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    IconButton(onClick = { if (copies > 1) copies-- }, colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                    }
                                    Text(copies.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    IconButton(onClick = { copies++ }, colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            if (isLocalPrintingJob) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    Text("Connecting & spooling to local subnet...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                isLocalPrintingJob = true
                                scope.launch {
                                    kotlinx.coroutines.delay(1800)
                                    isLocalPrintingJob = false
                                    showLocalPrintDialog = false
                                    
                                    val pdfFile = com.example.utils.DocumentPdfExporter.generateBulkPdf(
                                        context = context,
                                        listings = selectedListings,
                                        buyerName = buyerName.ifBlank { "N/A" },
                                        buyerPhone = buyerPhone.ifBlank { "N/A" },
                                        buyerAddress = buyerAddress.ifBlank { "N/A" },
                                        saleDate = saleDate,
                                        signaturePaths = sigPaths.map { it.asAndroidPath() }
                                    )
                                    if (pdfFile != null) {
                                        com.example.utils.DocumentPdfExporter.printPdf(context, pdfFile)
                                        showSnackbar("Successfully spooled bulk job to $localSelectedPrinter ($copies copies)")
                                    } else {
                                        showSnackbar("Failed to generate bulk PDF")
                                    }
                                }
                            },
                            enabled = !isLocalPrintingJob,
                            modifier = Modifier.testTag("bulk_print_confirm_btn")
                        ) {
                            Text("Print Job")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLocalPrintDialog = false }, enabled = !isLocalPrintingJob) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ListingItemCard(
    listing: InventoryListing,
    format: NumberFormat,
    onDelete: () -> Unit,
    onPrepareSale: () -> Unit,
    onExportDocs: () -> Unit,
    isSelected: Boolean = false,
    onSelectionChanged: (Boolean) -> Unit = {}
) {
    NeumorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("listing_card_${listing.id}")
    ) {
        Column {
            // Header: make/model/year and status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = onSelectionChanged,
                        modifier = Modifier.testTag("listing_checkbox_${listing.id}"),
                        colors = CheckboxDefaults.colors(
                            checkedColor = CountryOrange,
                            uncheckedColor = MutedBone,
                            checkmarkColor = BoneWhite
                        )
                    )
                    Column {
                        Text(
                            text = "${listing.year} ${listing.make}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BoneWhite
                        )
                        Text(
                            text = listing.model,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            ),
                            color = CountryOrange,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Status Badge
                val badgeColor = if (listing.status.equals("Sold", true)) SmoothRed else SmoothGreen
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .border(1.dp, badgeColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = listing.status.uppercase(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Spec row (enclosed in a beautiful recessed container for extra skeletal depth)
            NeumorphicRecessedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("VEHICLE CLASS", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = MutedBone)
                        Text(listing.vehicleType, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = BoneWhite)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("COLOR", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = MutedBone)
                        Text(listing.color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = BoneWhite)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("BARCODE", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = MutedBone)
                        Text(listing.barcode, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.SemiBold, color = BoneWhite)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Price and Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = format.format(listing.price),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = CountryAmber
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Neumorphic circular trash/delete button
                    NeumorphicIconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_listing_${listing.id}"),
                        containerColor = CinematicCharcoalDark
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = SmoothRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Neumorphic secondary action: Docs
                    NeumorphicButton(
                        onClick = onExportDocs,
                        modifier = Modifier.testTag("export_docs_btn_${listing.id}"),
                        containerColor = CinematicCharcoalMedium
                    ) {
                        Icon(Icons.Rounded.Description, contentDescription = "Documents", modifier = Modifier.size(16.dp), tint = CountryAmber)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Docs", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BoneWhite)
                    }

                    // Neumorphic primary action: Sale
                    if (!listing.status.equals("Sold", true)) {
                        NeumorphicButton(
                            onClick = onPrepareSale,
                            modifier = Modifier.testTag("sale_btn_${listing.id}"),
                            accentColor = CountryOrange
                        ) {
                            Icon(Icons.Default.PointOfSale, contentDescription = "Sale", modifier = Modifier.size(16.dp), tint = BoneWhite)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sale", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BoneWhite)
                        }
                    } else {
                        // Sold label in place of sale button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CinematicCharcoalDark)
                                .border(1.dp, SmoothGray, RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text("SOLD", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MutedBone)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BarcodeMappingsScreen(viewModel: InventoryViewModel) {
    val mappings by viewModel.allMappings.collectAsState()
    val format = NumberFormat.getCurrencyInstance(Locale.US)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "BARCODE-TO-VEHICLE MAP CATALOG",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = CountryOrange
        )
        Text(
            text = "Database of all learned barcode associations. Scanning these barcodes automatically pre-populates the incoming specs form.",
            style = MaterialTheme.typography.bodySmall,
            color = MutedBone
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (mappings.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No barcode mappings saved. Scan and save a barcode specifications card to start cataloging.",
                    textAlign = TextAlign.Center,
                    color = MutedBone
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(mappings) { map ->
                    NeumorphicCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = map.barcode,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = CountryAmber
                                )
                                Text(
                                    text = "${map.year} ${map.make} ${map.model}",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BoneWhite
                                )
                                Text(
                                    text = "Color: ${map.color} | Price: ${format.format(map.price)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedBone
                                )
                            }
                            NeumorphicIconButton(
                                onClick = { viewModel.deleteMapping(map.barcode) },
                                containerColor = CinematicCharcoalDark
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete mapping",
                                    tint = SmoothRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Sales Preparation Screen with Document pre-population and signature ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesPreparationScreen(
    viewModel: InventoryViewModel,
    listing: InventoryListing,
    onClose: () -> Unit,
    showSnackbar: (String) -> Unit
) {
    var documentTab by remember { mutableStateOf(0) } // 0: Bill of Sale, 1: Financing Papers, 2: DMV Registration

    val buyerName by viewModel.buyerName.collectAsState()
    val buyerEmail by viewModel.buyerEmail.collectAsState()
    val buyerPhone by viewModel.buyerPhone.collectAsState()
    val buyerAddress by viewModel.buyerAddress.collectAsState()
    val saleDate by viewModel.saleDate.collectAsState()

    val downPayment by viewModel.downPaymentInput.collectAsState()
    val interestRate by viewModel.interestRateInput.collectAsState()
    val loanTerm by viewModel.loanTermInput.collectAsState()

    val format = NumberFormat.getCurrencyInstance(Locale.US)

    // Drawing signature state
    val signaturePaths = remember { mutableStateListOf<Path>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showEmailDialog by remember { mutableStateOf(false) }
    var emailAddressInput by remember { mutableStateOf("") }
    var isSendingEmail by remember { mutableStateOf(false) }

    var showPrintDialog by remember { mutableStateOf(false) }
    var selectedPrinter by remember { mutableStateOf("Office Jet 5000 (Sales Room)") }
    var isPrintingJob by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.completeSale() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "SALE CONTRACT PREPARATION",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Box(modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Target Vehicle spec card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Pre-populating Documents For:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(
                        text = "${listing.year} ${listing.make} ${listing.model}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "VIN: ${listing.vin} | Original Price: ${format.format(listing.price)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Buyer details input
        Text("1. CUSTOMER & DEALERSHIP INPUTS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = buyerName,
            onValueChange = { viewModel.buyerName.value = it },
            label = { Text("Buyer Full Name") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("buyer_name_field"),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = buyerPhone,
                onValueChange = { viewModel.buyerPhone.value = it },
                label = { Text("Phone") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("buyer_phone_field"),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
                value = buyerEmail,
                onValueChange = { viewModel.buyerEmail.value = it },
                label = { Text("Email") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("buyer_email_field"),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = buyerAddress,
            onValueChange = { viewModel.buyerAddress.value = it },
            label = { Text("Buyer Registration Address") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("buyer_address_field"),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Finance Fields (Pre-population options)
        Text("Financing Options:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = downPayment,
                onValueChange = { viewModel.downPaymentInput.value = it },
                label = { Text("Down Payment ($)") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("down_payment_field"),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
                value = interestRate,
                onValueChange = { viewModel.interestRateInput.value = it },
                label = { Text("APR %") },
                modifier = Modifier
                    .weight(0.8f)
                    .testTag("interest_rate_field"),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
                value = loanTerm,
                onValueChange = { viewModel.loanTermInput.value = it },
                label = { Text("Term (Mo)") },
                modifier = Modifier
                    .weight(0.8f)
                    .testTag("loan_term_field"),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Generated Documents Tabs
        Text("2. PRE-POPULATED DEALERSHIP CONTRACTS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Bill of Sale", "Finance Papers", "DMV Papers").forEachIndexed { index, tabName ->
                Button(
                    onClick = { documentTab = index },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("doc_tab_$index"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (documentTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (documentTab == index) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(tabName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Display pre-populated Document Template
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White) // Print documents are classically white
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                when (documentTab) {
                    0 -> BillOfSaleDoc(listing, buyerName, buyerPhone, buyerAddress, saleDate, format)
                    1 -> FinanceDoc(listing, buyerName, downPayment, interestRate, loanTerm, format)
                    2 -> DmvDoc(listing, buyerName, buyerAddress, saleDate)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Document Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val pdfFile = when (documentTab) {
                        0 -> com.example.utils.DocumentPdfExporter.generateBillOfSalePdf(
                            context = context,
                            listing = listing,
                            buyerName = buyerName,
                            buyerPhone = buyerPhone,
                            buyerAddress = buyerAddress,
                            saleDate = saleDate,
                            signaturePaths = signaturePaths.map { it.asAndroidPath() }
                        )
                        1 -> com.example.utils.DocumentPdfExporter.generateFinanceAgreementPdf(
                            context = context,
                            listing = listing,
                            buyerName = buyerName,
                            downPaymentStr = downPayment,
                            rateStr = interestRate,
                            termStr = loanTerm,
                            signaturePaths = signaturePaths.map { it.asAndroidPath() }
                        )
                        2 -> com.example.utils.DocumentPdfExporter.generateDmvRegistrationPdf(
                            context = context,
                            listing = listing,
                            buyerName = buyerName,
                            buyerAddress = buyerAddress,
                            saleDate = saleDate,
                            signaturePaths = signaturePaths.map { it.asAndroidPath() }
                        )
                        else -> null
                    }
                    if (pdfFile != null) {
                        showSnackbar("Successfully exported contract to Downloads: ${pdfFile.name}")
                    } else {
                        showSnackbar("Error generating document PDF")
                    }
                },
                modifier = Modifier
                    .weight(1.1f)
                    .testTag("download_pdf_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(Icons.Rounded.Download, contentDescription = "Download", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    showEmailDialog = true
                },
                modifier = Modifier
                    .weight(0.9f)
                    .testTag("email_pdf_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(Icons.Rounded.Email, contentDescription = "Email", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Email", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    showPrintDialog = true
                },
                modifier = Modifier
                    .weight(0.9f)
                    .testTag("print_pdf_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(Icons.Rounded.Print, contentDescription = "Print", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Print", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Dialogs for Email and Print
        if (showEmailDialog) {
            val targetEmail = if (emailAddressInput.isBlank()) buyerEmail.ifBlank { "johndoe@example.com" } else emailAddressInput
            AlertDialog(
                onDismissRequest = { if (!isSendingEmail) showEmailDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Email Contract PDF", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Prepare and transmit secure PDF contract documents to the client's email address.", style = MaterialTheme.typography.bodyMedium)
                        
                        OutlinedTextField(
                            value = targetEmail,
                            onValueChange = { emailAddressInput = it },
                            label = { Text("Recipient Email") },
                            modifier = Modifier.fillMaxWidth().testTag("email_recipient_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        // Attached Badge
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Column {
                                    val docName = when (documentTab) {
                                        0 -> "Moab_Bill_Of_Sale_${listing.id}.pdf"
                                        1 -> "Moab_Finance_Agreement_${listing.id}.pdf"
                                        2 -> "Moab_DMV_Registration_${listing.id}.pdf"
                                        else -> "document.pdf"
                                    }
                                    Text(docName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text("A4 Document • Pre-populated • Digital Signatures Included", style = MaterialTheme.typography.bodySmall, fontSize = 9.sp, color = Color.Gray)
                                }
                            }
                        }

                        if (isSendingEmail) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                Text("Encrypting attachment & sending...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isSendingEmail = true
                            scope.launch {
                                kotlinx.coroutines.delay(1800)
                                isSendingEmail = false
                                showEmailDialog = false
                                
                                val pdfFile = when (documentTab) {
                                    0 -> com.example.utils.DocumentPdfExporter.generateBillOfSalePdf(
                                        context = context,
                                        listing = listing,
                                        buyerName = buyerName,
                                        buyerPhone = buyerPhone,
                                        buyerAddress = buyerAddress,
                                        saleDate = saleDate,
                                        signaturePaths = signaturePaths.map { it.asAndroidPath() }
                                    )
                                    1 -> com.example.utils.DocumentPdfExporter.generateFinanceAgreementPdf(
                                        context = context,
                                        listing = listing,
                                        buyerName = buyerName,
                                        downPaymentStr = downPayment,
                                        rateStr = interestRate,
                                        termStr = loanTerm,
                                        signaturePaths = signaturePaths.map { it.asAndroidPath() }
                                    )
                                    2 -> com.example.utils.DocumentPdfExporter.generateDmvRegistrationPdf(
                                        context = context,
                                        listing = listing,
                                        buyerName = buyerName,
                                        buyerAddress = buyerAddress,
                                        saleDate = saleDate,
                                        signaturePaths = signaturePaths.map { it.asAndroidPath() }
                                    )
                                    else -> null
                                }
                                
                                if (pdfFile != null) {
                                    com.example.utils.DocumentPdfExporter.sharePdfFile(context, pdfFile, targetEmail)
                                    showSnackbar("Sent contract documents to: $targetEmail")
                                } else {
                                    showSnackbar("Failed to generate PDF for emailing")
                                }
                            }
                        },
                        enabled = !isSendingEmail && targetEmail.isNotBlank(),
                        modifier = Modifier.testTag("send_email_confirm_btn")
                    ) {
                        Text("Send & Share")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showEmailDialog = false },
                        enabled = !isSendingEmail
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showPrintDialog) {
            var copiesCount by remember { mutableStateOf(1) }
            AlertDialog(
                onDismissRequest = { if (!isPrintingJob) showPrintDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Print Dealership Copy", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Spool pre-populated contract page to local office or network printer.", style = MaterialTheme.typography.bodyMedium)
                        
                        Text("Select Local Printer:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedPrinter = if (selectedPrinter.contains("Office Jet")) "Canon Pro (Front Desk)" else "Office Jet 5000 (Sales Room)"
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Print, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                Text(selectedPrinter, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Change printer")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Number of Copies:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                IconButton(
                                    onClick = { if (copiesCount > 1) copiesCount-- },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                }
                                Text(copiesCount.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                IconButton(
                                    onClick = { copiesCount++ },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        if (isPrintingJob) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                Text("Connecting & spooling to local subnet...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isPrintingJob = true
                            scope.launch {
                                kotlinx.coroutines.delay(1800)
                                isPrintingJob = false
                                showPrintDialog = false
                                
                                val pdfFile = when (documentTab) {
                                    0 -> com.example.utils.DocumentPdfExporter.generateBillOfSalePdf(
                                        context = context,
                                        listing = listing,
                                        buyerName = buyerName,
                                        buyerPhone = buyerPhone,
                                        buyerAddress = buyerAddress,
                                        saleDate = saleDate,
                                        signaturePaths = signaturePaths.map { it.asAndroidPath() }
                                    )
                                    1 -> com.example.utils.DocumentPdfExporter.generateFinanceAgreementPdf(
                                        context = context,
                                        listing = listing,
                                        buyerName = buyerName,
                                        downPaymentStr = downPayment,
                                        rateStr = interestRate,
                                        termStr = loanTerm,
                                        signaturePaths = signaturePaths.map { it.asAndroidPath() }
                                    )
                                    2 -> com.example.utils.DocumentPdfExporter.generateDmvRegistrationPdf(
                                        context = context,
                                        listing = listing,
                                        buyerName = buyerName,
                                        buyerAddress = buyerAddress,
                                        saleDate = saleDate,
                                        signaturePaths = signaturePaths.map { it.asAndroidPath() }
                                    )
                                    else -> null
                                }
                                if (pdfFile != null) {
                                    com.example.utils.DocumentPdfExporter.printPdf(context, pdfFile)
                                    showSnackbar("Successfully spooled job to $selectedPrinter ($copiesCount copies)")
                                } else {
                                    showSnackbar("Failed to generate PDF for printing")
                                }
                            }
                        },
                        enabled = !isPrintingJob,
                        modifier = Modifier.testTag("print_confirm_btn")
                    ) {
                        Text("Print Job")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showPrintDialog = false },
                        enabled = !isPrintingJob
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Buyer interactive signature pad
        Text("3. INTERACTIVE BUYER SIGNATURE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Text("Sign below with your stylus or finger to authorize the contracts.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val path = Path().apply { moveTo(offset.x, offset.y) }
                            currentPath = path
                            signaturePaths.add(path)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            currentPath?.let { path ->
                                val currentOffset = change.position
                                path.lineTo(currentOffset.x, currentOffset.y)
                                // Force recomposition on drawing canvas
                                val temp = signaturePaths.toList()
                                signaturePaths.clear()
                                signaturePaths.addAll(temp)
                            }
                        },
                        onDragEnd = {
                            currentPath = null
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                signaturePaths.forEach { path ->
                    drawPath(
                        path = path,
                        color = Color.Black,
                        style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            if (signaturePaths.isEmpty()) {
                Text(
                    text = "X ___________________________ SIGN HERE",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .alpha(0.25f),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            IconButton(
                onClick = { signaturePaths.clear() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Clear signature", tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Confirm & Execute button
        Button(
            onClick = {
                onClose() // sold status triggers
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("execute_sale_btn"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen),
            enabled = buyerName.isNotBlank() && signaturePaths.isNotEmpty()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = "Check", tint = Color.White)
                Text("Execute Sale & Record Title Transfer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun BillOfSaleDoc(
    listing: InventoryListing,
    buyerName: String,
    buyerPhone: String,
    buyerAddress: String,
    saleDate: String,
    format: NumberFormat
) {
    Text(
        text = "BILL OF SALE & TITLE TRANSFER",
        fontWeight = FontWeight.Black,
        style = MaterialTheme.typography.titleMedium,
        color = Color.Black,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        text = "DEALER RECORD - MOAB POWER SPORTS HUB",
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodySmall,
        color = Color.DarkGray,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))

    Text("1. DECLARED TRANSACTION DATE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
    Text("Date of Transfer: $saleDate", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(8.dp))

    Text("2. SELLER INFORMATION", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
    Text("Moab Off-Road Powersport Dealers LLC, Utah State License #UT-40912", fontSize = 12.sp, color = Color.Black)
    Spacer(modifier = Modifier.height(8.dp))

    Text("3. BUYER CERTIFICATION", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
    Text("Buyer Name: ${buyerName.ifBlank { "____________________" }}", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold)
    Text("Phone: ${buyerPhone.ifBlank { "____________________" }}", fontSize = 12.sp, color = Color.Black)
    Text("Address: ${buyerAddress.ifBlank { "____________________" }}", fontSize = 12.sp, color = Color.Black)
    Spacer(modifier = Modifier.height(8.dp))

    Text("4. DESCRIBED POWERSPORT VEHICLE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
    Text("Vehicle: ${listing.year} ${listing.make} ${listing.model}", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold)
    Text("Class Type: ${listing.vehicleType} | Body Color: ${listing.color}", fontSize = 12.sp, color = Color.Black)
    Text("Assigned VIN/Serial: ${listing.vin}", fontSize = 11.sp, color = Color.Black, fontFamily = FontFamily.Monospace)
    Spacer(modifier = Modifier.height(8.dp))

    Text("5. LIQUIDATED PRICE & FEES SUMMARY", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Negotiated Base Price:", fontSize = 12.sp, color = Color.Black)
        Text(format.format(listing.price), fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.SemiBold)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Utah State Sales Tax (6.5%):", fontSize = 12.sp, color = Color.Black)
        Text(format.format(listing.price * 0.065), fontSize = 12.sp, color = Color.Black)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Dealer Destination Delivery Fee:", fontSize = 12.sp, color = Color.Black)
        Text(format.format(495.00), fontSize = 12.sp, color = Color.Black)
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.LightGray)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Total Transferred Value:", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold)
        Text(format.format(listing.price * 1.065 + 495.00), fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Black)
    }
}

@Composable
fun FinanceDoc(
    listing: InventoryListing,
    buyerName: String,
    downPaymentStr: String,
    rateStr: String,
    termStr: String,
    format: NumberFormat
) {
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

    Text(
        text = "FINANCIAL LEASE & CREDIT AGREEMENT",
        fontWeight = FontWeight.Black,
        style = MaterialTheme.typography.titleMedium,
        color = Color.Black,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))

    Text("DEBTOR CERTIFICATION", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
    Text("Debtor: ${buyerName.ifBlank { "____________________" }}", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))

    Text("COLLATERAL SECURITY DESCRIPTION", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
    Text("${listing.year} ${listing.make} ${listing.model} (VIN: ${listing.vin})", fontSize = 12.sp, color = Color.Black)
    Spacer(modifier = Modifier.height(8.dp))

    Text("AMORTIZATION SCHEDULE DETAILS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Purchase MSRP:", fontSize = 12.sp, color = Color.Black)
        Text(format.format(listing.price), fontSize = 12.sp, color = Color.Black)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Debtor Down Payment Cash:", fontSize = 12.sp, color = Color.Black)
        Text(format.format(downPayment), fontSize = 12.sp, color = Color.Black)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Total Financed Principal:", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.SemiBold)
        Text(format.format(principal), fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.SemiBold)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Interest Rate (APR):", fontSize = 12.sp, color = Color.Black)
        Text("$rateStr%", fontSize = 12.sp, color = Color.Black)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Amortization Term Period:", fontSize = 12.sp, color = Color.Black)
        Text("$term Months", fontSize = 12.sp, color = Color.Black)
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.LightGray)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("CALCULATED ESTIMATED MONTHLY PAYMENT:", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
        Text(format.format(monthlyPayment), fontSize = 14.sp, color = Color.Red, fontWeight = FontWeight.Black)
    }
}

@Composable
fun DmvDoc(
    listing: InventoryListing,
    buyerName: String,
    buyerAddress: String,
    saleDate: String
) {
    Text(
        text = "UTAH DMV OFF-ROAD TITLE REGISTRATION",
        fontWeight = FontWeight.Black,
        style = MaterialTheme.typography.titleMedium,
        color = Color.Black,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))

    Text("OFF-HIGHWAY VEHICLE (OHV) CATEGORY", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
    Text("Permit Registration Classification: ${listing.vehicleType}", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))

    Text("VEHICLE DESCRIPTION SPECIFICATIONS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
    Text("Make: ${listing.make} | Model: ${listing.model} | Year: ${listing.year}", fontSize = 12.sp, color = Color.Black)
    Text("Color: ${listing.color} | Vehicle Type ID: ${listing.barcode}", fontSize = 12.sp, color = Color.Black)
    Text("VIN/HULL Identification: ${listing.vin}", fontSize = 11.sp, color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))

    Text("REGISTERED OWNER DETAILS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
    Text("Owner: ${buyerName.ifBlank { "____________________" }}", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold)
    Text("Address: ${buyerAddress.ifBlank { "____________________" }}", fontSize = 12.sp, color = Color.Black)
    Spacer(modifier = Modifier.height(8.dp))

    Text("DEALERSHIP COMPLIANCE VERIFICATION", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
    Text("Agent Signature: Moab Powersports Hub Representative", fontSize = 11.sp, color = Color.Black)
    Text("Emission Standard Exemption: EPA Off-Road Certified", fontSize = 11.sp, color = Color.Black)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentTemplatesMapperScreen(
    viewModel: InventoryViewModel,
    showSnackbar: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val customDocs by viewModel.allCustomDocuments.collectAsState()
    val selectedDocId by viewModel.selectedDocumentId.collectAsState()
    val activeZones by viewModel.currentDocZones.collectAsState()
    val scannedListings by viewModel.allListings.collectAsState()
    val barcodeMappings by viewModel.allMappings.collectAsState()

    var showAddDocDialog by remember { mutableStateOf(false) }
    var newDocName by remember { mutableStateOf("") }
    var newDocUrl by remember { mutableStateOf("https://images.unsplash.com/photo-1554415707-6e8cfc93fe23?auto=format&fit=crop&q=80&w=600") }

    // State for click-and-drag box drawing
    var isDragging by remember { mutableStateOf(false) }
    var dragStartOffset by remember { mutableStateOf(Offset.Zero) }
    var dragCurrentOffset by remember { mutableStateOf(Offset.Zero) }

    // Canvas size in pixels to calculate percentages
    var canvasWidthPx by remember { mutableStateOf(1f) }
    var canvasHeightPx by remember { mutableStateOf(1f) }

    // Dialog state for variable designation
    var showAssignDialog by remember { mutableStateOf(false) }
    var pendingLeft by remember { mutableStateOf(0f) }
    var pendingTop by remember { mutableStateOf(0f) }
    var pendingRight by remember { mutableStateOf(0f) }
    var pendingBottom by remember { mutableStateOf(0f) }
    var selectedFieldName by remember { mutableStateOf("make") }

    // Sandbox simulation selection
    var sandboxSimulationMode by remember { mutableStateOf(true) }
    var sandboxSelectedListingIndex by remember { mutableStateOf(0) }

    val mockPredefinedTemplates = listOf(
        Pair("Standard DMV Title Application", "https://images.unsplash.com/photo-1586075010923-2dd4570fb338?auto=format&fit=crop&q=80&w=600"),
        Pair("Bill of Sale & Finance Agreement", "https://images.unsplash.com/photo-1554415707-6e8cfc93fe23?auto=format&fit=crop&q=80&w=600"),
        Pair("Dealer Inspection & Prep Checklist", "https://images.unsplash.com/photo-1506784983877-45594efa4cbe?auto=format&fit=crop&q=80&w=600")
    )

    // Select first doc by default if none selected
    LaunchedEffect(customDocs) {
        if (selectedDocId == null && customDocs.isNotEmpty()) {
            viewModel.selectDocument(customDocs.first().id)
        }
    }

    val selectedDocument = customDocs.find { it.id == selectedDocId }

    // Master list of available variable mappings
    val variableFields = listOf(
        Pair("make", "Make / Brand"),
        Pair("model", "Model Name"),
        Pair("year", "Year"),
        Pair("color", "Vehicle Color"),
        Pair("vin", "17-Digit VIN"),
        Pair("price", "Dealer Price"),
        Pair("buyerName", "Buyer Name"),
        Pair("saleDate", "Sale Date"),
        Pair("downPayment", "Down Payment"),
        Pair("vehicleType", "Vehicle Type"),
        Pair("notes", "Scan Notes")
    )

    // Find the currently selected vehicle for live sandbox simulation
    val currentSandboxVehicle = if (scannedListings.isNotEmpty() && sandboxSelectedListingIndex < scannedListings.size) {
        scannedListings[sandboxSelectedListingIndex]
    } else if (barcodeMappings.isNotEmpty()) {
        val mapping = barcodeMappings.first()
        InventoryListing(
            barcode = mapping.barcode,
            make = mapping.make,
            model = mapping.model,
            year = mapping.year,
            color = mapping.color,
            vehicleType = mapping.vehicleType,
            vin = mapping.vin,
            price = mapping.price,
            notes = mapping.notes,
            status = "In Stock"
        )
    } else {
        InventoryListing(
            barcode = "DEMO-999",
            make = "Can-Am",
            model = "Maverick X3 DS Turbo",
            year = "2023",
            color = "Can-Am Red",
            vehicleType = "Side-by-Side (UTV)",
            vin = "3JBXAA01XNP102431",
            price = 22499.00,
            notes = "Turbocharged Rotax ACE engine. Fox 2.5 Shocks.",
            status = "In Stock"
        )
    }

    fun getPopulatedValue(fieldName: String): String {
        return when (fieldName) {
            "make" -> currentSandboxVehicle.make
            "model" -> currentSandboxVehicle.model
            "year" -> currentSandboxVehicle.year
            "color" -> currentSandboxVehicle.color
            "vin" -> currentSandboxVehicle.vin
            "price" -> "$${String.format("%,.2f", currentSandboxVehicle.price)}"
            "buyerName" -> "John Doe (Dealer Draft)"
            "saleDate" -> "2026-07-03"
            "downPayment" -> "$2,000.00"
            "vehicleType" -> currentSandboxVehicle.vehicleType
            "notes" -> currentSandboxVehicle.notes
            else -> "N/A"
        }
    }

    // Main layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Applet header styled symmetrically
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TEMPLATE BUILDER & ZONE MAPPER",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = CountryOrange
                )
                Text(
                    text = "Map input zones over scanned documents & title agreements",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedBone
                )
            }
            NeumorphicButton(
                onClick = { showAddDocDialog = true },
                accentColor = CountryOrange,
                modifier = Modifier.testTag("upload_template_btn")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = BoneWhite)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Scan/Upload", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = BoneWhite)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (customDocs.isEmpty()) {
            // Empty state for documents
            NeumorphicCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        tint = CountryOrange,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Templates Loaded",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = BoneWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Upload or scan dealer contracts, title documents, or custom forms to get started with auto-fill mappings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedBone,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    NeumorphicButton(
                        onClick = { showAddDocDialog = true },
                        accentColor = CountryOrange
                    ) {
                        Text("Add Document Template", color = BoneWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // We have custom documents loaded!
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Panel: Documents List & Field Mappings
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Document selector
                    Text(
                        text = "SELECT TEMPLATE FORM",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.4f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(customDocs) { doc ->
                            val isSelected = doc.id == selectedDocId
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectDocument(doc.id) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Description,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = doc.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Mapped variables: ${if (isSelected) activeZones.size else "..."}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteDocument(doc.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Delete Document",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Sandbox Simulation Controls
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SANDBOX SIMULATOR",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Auto-Fill Info", style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Switch(
                                        checked = sandboxSimulationMode,
                                        onCheckedChange = { sandboxSimulationMode = it },
                                        modifier = Modifier.scale(0.7f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Choose Scanned Vehicle to Test populate on contract layout:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Vehicle Spinner list
                            val vehicleNames = (scannedListings.map { "${it.year} ${it.make} ${it.model}" }).toMutableList()
                            if (vehicleNames.isEmpty()) {
                                vehicleNames.add("Can-Am Maverick Demo")
                            }

                            var dropdownExpanded by remember { mutableStateOf(false) }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { dropdownExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (scannedListings.isNotEmpty() && sandboxSelectedListingIndex < scannedListings.size) {
                                                "${scannedListings[sandboxSelectedListingIndex].year} ${scannedListings[sandboxSelectedListingIndex].make} ${scannedListings[sandboxSelectedListingIndex].model}"
                                            } else {
                                                "Demo: Can-Am Maverick X3"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                }

                                DropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    scannedListings.forEachIndexed { idx, listing ->
                                        DropdownMenuItem(
                                            text = { Text("${listing.year} ${listing.make} ${listing.model} (${listing.vin.take(6)})") },
                                            onClick = {
                                                sandboxSelectedListingIndex = idx
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Mapping instructions card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "👉 Mapping Instructions:",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "1. Select or scan a template document.\n" +
                                        "2. Click & drag (or pen-draw) a bounding box on the document preview to the right.\n" +
                                        "3. Select which scanned information variable (make, VIN, customer etc.) will auto-populate there.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Right Panel: Interactive Visual Mapper Canvas
                Column(
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "INTERACTIVE DOCUMENT DRAWING CANVAS (DRAG TO MAP)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    selectedDocument?.let { doc ->
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.DarkGray)
                                .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant))
                        ) {
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val constraintsWidth = maxWidth
                            val constraintsHeight = maxHeight
                            val maxWidthPx = with(density) { constraintsWidth.toPx() }
                            val maxHeightPx = with(density) { constraintsHeight.toPx() }

                            // Update canvas measurement size variables
                            LaunchedEffect(maxWidthPx, maxHeightPx) {
                                canvasWidthPx = maxWidthPx
                                canvasHeightPx = maxHeightPx
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                isDragging = true
                                                dragStartOffset = offset
                                                dragCurrentOffset = offset
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragCurrentOffset += dragAmount
                                            },
                                            onDragEnd = {
                                                isDragging = false
                                                // Convert coordinates to percentages of canvas size!
                                                val leftPct = (minOf(dragStartOffset.x, dragCurrentOffset.x) / canvasWidthPx).coerceIn(0f, 1f)
                                                val topPct = (minOf(dragStartOffset.y, dragCurrentOffset.y) / canvasHeightPx).coerceIn(0f, 1f)
                                                val rightPct = (maxOf(dragStartOffset.x, dragCurrentOffset.x) / canvasWidthPx).coerceIn(0f, 1f)
                                                val bottomPct = (maxOf(dragStartOffset.y, dragCurrentOffset.y) / canvasHeightPx).coerceIn(0f, 1f)

                                                // Only show assignment dialog if box is reasonably large (to filter random clicks)
                                                if ((rightPct - leftPct) > 0.02f && (bottomPct - topPct) > 0.02f) {
                                                    pendingLeft = leftPct
                                                    pendingTop = topPct
                                                    pendingRight = rightPct
                                                    pendingBottom = bottomPct
                                                    showAssignDialog = true
                                                }
                                            }
                                        )
                                    }
                            ) {
                                // Background contract document flat template
                                AsyncImage(
                                    model = doc.imageUrl,
                                    contentDescription = "Contract Background Template",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                // Tech blueprints grids background overlay
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.05f)
                                    )
                                }

                                // Overlay Existing Zones loaded from Database
                                activeZones.forEach { zone ->
                                    val left = constraintsWidth * zone.leftPercent
                                    val top = constraintsHeight * zone.topPercent
                                    val right = constraintsWidth * zone.rightPercent
                                    val bottom = constraintsHeight * zone.bottomPercent
                                    val width = right - left
                                    val height = bottom - top

                                    Box(
                                        modifier = Modifier
                                            .offset(x = left, y = top)
                                            .size(width = width, height = height)
                                            .background(
                                                if (sandboxSimulationMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                                else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
                                            )
                                            .border(
                                                BorderStroke(
                                                    2.dp,
                                                    if (sandboxSimulationMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                                ),
                                                RoundedCornerShape(4.dp)
                                            )
                                    ) {
                                        // Content layout: Sandbox Filled Value OR Variable Tag Placeholder!
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(4.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Dynamic Tag text
                                                Text(
                                                    text = "[${zone.fieldName.uppercase()}]",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 0.5.sp
                                                    ),
                                                    color = if (sandboxSimulationMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier
                                                        .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                )

                                                // Quick delete icon
                                                IconButton(
                                                    onClick = { viewModel.deleteDocumentZone(zone.id) },
                                                    modifier = Modifier.size(16.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = "Delete Mapping",
                                                        tint = Color.Red,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }

                                            // Populated value overlay if simulation is on!
                                            if (sandboxSimulationMode) {
                                                Text(
                                                    text = getPopulatedValue(zone.fieldName),
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF1F2937) // Dark print text
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .background(Color(0xFFFEF3C7), RoundedCornerShape(3.dp)) // Yellow highlighter print
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }

                                // Overlay active drag drawing box feedback
                                if (isDragging) {
                                    val dragLeft = minOf(dragStartOffset.x, dragCurrentOffset.x)
                                    val dragTop = minOf(dragStartOffset.y, dragCurrentOffset.y)
                                    val dragRight = maxOf(dragStartOffset.x, dragCurrentOffset.x)
                                    val dragBottom = maxOf(dragStartOffset.y, dragCurrentOffset.y)

                                    val leftDp = with(density) { dragLeft.toDp() }
                                    val topDp = with(density) { dragTop.toDp() }
                                    val widthDp = with(density) { (dragRight - dragLeft).toDp() }
                                    val heightDp = with(density) { (dragBottom - dragTop).toDp() }

                                    Box(
                                        modifier = Modifier
                                            .offset(x = leftDp, y = topDp)
                                            .size(width = widthDp, height = heightDp)
                                            .background(Color(0x334F46E5))
                                            .border(
                                                BorderStroke(2.dp, Brush.linearGradient(listOf(Color.Cyan, Color.Magenta))),
                                                RoundedCornerShape(4.dp)
                                            )
                                    ) {
                                        Text(
                                            text = "DRAWING ZONE...",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } ?: run {
                        // Document unselected view
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Please Select a Template Document Form to Begin mapping.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // Assign Variable Field Dialog
    if (showAssignDialog) {
        AlertDialog(
            onDismissRequest = { showAssignDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Designate Information Variable", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column {
                    Text(
                        text = "Assign the captured area to auto-populate with a catalog vehicle specification field when generating the document:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("SELECT VARIABLE FROM SCANNED DATABASE:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Grid-like chip selector
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        variableFields.chunked(2).forEach { chunk ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                chunk.forEach { pair ->
                                    val isSelected = selectedFieldName == pair.first
                                    InputChip(
                                        selected = isSelected,
                                        onClick = { selectedFieldName = pair.first },
                                        label = { Text(pair.second, style = MaterialTheme.typography.bodySmall) },
                                        leadingIcon = {
                                            if (isSelected) {
                                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addDocumentZone(
                            fieldName = selectedFieldName,
                            left = pendingLeft,
                            top = pendingTop,
                            right = pendingRight,
                            bottom = pendingBottom
                        )
                        showAssignDialog = false
                        showSnackbar("Mapping zone assigned successfully!")
                    }
                ) {
                    Text("Save Mapping Zone")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAssignDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Document Dialog
    if (showAddDocDialog) {
        AlertDialog(
            onDismissRequest = { showAddDocDialog = false },
            title = {
                Text("Scan & Upload Document Template", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Store custom legal files, dealership bill of sales, or compliance forms. Define input text field overlays visually.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = newDocName,
                        onValueChange = { newDocName = it },
                        label = { Text("Template Document Name") },
                        placeholder = { Text("e.g. Washington DMV Form 735") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("CHOOSE MOCK SCANNED BACKDROP TEMPLATE:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

                    // Display list of pre-set backdrops
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(mockPredefinedTemplates) { (name, url) ->
                            val isSelected = newDocUrl == url
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { newDocUrl = url },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = isSelected, onClick = { newDocUrl = url })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newDocName.isNotBlank()) {
                            viewModel.addNewDocument(newDocName, newDocUrl)
                            showAddDocDialog = false
                            newDocName = ""
                            showSnackbar("Custom contract template loaded!")
                        } else {
                            showSnackbar("Please enter a valid template document name.")
                        }
                    }
                ) {
                    Text("Add Template")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDocDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

