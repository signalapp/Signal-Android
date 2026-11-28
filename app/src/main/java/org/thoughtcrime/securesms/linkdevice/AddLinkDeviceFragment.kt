package org.thoughtcrime.securesms.linkdevice

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.camera.CameraScreenEvents
import org.signal.camera.CameraScreenState
import org.signal.camera.CameraScreenViewModel
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.PermissionController
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.VibrateUtil
import org.signal.core.ui.R as CoreUiR
import org.signal.mediasend.R as MediaSendR

/**
 * Fragment that allows users to scan a QR code from their camera to link a device
 */
class AddLinkDeviceFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter()

  @Composable
  override fun FragmentContent() {
    AddLinkDeviceEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }
}

private const val VIBRATE_DURATION_MS = 50
private const val INTRO_SHEET_TAG = "LinkDeviceIntroBottomSheet"
private const val SYNC_SHEET_TAG = "LinkDeviceSyncBottomSheet"

/**
 * Scans a linking QR code, wired to the view model the linking flow shares, leaving through [parentEventEmitter].
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AddLinkDeviceEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val context = LocalContext.current
  val fragmentManager = (requireNotNull(LocalActivity.current) as FragmentActivity).supportFragmentManager
  val lifecycleOwner = LocalLifecycleOwner.current
  val coroutineScope = rememberCoroutineScope()
  val viewModel = linkDeviceViewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val cameraViewModel: CameraScreenViewModel = viewModel { CameraScreenViewModel() }
  val cameraState by cameraViewModel.state
  val cameraPermissionState: PermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
  val cameraPermission = remember { PermissionController(Manifest.permission.CAMERA, permanentDenialMessage = MediaSendR.string.CameraXFragment_signal_needs_camera_access_scan_qr_code) }
  val cameraNeeded = stringResource(MediaSendR.string.CameraXFragment_signal_needs_camera_access_scan_qr_code)

  cameraPermission.Content()

  LaunchedEffect(state.seenQrEducationSheet) {
    if (!state.seenQrEducationSheet) {
      LinkDeviceIntroBottomSheet().show(fragmentManager, INTRO_SHEET_TAG)
      viewModel.markQrEducationSheetSeen()
    }
  }

  LaunchedEffect(state.qrCodeState) {
    if (state.qrCodeState != LinkDeviceSettingsState.QrCodeState.NONE) {
      (fragmentManager.findFragmentByTag(INTRO_SHEET_TAG) as? DialogFragment)?.dismissAllowingStateLoss()
    }

    if (state.qrCodeState == LinkDeviceSettingsState.QrCodeState.VALID_WITH_SYNC && fragmentManager.findFragmentByTag(SYNC_SHEET_TAG) == null) {
      LinkDeviceSyncBottomSheet().show(fragmentManager, SYNC_SHEET_TAG)
    }
  }

  DisposableEffect(fragmentManager, lifecycleOwner) {
    fragmentManager.setFragmentResultListener(LinkDeviceSyncBottomSheet.REQUEST_KEY, lifecycleOwner) { _, _ ->
      parentEventEmitter.navigateBack()
    }
    onDispose { fragmentManager.clearFragmentResultListener(LinkDeviceSyncBottomSheet.REQUEST_KEY) }
  }

  LaunchedEffect(cameraViewModel) {
    cameraViewModel.qrCodeDetected.collect { data ->
      if (VibrateUtil.isHapticFeedbackEnabled(context)) {
        VibrateUtil.vibrate(context, VIBRATE_DURATION_MS)
      }
      viewModel.onQrCodeScanned(data)
    }
  }

  MainScreen(
    state = state,
    cameraState = cameraState,
    cameraEmitter = cameraViewModel::onEvent,
    onNavigationClick = { parentEventEmitter.navigateBack() },
    hasPermissions = cameraPermissionState.status.isGranted,
    onRequestPermissions = {
      coroutineScope.launch {
        if (!cameraPermission.request()) {
          Toast.makeText(context, cameraNeeded, Toast.LENGTH_LONG).show()
        }
      }
    },
    onSwitchCamera = { cameraViewModel.onEvent(CameraScreenEvents.SwitchCamera(context)) },
    onQrCodeApproved = {
      parentEventEmitter.navigateBack()
      viewModel.addDevice(shouldSync = false)
    },
    onQrCodeDismissed = { viewModel.onQrCodeDismissed() },
    onLinkDeviceSuccess = {
      viewModel.onLinkDeviceResult(showSheet = true)
    },
    onLinkDeviceFailure = { viewModel.onLinkDeviceResult(showSheet = false) }
  )
}

@Composable
private fun MainScreen(
  state: LinkDeviceSettingsState,
  cameraState: CameraScreenState = CameraScreenState(),
  cameraEmitter: (CameraScreenEvents) -> Unit = {},
  onNavigationClick: () -> Unit = {},
  hasPermissions: Boolean = false,
  onRequestPermissions: () -> Unit = {},
  onSwitchCamera: () -> Unit = {},
  onQrCodeApproved: () -> Unit = {},
  onQrCodeDismissed: () -> Unit = {},
  onLinkDeviceSuccess: () -> Unit = {},
  onLinkDeviceFailure: () -> Unit = {}
) {
  Scaffolds.Settings(
    title = "",
    onNavigationClick = onNavigationClick,
    navigationIcon = ImageVector.vectorResource(id = R.drawable.ic_x),
    navigationContentDescription = stringResource(id = CoreUiR.string.CloseButton__content_description),
    actions = {
      IconButton(onClick = onSwitchCamera) {
        Icon(painterResource(id = R.drawable.symbol_switch_24), contentDescription = null)
      }
    }
  ) { contentPadding: PaddingValues ->
    LinkDeviceQrScanScreen(
      hasPermission = hasPermissions,
      onRequestPermissions = onRequestPermissions,
      cameraState = cameraState,
      cameraEmitter = cameraEmitter,
      qrCodeState = state.qrCodeState,
      onQrCodeAccepted = onQrCodeApproved,
      onQrCodeDismissed = onQrCodeDismissed,
      linkDeviceResult = state.linkDeviceResult,
      onLinkDeviceSuccess = onLinkDeviceSuccess,
      onLinkDeviceFailure = onLinkDeviceFailure,
      modifier = Modifier.padding(contentPadding)
    )
  }
}

@DayNightPreviews
@Composable
private fun LinkDeviceAddScreenPreview() {
  Previews.Preview {
    MainScreen(
      state = LinkDeviceSettingsState()
    )
  }
}
