package com.alvinb.counterpos.ui.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

enum class ScannerMode {
    SELL,
    TAKE_STOCK,
    TABLE_ADD,
    ADMIN_QR,
    ASSIGN_BARCODE
}

@Composable
fun BarcodeScannerScreen(
    mode: ScannerMode,
    products: List<ProductEntity>,
    onClose: () -> Unit,
    onProductScannedForSale: (ProductEntity) -> Unit,
    onProductScannedForStock: (ProductEntity) -> Unit,
    onProductScannedForTable: (ProductEntity) -> Unit,
    onAdminQrCodeScanned: (String) -> Unit,
    onBarcodeAssigned: (String) -> Unit,
    onBarcodeNotRecognized: (String) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraScannerView(
            mode = mode,
            products = products,
            onClose = onClose,
            onProductScannedForSale = onProductScannedForSale,
            onProductScannedForStock = onProductScannedForStock,
            onProductScannedForTable = onProductScannedForTable,
            onAdminQrCodeScanned = onAdminQrCodeScanned,
            onBarcodeAssigned = onBarcodeAssigned,
            onBarcodeNotRecognized = onBarcodeNotRecognized
        )
    } else {
        PermissionRequiredScreen(
            onGrantPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onClose = onClose,
            products = products,
            mode = mode
        )
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraScannerView(
    mode: ScannerMode,
    products: List<ProductEntity>,
    onClose: () -> Unit,
    onProductScannedForSale: (ProductEntity) -> Unit,
    onProductScannedForStock: (ProductEntity) -> Unit,
    onProductScannedForTable: (ProductEntity) -> Unit,
    onAdminQrCodeScanned: (String) -> Unit,
    onBarcodeAssigned: (String) -> Unit,
    onBarcodeNotRecognized: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var camera by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(false) }

    var lastScannedBarcode by remember { mutableStateOf<String?>(null) }
    var lastScannedProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var lastScannedTime by remember { mutableLongStateOf(0L) }
    var scanSuccessFlash by remember { mutableStateOf(false) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_QR_CODE
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    // Laser sweep line animation
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_pos"
    )

    fun onBarcodeDetected(rawBarcode: String) {
        val now = System.currentTimeMillis()
        // Debounce if same barcode scanned in last 1.8 seconds
        if (rawBarcode == lastScannedBarcode && (now - lastScannedTime) < 1800) {
            return
        }

        lastScannedBarcode = rawBarcode
        lastScannedTime = now
        scanSuccessFlash = true

        triggerHapticAndTone(context)

        val matchedProduct = findProductByCodeOrBarcode(rawBarcode, products)
        lastScannedProduct = matchedProduct

        handleBarcodeResult(
            barcode = rawBarcode,
            mode = mode,
            products = products,
            context = context,
            onProductScannedForSale = onProductScannedForSale,
            onProductScannedForStock = onProductScannedForStock,
            onProductScannedForTable = onProductScannedForTable,
            onAdminQrCodeScanned = onAdminQrCodeScanned,
            onBarcodeAssigned = onBarcodeAssigned,
            onBarcodeNotRecognized = onBarcodeNotRecognized
        )
    }

    LaunchedEffect(scanSuccessFlash) {
        if (scanSuccessFlash) {
            kotlinx.coroutines.delay(400)
            scanSuccessFlash = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            barcodeScanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val rawValue = barcode.rawValue
                                        if (!rawValue.isNullOrBlank()) {
                                            onBarcodeDetected(rawValue)
                                            break
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    val cameraSelector = if (useFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            update = {
                // Handle camera toggles if needed
            }
        )

        // Overlay with scanning reticle
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidth = maxWidth
            val screenHeight = maxHeight
            val reticleWidth = (screenWidth * 0.82f).coerceAtMost(340.dp)
            val reticleHeight = (reticleWidth * 0.65f).coerceAtLeast(180.dp)

            // Scrim with cutout
            Canvas(modifier = Modifier.fillMaxSize()) {
                val boxLeft = (size.width - reticleWidth.toPx()) / 2f
                val boxTop = (size.height - reticleHeight.toPx()) / 2.4f
                val boxRect = Rect(
                    offset = Offset(boxLeft, boxTop),
                    size = Size(reticleWidth.toPx(), reticleHeight.toPx())
                )

                // Darken surrounding area
                val path = Path().apply {
                    addRoundRect(RoundRect(boxRect, CornerRadius(24f, 24f)))
                }

                clipPath(path, clipOp = ClipOp.Difference) {
                    drawRect(color = Color(0x99000000))
                }

                // Laser line sweep inside the reticle
                val laserY = boxTop + (reticleHeight.toPx() * laserPosition)
                drawLine(
                    color = if (scanSuccessFlash) Color(0xFF10B981) else Color(0xFFFF3366),
                    start = Offset(boxLeft + 16f, laserY),
                    end = Offset(boxLeft + reticleWidth.toPx() - 16f, laserY),
                    strokeWidth = 3.dp.toPx()
                )
            }

            // Reticle Border & Corner Marks
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 60.dp)
                    .size(width = reticleWidth, height = reticleHeight)
                    .border(
                        width = if (scanSuccessFlash) 3.dp else 2.dp,
                        color = if (scanSuccessFlash) Color(0xFF10B981) else Color(0xFF22D3EE).copy(alpha = 0.8f),
                        shape = RoundedCornerShape(16.dp)
                    )
            )
        }

        // Top Control Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0x77000000), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close scanner",
                        tint = Color.White
                    )
                }

                // Mode Title Pill
                Surface(
                    color = when (mode) {
                        ScannerMode.SELL, ScannerMode.TABLE_ADD -> Color(0xFF0F766E).copy(alpha = 0.9f)
                        ScannerMode.ADMIN_QR -> Color(0xFF4338CA).copy(alpha = 0.9f)
                        else -> Color(0xFFB45309).copy(alpha = 0.9f)
                    },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (mode == ScannerMode.TAKE_STOCK) Icons.Default.Inventory2 else Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when (mode) {
                                ScannerMode.SELL -> "SELLING / REGISTER"
                                ScannerMode.TABLE_ADD -> "ADD TO TABLE BILL"
                                ScannerMode.ADMIN_QR -> "ADMIN ENROLLMENT QR"
                                else -> "TAKING STOCK / RECEIVE"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Torch Toggle
                    IconButton(
                        onClick = {
                            isTorchOn = !isTorchOn
                            camera?.cameraControl?.enableTorch(isTorchOn)
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(if (isTorchOn) Color(0xFFF59E0B) else Color(0x77000000), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                            contentDescription = "Toggle flashlight",
                            tint = Color.White
                        )
                    }

                    // Flip Camera
                    IconButton(
                        onClick = {
                            useFrontCamera = !useFrontCamera
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0x77000000), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Flip camera",
                            tint = Color.White
                        )
                    }
                }
            }

            if (mode == ScannerMode.SELL || mode == ScannerMode.TABLE_ADD || mode == ScannerMode.ADMIN_QR) {
                Text(
                    if (mode == ScannerMode.ADMIN_QR) {
                        "Scan a CounterPOS business or user enrollment QR. The backend still verifies superadmin permission and registered accounts."
                    } else if (mode == ScannerMode.TABLE_ADD) {
                        "Scan an in-stock drink to add it to this table’s bill. Confirm the quantity and sale unit next."
                    } else {
                        "Scan a barcode to sell one unit immediately and deduct it from stock."
                    },
                    color = Color(0xFFD1D5DB),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(Color(0x88000000), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        // Bottom Results & Sample Barcodes
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Last Scanned Item Banner
            AnimatedVisibility(
                visible = lastScannedBarcode != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val item = lastScannedProduct
                val barcode = lastScannedBarcode.orEmpty()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (item != null) Color(0xF0111827) else Color(0xF0374151)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = if (item != null) Color(0xFF10B981) else Color(0xFFF59E0B)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (item != null) Icons.Default.Check else Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item?.name ?: "Scanned: $barcode",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (item != null) {
                                    if (mode == ScannerMode.SELL || mode == ScannerMode.TABLE_ADD) {
                                        "KSh ${item.sellingPriceKsh} · ${item.stockQuantity} in stock"
                                    } else {
                                        "Stock: ${item.stockQuantity} units · Size: ${item.size}"
                                    }
                                } else {
                                    "Barcode detected · Tap below to register or restock"
                                },
                                fontSize = 12.sp,
                                color = Color(0xFFE5E7EB)
                            )
                        }

                        if (item != null && mode == ScannerMode.TAKE_STOCK) {
                            Button(
                                onClick = { onProductScannedForStock(item) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Restock", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequiredScreen(
    onGrantPermission: () -> Unit,
    onClose: () -> Unit,
    products: List<ProductEntity>,
    mode: ScannerMode
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Camera Access Needed",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "The barcode scanner uses your phone camera to scan products on shelves and at checkout, just like in a supermarket.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onGrantPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enable Camera Scanner")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel / Enter Barcode Manually")
        }
    }
}

private fun triggerHapticAndTone(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(45)
        }
    } catch (_: Exception) {}

    try {
        val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
    } catch (_: Exception) {}
}

private fun findProductByCodeOrBarcode(barcode: String, products: List<ProductEntity>): ProductEntity? {
    val clean = barcode.trim()
    return products.firstOrNull { it.catalogCode.equals(clean, ignoreCase = true) }
        ?: products.firstOrNull { it.verifiedBarcode?.equals(clean, ignoreCase = true) == true }
        ?: products.firstOrNull { it.catalogCode.contains(clean, ignoreCase = true) }
}

private fun handleBarcodeResult(
    barcode: String,
    mode: ScannerMode,
    products: List<ProductEntity>,
    context: Context,
    onProductScannedForSale: (ProductEntity) -> Unit,
    onProductScannedForStock: (ProductEntity) -> Unit,
    onProductScannedForTable: (ProductEntity) -> Unit,
    onAdminQrCodeScanned: (String) -> Unit,
    onBarcodeAssigned: (String) -> Unit,
    onBarcodeNotRecognized: (String) -> Unit
) {
    if (mode == ScannerMode.ADMIN_QR) {
        onAdminQrCodeScanned(barcode.trim())
        return
    }
    if (mode == ScannerMode.ASSIGN_BARCODE) {
        onBarcodeAssigned(barcode.trim())
        return
    }
    val matched = findProductByCodeOrBarcode(barcode, products)
    if (matched != null) {
        when (mode) {
            ScannerMode.SELL -> onProductScannedForSale(matched)
            ScannerMode.TABLE_ADD -> onProductScannedForTable(matched)
            ScannerMode.TAKE_STOCK -> onProductScannedForStock(matched)
            ScannerMode.ADMIN_QR -> Unit
            ScannerMode.ASSIGN_BARCODE -> Unit
        }
    } else {
        onBarcodeNotRecognized(barcode)
    }
}
