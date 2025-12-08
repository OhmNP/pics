package com.photosync.android.ui.screens

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

@Composable
fun PairingScreen(
    onPaired: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showManualEntry by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(true) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Pair with Server",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        if (!showManualEntry && isScanning) {
            // QR Code Scanner
            QRCodeScanner(
                onQRCodeScanned = { serverIp ->
                    isScanning = false
                    onPaired(serverIp)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
            
            Text(
                text = "Scan QR code from server dashboard",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            OutlinedButton(
                onClick = { showManualEntry = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enter IP Manually")
            }
        } else {
            // Manual IP Entry
            OutlinedTextField(
                value = manualIp,
                onValueChange = { manualIp = it },
                label = { Text("Server IP Address") },
                placeholder = { Text("192.168.1.100") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { onPaired(manualIp) },
                modifier = Modifier.fillMaxWidth(),
                enabled = manualIp.isNotBlank()
            ) {
                Text("Connect")
            }
            
            if (!isScanning) {
                OutlinedButton(
                    onClick = { 
                        showManualEntry = false
                        isScanning = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Scan QR Code Instead")
                }
            }
        }
    }
}

@Composable
fun QRCodeScanner(
    onQRCodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasScanned by remember { mutableStateOf(false) }
    
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                            if (!hasScanned) {
                                val buffer = imageProxy.planes[0].buffer
                                val data = ByteArray(buffer.remaining())
                                buffer.get(data)
                                
                                val source = PlanarYUVLuminanceSource(
                                    data,
                                    imageProxy.width,
                                    imageProxy.height,
                                    0, 0,
                                    imageProxy.width,
                                    imageProxy.height,
                                    false
                                )
                                
                                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                                
                                try {
                                    val result = MultiFormatReader().decode(binaryBitmap)
                                    hasScanned = true
                                    onQRCodeScanned(result.text)
                                } catch (e: Exception) {
                                    // No QR code found, continue scanning
                                }
                            }
                            imageProxy.close()
                        }
                    }
                
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    // Handle error
                }
            }, ContextCompat.getMainExecutor(ctx))
            
            previewView
        },
        modifier = modifier
    )
}
