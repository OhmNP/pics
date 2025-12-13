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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photosync.android.viewmodel.PairingViewModel

import org.json.JSONObject

@Composable
fun PairingScreen(
    onPairingSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PairingViewModel = viewModel()
) {
    val pairingState by viewModel.pairingState.collectAsStateWithLifecycle()
    val discoveredServers by viewModel.discoveredServers.collectAsStateWithLifecycle()
    
    // Auto-discover on mount
    LaunchedEffect(Unit) {
        viewModel.discoverServer()
    }

    // Handle Success State
    LaunchedEffect(pairingState) {
        if (pairingState is PairingViewModel.PairingState.Success) {
            onPairingSuccess()
        }
    }

    var showManualEntry by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var manualToken by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(true) }
    
    // Update manualIp if server discovered
    LaunchedEffect(discoveredServers) {
        if (discoveredServers.isNotEmpty() && manualIp.isEmpty()) {
            manualIp = discoveredServers.first().ip
            // If discovered, maybe show manual entry prepopulated?
            // Or just a "Server Found" card above manual entry.
        }
    }
    
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
        
        // Error Message
        if (pairingState is PairingViewModel.PairingState.Error) {
             Text(
                text = (pairingState as PairingViewModel.PairingState.Error).message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
             )
        }

        // Connecting State
        if (pairingState is PairingViewModel.PairingState.Connecting) {
             CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
             Text("Connecting...", modifier = Modifier.padding(bottom = 16.dp))
        }

        // Discovered Servers
        if (discoveredServers.isNotEmpty() && !showManualEntry && isScanning) {
            Text(
                text = "Discovered Servers",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
            )
            discoveredServers.forEach { server ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    onClick = {
                        manualIp = server.ip
                        showManualEntry = true
                        isScanning = false
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(server.name, style = MaterialTheme.typography.titleMedium)
                        Text(server.ip, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Divider(modifier = Modifier.padding(vertical = 16.dp))
        }
        
        if (!showManualEntry && isScanning) {
            // QR Code Scanner
            QRCodeScanner(
                onQRCodeScanned = { result ->
                    isScanning = false
                    if (result.contains("{")) {
                        try {
                            val json = JSONObject(result)
                            val ip = json.optString("ip")
                            val token = json.optString("token")
                            if (ip.isNotEmpty()) {
                                 viewModel.pairWithServer(ip, token)
                            } else {
                                 viewModel.pairWithServer(result, "") // Fallback
                            }
                        } catch (e: Exception) {
                             viewModel.pairWithServer(result, "")
                        }
                    } else {
                        val parts = result.split(" ")
                        if (parts.size >= 2) {
                             viewModel.pairWithServer(parts[0], parts[1])
                        } else {
                             viewModel.pairWithServer(result, "")
                        }
                    }
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
                Text("Enter Details Manually")
            }
        } else {
            // Manual Entry
            OutlinedTextField(
                value = manualIp,
                onValueChange = { manualIp = it },
                label = { Text("Server IP Address") },
                placeholder = { Text("192.168.1.100") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = manualToken,
                onValueChange = { manualToken = it },
                label = { Text("Pairing Token") },
                placeholder = { Text("123456") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { viewModel.pairWithServer(manualIp, manualToken) },
                modifier = Modifier.fillMaxWidth(),
                enabled = manualIp.isNotBlank() && manualToken.isNotBlank() && pairingState !is PairingViewModel.PairingState.Connecting
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
