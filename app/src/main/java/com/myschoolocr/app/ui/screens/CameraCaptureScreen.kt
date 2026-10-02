package com.myschoolocr.app.ui.screens

import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors

/**
 * Écran plein écran avec aperçu caméra + bouton de capture.
 * Retourne le Bitmap capturé (redressé) via onCaptured.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraCaptureScreen(
    title: String,
    onCaptured: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }

    if (!cameraPermission.status.isGranted) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("L'accès à la caméra est nécessaire pour scanner.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = { cameraPermission.launchPermissionRequest() }) { Text("Autoriser la caméra") }
            TextButton(onClick = onCancel) { Text("Annuler") }
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var captureError by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val selector = CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        Text(
            title,
            color = Color_White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        )

        captureError?.let { msg ->
            Text(
                msg,
                color = Color_White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp)
            )
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = onCancel) { Text("Annuler") }
            FloatingActionButton(onClick = {
                captureError = null
                imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        // Toute erreur ici (décodage raté, image nulle, OOM...) ne doit
                        // JAMAIS crasher l'app : on affiche un message et on laisse
                        // reprendre une photo plutôt que de laisser planter tout le process.
                        try {
                            val bitmap = imageProxyToBitmap(image)
                            image.close()
                            if (bitmap != null) {
                                onCaptured(bitmap)
                            } else {
                                captureError = "Échec de la capture, réessaie."
                            }
                        } catch (e: Exception) {
                            captureError = "Erreur lors de la capture (${e.javaClass.simpleName}), réessaie."
                            try { image.close() } catch (_: Exception) {}
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        exception.printStackTrace()
                        captureError = "Erreur caméra, réessaie."
                    }
                })
            }) {
                Icon(Icons.Filled.Camera, contentDescription = "Capturer")
            }
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    var bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rotation = image.imageInfo.rotationDegrees
    if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    return bitmap
}

// Petit alias pour éviter un import Color en double avec Material3 dans ce fichier
private val Color_White = androidx.compose.ui.graphics.Color.White
