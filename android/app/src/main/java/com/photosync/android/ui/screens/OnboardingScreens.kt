package com.photosync.android.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.photosync.android.viewmodel.PairingViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photosync.android.data.SettingsManager


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(
    onPermissionsGranted: () -> Unit
) {
    var currentStep by remember { mutableStateOf(PermissionStep.NOTIFICATIONS) }

    // Helper to check if a permission is granted
    fun isGranted(permission: String, context: android.content.Context): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            permission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val context = LocalContext.current

    // Determine initial step based on what's already granted
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isGranted(Manifest.permission.POST_NOTIFICATIONS, context)) {
                currentStep = PermissionStep.NOTIFICATIONS
            } else if (!isGranted(Manifest.permission.CAMERA, context)) {
                currentStep = PermissionStep.CAMERA
            } else if (!isGranted(Manifest.permission.READ_MEDIA_IMAGES, context)) {
                currentStep = PermissionStep.STORAGE
            } else {
                currentStep = PermissionStep.DONE
                onPermissionsGranted()
            }
        } else {
             // For older Android versions, skip notifications if not runtime, proceed to Camera
             if (!isGranted(Manifest.permission.CAMERA, context)) {
                currentStep = PermissionStep.CAMERA
            } else if (!isGranted(Manifest.permission.READ_EXTERNAL_STORAGE, context)) {
                currentStep = PermissionStep.STORAGE
            } else {
                currentStep = PermissionStep.DONE
                onPermissionsGranted()
            }
        }
    }

    // Prepare permission states for each step
    
    // Notifications (Android 13+)
    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberMultiplePermissionsState(listOf(Manifest.permission.POST_NOTIFICATIONS))
    } else {
        null
    }

    // Camera
    val cameraPermissionState = rememberMultiplePermissionsState(listOf(Manifest.permission.CAMERA))

    // Storage
    val storagePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val storagePermissionState = rememberMultiplePermissionsState(storagePermissions)

    // Advance logic
    fun advance() {
        when (currentStep) {
            PermissionStep.NOTIFICATIONS -> currentStep = PermissionStep.CAMERA
            PermissionStep.CAMERA -> currentStep = PermissionStep.STORAGE
            PermissionStep.STORAGE -> {
                currentStep = PermissionStep.DONE
                onPermissionsGranted()
            }
            PermissionStep.DONE -> onPermissionsGranted()
        }
    }

    // Describe current step content
    val stepInfo = when (currentStep) {
        PermissionStep.NOTIFICATIONS -> Triple(
            "Stay Updated",
            "Enable notifications to see sync progress and transfer completion status.",
            "Enable Notifications"
        ) to notificationPermissionState
        PermissionStep.CAMERA -> Triple(
            "Quick Pairing",
            "Allow camera access to scan the QR code for instant server pairing.",
            "Enable Camera"
        ) to cameraPermissionState
        PermissionStep.STORAGE -> Triple(
            "Sync Photos",
            "Grant access to your media library so we can sync your photos to the server.",
            "Allow Access"
        ) to storagePermissionState
        PermissionStep.DONE -> Triple("", "", "") to null
    }

    val (contentInfo, currentPermissionState) = stepInfo
    val (title, description, buttonText) = contentInfo
    
    // Auto-advance if system dialog grants it
    LaunchedEffect(currentStep, notificationPermissionState?.allPermissionsGranted, cameraPermissionState.allPermissionsGranted, storagePermissionState.allPermissionsGranted) {
         when (currentStep) {
            PermissionStep.NOTIFICATIONS -> if (notificationPermissionState?.allPermissionsGranted == true) advance()
            PermissionStep.CAMERA -> if (cameraPermissionState.allPermissionsGranted) advance()
            PermissionStep.STORAGE -> if (storagePermissionState.allPermissionsGranted) {
                currentStep = PermissionStep.DONE
                onPermissionsGranted()
            }
            else -> {}
        }
    }

    // Auto-launch permission request when step changes
    LaunchedEffect(currentStep) {
        if (currentStep == PermissionStep.NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
             advance()
        } else {
            currentPermissionState?.launchMultiplePermissionRequest()
        }
    }

    if (currentStep == PermissionStep.DONE) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Button acts as retry mechanism
        Button(
            onClick = {
                if (currentStep == PermissionStep.NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    advance()
                } else {
                    currentPermissionState?.launchMultiplePermissionRequest()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (buttonText == "Allow Access" || buttonText == "Enable Camera" || buttonText == "Enable Notifications") "Retry $buttonText" else buttonText)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// Define steps enum outside
enum class PermissionStep {
    NOTIFICATIONS,
    CAMERA,
    STORAGE,
    DONE
}


@Composable
fun OnboardingServerDiscoveryScreen(
    onOnboardingCompleted: () -> Unit,
    viewModel: PairingViewModel = viewModel()
) {
    val context = LocalContext.current
    // Reuse PairingScreen logic but with "Finish Onboarding" behavior
    PairingScreen(
        onPairingSuccess = {
            // Mark onboarding complete
            val settings = SettingsManager(context)
            settings.isOnboardingCompleted = true
            onOnboardingCompleted()
        },
        viewModel = viewModel
    )
}
