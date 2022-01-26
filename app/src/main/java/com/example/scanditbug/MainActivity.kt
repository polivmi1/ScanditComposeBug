package com.example.scanditbug

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.scanditbug.ui.theme.ScanditBugTheme
import com.scandit.datacapture.barcode.capture.BarcodeCapture
import com.scandit.datacapture.barcode.capture.BarcodeCaptureListener
import com.scandit.datacapture.barcode.capture.BarcodeCaptureSession
import com.scandit.datacapture.barcode.capture.BarcodeCaptureSettings
import com.scandit.datacapture.barcode.data.Symbology
import com.scandit.datacapture.core.capture.DataCaptureContext
import com.scandit.datacapture.core.capture.DataCaptureContextListener
import com.scandit.datacapture.core.common.ContextStatus
import com.scandit.datacapture.core.data.FrameData
import com.scandit.datacapture.core.source.Camera
import com.scandit.datacapture.core.source.FrameSourceState
import com.scandit.datacapture.core.time.TimeInterval
import com.scandit.datacapture.core.ui.DataCaptureView
import com.scandit.datacapture.core.ui.control.TorchSwitchControl
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionRequired
import com.google.accompanist.permissions.rememberPermissionState
import com.scandit.datacapture.core.extensions.Callback
import com.scandit.datacapture.core.source.TorchState

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScanditBugTheme {
                var scanditOn by remember { mutableStateOf(false) }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

                    PermissionRequired(
                        permissionState = cameraPermissionState,
                        permissionNotGrantedContent = {
                            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                                Text("Grant permission")
                            }
                        },
                        permissionNotAvailableContent = {
                            Text("Change in settings")
                        }
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            Button(onClick = { scanditOn = !scanditOn }) {
                                Text(text = "Switch")
                            }
                            if (scanditOn) {
                                //TODO: enter your scandit key
                                BarcodesView(scanditLicence = "")
                            } else {
                                CameraOther()
                            }
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BarcodesView(
    scanditLicence: String,
) {
    var camera: Camera? by remember { mutableStateOf(null) }
    val barcodeCaptureListener: BarcodeCaptureListener by remember {
        mutableStateOf(object : BarcodeCaptureListener {
            override fun onBarcodeScanned(
                barcodeCapture: BarcodeCapture,
                session: BarcodeCaptureSession,
                data: FrameData
            ) {

            }
        })
    }
    val scanditListener: DataCaptureContextListener by remember {
        mutableStateOf(object : DataCaptureContextListener {
            override fun onStatusChanged(dataCaptureContext: DataCaptureContext, contextStatus: ContextStatus) {
                super.onStatusChanged(dataCaptureContext, contextStatus)
            }
        })
    }
    var barcodeCapture: BarcodeCapture? by remember { mutableStateOf(null) }
    val dataCaptureContext = remember { DataCaptureContext.forLicenseKey(scanditLicence) }
    val coroutineScope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleObserver = remember {
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                }
                Lifecycle.Event.ON_START -> {
                }
                Lifecycle.Event.ON_RESUME -> {
                    barcodeCapture?.isEnabled = true
                    camera?.switchToDesiredState(FrameSourceState.ON, null)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    barcodeCapture?.isEnabled = false
                    camera?.switchToDesiredState(FrameSourceState.OFF, null)
                }
                Lifecycle.Event.ON_STOP -> {
                }
                Lifecycle.Event.ON_DESTROY -> {
                    //handled in onDispose
                }
                else -> throw IllegalStateException()
            }
        }
    }
    DisposableEffect(lifecycle) {
        lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
            barcodeCapture?.removeListener(barcodeCaptureListener)
            dataCaptureContext.removeListener(scanditListener)
            dataCaptureContext.removeAllModes()
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberBottomSheetState(BottomSheetValue.Collapsed)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .matchParentSize(),
            factory = { context ->
                dataCaptureContext.addListener(scanditListener)
                val dataCaptureView = DataCaptureView.newInstance(context, dataCaptureContext)
                dataCaptureView.addControl(TorchSwitchControl(context))

                coroutineScope.launch {
                    camera = Camera.getDefaultCamera(BarcodeCapture.createRecommendedCameraSettings())
                    if (camera != null) {
                        //TODO: comment to fix the behavior
                        dataCaptureContext.setFrameSource(frameSource = camera)
                        //TODO: uncomment to fix the behavior
                        /*dataCaptureContext.setFrameSource(frameSource = camera, whenDone = {
                            barcodeCapture?.isEnabled = true
                            camera?.switchToDesiredState(FrameSourceState.OFF, whenDone = Callback {
                                camera?.switchToDesiredState(FrameSourceState.ON, whenDone = Callback {
                                })
                            })
                        })*/
                    } else {
                        throw IllegalStateException("Sample depends on a camera, which failed to initialize.")
                    }

                    val settings = BarcodeCaptureSettings()
                    settings.codeDuplicateFilter = TimeInterval.millis(5000)
                    settings.enableSymbology(Symbology.QR, true)
                    settings.enableSymbology(Symbology.DATA_MATRIX, true)
                    settings.enableSymbology(Symbology.CODE39, true)
                    val symSettings = settings.getSymbologySettings(Symbology.CODE39)
                    val set: Set<Short> =
                        HashSet(listOf(7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24))
                    symSettings.activeSymbolCounts = set
                    settings.enableSymbology(Symbology.CODE128, true)
                    settings.enableSymbology(Symbology.EAN13_UPCA, true)
                    settings.enableSymbology(Symbology.INTERLEAVED_TWO_OF_FIVE, true)
                    settings.enableSymbology(Symbology.UPCE, true)
                    settings.enableSymbology(Symbology.EAN8, true)
                    settings.enableSymbology(Symbology.CODE93, true)
                    settings.enableSymbology(Symbology.GS1_DATABAR, true)
                    settings.enableSymbology(Symbology.GS1_DATABAR_LIMITED, true)
                    settings.enableSymbology(Symbology.GS1_DATABAR_EXPANDED, true)
                    settings.enableSymbology(Symbology.MICRO_QR, true)
                    settings.enableSymbology(Symbology.MSI_PLESSEY, true)
                    settings.enableSymbology(Symbology.PDF417, true)
                    settings.enableSymbology(Symbology.CODABAR, true)
                    settings.enableSymbology(Symbology.DOT_CODE, true)
                    settings.enableSymbology(Symbology.MICRO_PDF417, true)
                    settings.enableSymbology(Symbology.CODE11, true)
                    settings.enableSymbology(Symbology.AZTEC, true)
                    settings.enableSymbology(Symbology.MAXI_CODE, true)
                    barcodeCapture = BarcodeCapture.forDataCaptureContext(dataCaptureContext, settings)
                    barcodeCapture?.addListener(barcodeCaptureListener)
                }
                dataCaptureView
            }, update = {
                barcodeCapture?.isEnabled = true
                camera?.switchToDesiredState(FrameSourceState.ON, null)
            })
    }
}

@Composable
fun CameraOther() {
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var camera: androidx.camera.core.Camera? by remember { mutableStateOf(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { context ->
                val previewView = PreviewView(context)
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                coroutineScope.launch {
                    val cameraProvider = context.getCameraProvider()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    cameraProvider.unbindAll()
                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                    )
                }
                previewView
            },
        )
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleObserver = remember {
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                }
                Lifecycle.Event.ON_START -> {
                }
                Lifecycle.Event.ON_RESUME -> {
                }
                Lifecycle.Event.ON_PAUSE -> {
                }
                Lifecycle.Event.ON_STOP -> {
                }
                Lifecycle.Event.ON_DESTROY -> {
                    //handled in onDispose
                }
                else -> throw IllegalStateException()
            }
        }
    }
    DisposableEffect(lifecycle) {
        lifecycle.addObserver(lifecycleObserver)
        onDispose {
        }
    }
}

suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    ProcessCameraProvider.getInstance(this).also { future ->
        future.addListener({
            continuation.resume(future.get())
        }, executor)
    }
}

val Context.executor: Executor
    get() = ContextCompat.getMainExecutor(this)