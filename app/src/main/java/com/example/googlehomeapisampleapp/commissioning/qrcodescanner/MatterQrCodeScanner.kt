/* Copyright 2025 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.example.googlehomeapisampleapp.commissioning.qrcodescanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

private const val TAG = "MatterQrCodeScanner"

@Composable
fun MatterQrCodeScanner(
  onQrCodeScanned: (String) -> Unit,
  onPermissionDenied: () -> Unit,
  onClose: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
  val previewView = remember { PreviewView(context) }
  // navigates back to the previous screen instead of exiting/minimizing the app.
  BackHandler(enabled = true) {
    onClose()
  }

  // Check and request Camera Permission
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      bindCameraUseCases(
        context,
        lifecycleOwner,
        cameraProviderFuture,
        previewView,
        onQrCodeScanned
      )
    } else {
      onPermissionDenied()
    }
  }

  DisposableEffect(Unit) {
    if (ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
      ) == PackageManager.PERMISSION_GRANTED
    ) {
      bindCameraUseCases(
        context,
        lifecycleOwner,
        cameraProviderFuture,
        previewView,
        onQrCodeScanned
      )
    } else {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    onDispose {
      // Ensure camera resources are released when the Composable leaves the screen
      cameraProviderFuture.get().unbindAll()
    }
  }

  // Display the Camera Preview
  Box(
    modifier = Modifier.fillMaxSize().background(Color.Black),
    contentAlignment = Alignment.Center
  ) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { previewView }
    )
    // Back button overlay
    IconButton(
      onClick = onClose,
      modifier = Modifier
        .align(Alignment.TopStart)
        .statusBarsPadding()
        .padding(top = 16.dp, start = 16.dp)
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
        tint = Color.White
      )
    }
    // Simple text overlay for user guidance
    Text("Point camera at Matter QR Code", color = Color.White)
  }
}

@OptIn(ExperimentalGetImage::class)
private fun bindCameraUseCases(
  context: Context,
  lifecycleOwner: LifecycleOwner,
  cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
  previewView: PreviewView,
  onQrCodeScanned: (String) -> Unit,
) {
  cameraProviderFuture.addListener(
    {
      val cameraProvider = cameraProviderFuture.get()
      val preview = Preview.Builder().build().also {
        it.surfaceProvider = previewView.surfaceProvider
      }

      // Set up ML Kit for QR codes only
      val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()

      val barcodeScanner = BarcodeScanning.getClient(options)
      val cameraExecutor = Executors.newSingleThreadExecutor()

      val analyzer = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also {
          it.setAnalyzer(cameraExecutor) { imageProxy ->
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
                    // Matter QR codes start with "MT:" or with "GQR:"
                    if (rawValue != null && (rawValue.startsWith("MT:") || rawValue.startsWith("GQR:"))) {
                      Log.i(TAG, "Matter QR Code Scanned: $rawValue")
                      onQrCodeScanned(rawValue)
                      // Unbind all use cases and stop the camera
                      cameraProvider.unbindAll()
                      it.clearAnalyzer()
                      return@addOnSuccessListener
                    }
                  }
                }
                .addOnFailureListener { e ->
                  Log.e(TAG, "Barcode scanning failed", e)
                }
                .addOnCompleteListener {
                  imageProxy.close()
                }
            }
          }
        }

      // Bind use cases to the camera life cycle
      val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
      try {
        // Must unbind prior to re-binding
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
          lifecycleOwner,
          cameraSelector,
          preview,
          analyzer
        )
      } catch (exc: Exception) {
        Log.e(TAG, "Use case binding failed", exc)
      }
    },
    ContextCompat.getMainExecutor(context)
  )
}