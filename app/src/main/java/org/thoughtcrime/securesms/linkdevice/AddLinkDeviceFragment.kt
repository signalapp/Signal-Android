package org.thoughtcrime.securesms.linkdevice

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import org.signal.core.ui.Previews
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BiometricDeviceAuthentication
import org.thoughtcrime.securesms.BiometricDeviceLockContract
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.main.QrImageSelectionActivity
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Fragment that allows users to scan a QR code from their camera to link a device
 */
class AddLinkDeviceFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(AddLinkDeviceFragment::class)
  }

  private val viewModel: LinkDeviceViewModel by activityViewModels()
  private lateinit var biometricAuth: BiometricDeviceAuthentication
  private lateinit var biometricDeviceLockLauncher: ActivityResultLauncher<String>
  private lateinit var galleryLauncher: ActivityResultLauncher<Unit>

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    biometricDeviceLockLauncher = registerForActivityResult(BiometricDeviceLockContract()) { result: Int ->
      if (result == BiometricDeviceAuthentication.AUTHENTICATED) {
        viewModel.addDevice()
      } else {
        viewModel.clearBiometrics()
      }
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setAllowedAuthenticators(BiometricDeviceAuthentication.ALLOWED_AUTHENTICATORS)
      .setTitle(requireContext().getString(R.string.BiometricDeviceAuthentication__signal))
      .setConfirmationRequired(true)
      .build()
    biometricAuth = BiometricDeviceAuthentication(
      BiometricManager.from(requireActivity()),
      BiometricPrompt(requireActivity(), BiometricAuthenticationListener()),
      promptInfo
    )

    galleryLauncher = registerForActivityResult(QrImageSelectionActivity.Contract()) { uri ->
      if (uri != null) {
        viewModel.scanImage(requireContext(), uri)
      }
    }
  }

  override fun onPause() {
    super.onPause()
    viewModel.clearBiometrics()
    biometricAuth.cancelAuthentication()
  }

  @OptIn(ExperimentalPermissionsApi::class)
  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()
    val navController: NavController by remember { mutableStateOf(findNavController()) }
    val cameraPermissionState: PermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    if (!state.seenIntroSheet) {
      navController.safeNavigate(R.id.action_addLinkDeviceFragment_to_linkDeviceIntroBottomSheet)
      viewModel.markIntroSheetSeen()
    }

    if ((state.qrCodeFound || state.qrCodeInvalid) && navController.currentDestination?.id == R.id.linkDeviceIntroBottomSheet) {
      navController.popBackStack()
    }

    MainScreen(
      state = state,
      navController = navController,
      hasPermissions = cameraPermissionState.status.isGranted,
      onRequestPermissions = { askPermissions() },
      onShowFrontCamera = { viewModel.showFrontCamera() },
      onQrCodeScanned = { data -> viewModel.onQrCodeScanned(data) },
      onQrCodeApproved = {
        viewModel.onQrCodeApproved()
        biometricAuth.authenticate(requireContext(), true) { biometricDeviceLockLauncher.launch(getString(R.string.BiometricDeviceAuthentication__signal)) }
      },
      onQrCodeDismissed = { viewModel.onQrCodeDismissed() },
      onQrCodeRetry = { viewModel.onQrCodeScanned(state.url) },
      onLinkDeviceSuccess = {
        viewModel.onLinkDeviceResult(true)
        navController.popBackStack()
      },
      onLinkDeviceFailure = { viewModel.onLinkDeviceResult(false) },
      onGalleryOpened = { galleryLauncher.launch(Unit) }
    )
  }

  private fun askPermissions() {
    Permissions.with(this)
      .request(Manifest.permission.CAMERA)
      .ifNecessary()
      .withPermanentDenialDialog(getString(R.string.CameraXFragment_signal_needs_camera_access_scan_qr_code), null, R.string.CameraXFragment_allow_access_camera, R.string.CameraXFragment_to_scan_qr_codes, parentFragmentManager)
      .onAnyDenied { Toast.makeText(requireContext(), R.string.CameraXFragment_signal_needs_camera_access_scan_qr_code, Toast.LENGTH_LONG).show() }
      .execute()
  }

  @SuppressLint("MissingSuperCall")
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  private inner class BiometricAuthenticationListener : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationError(errorCode: Int, errorString: CharSequence) {
      Log.w(TAG, "Linked device authentication error: $errorCode")
      viewModel.clearBiometrics()
      onAuthenticationFailed()
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
      Log.i(TAG, "Linked device authentication succeeded")
      viewModel.addDevice()
    }

    override fun onAuthenticationFailed() {
      Log.w(TAG, "Linked device unable to authenticate")
    }
  }
}

@Composable
private fun MainScreen(
  state: LinkDeviceSettingsState,
  navController: NavController? = null,
  hasPermissions: Boolean = false,
  onRequestPermissions: () -> Unit = {},
  onShowFrontCamera: () -> Unit = {},
  onQrCodeScanned: (String) -> Unit = {},
  onQrCodeApproved: () -> Unit = {},
  onQrCodeDismissed: () -> Unit = {},
  onQrCodeRetry: () -> Unit = {},
  onLinkDeviceSuccess: () -> Unit = {},
  onLinkDeviceFailure: () -> Unit = {},
  onGalleryOpened: () -> Unit = {}
) {
  Scaffolds.Settings(
    title = "",
    onNavigationClick = { navController?.popBackStack() },
    navigationIconPainter = painterResource(id = R.drawable.ic_x),
    navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close),
    actions = {
      IconButton(onClick = { onShowFrontCamera() }) {
        Icon(painterResource(id = R.drawable.symbol_switch_24), contentDescription = null)
      }
    }
  ) { contentPadding: PaddingValues ->
    LinkDeviceQrScanScreen(
      hasPermission = hasPermissions,
      onRequestPermissions = onRequestPermissions,
      showFrontCamera = state.showFrontCamera,
      qrCodeFound = state.qrCodeFound,
      qrCodeInvalid = state.qrCodeInvalid,
      onQrCodeScanned = onQrCodeScanned,
      onQrCodeAccepted = onQrCodeApproved,
      onQrCodeDismissed = onQrCodeDismissed,
      onQrCodeRetry = onQrCodeRetry,
      pendingBiometrics = state.pendingBiometrics,
      linkDeviceResult = state.linkDeviceResult,
      onLinkDeviceSuccess = onLinkDeviceSuccess,
      onLinkDeviceFailure = onLinkDeviceFailure,
      onGalleryOpened = onGalleryOpened,
      modifier = Modifier.padding(contentPadding)
    )
  }
}

@SignalPreview
@Composable
private fun LinkDeviceAddScreenPreview() {
  Previews.Preview {
    MainScreen(
      state = LinkDeviceSettingsState()
    )
  }
}
