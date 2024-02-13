@file:OptIn(ExperimentalMaterial3Api::class)

package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.CoroutineScope
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Snackbars
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.main.UsernameLinkSettingsState.ActiveTab
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.providers.BlobProvider
import java.io.ByteArrayOutputStream
import java.util.UUID

@OptIn(ExperimentalPermissionsApi::class)
class UsernameLinkSettingsFragment : ComposeFragment() {

  private val viewModel: UsernameLinkSettingsViewModel by viewModels()
  private val disposables: LifecycleDisposable = LifecycleDisposable()

  override fun onStart() {
    super.onStart()
    setFragmentResultListener(UsernameLinkShareBottomSheet.REQUEST_KEY) { key, bundle ->
      if (bundle.getBoolean(UsernameLinkShareBottomSheet.KEY_COPY)) {
        viewModel.onLinkCopied()
      }
    }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun FragmentContent() {
    val state by viewModel.state
    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    val scope: CoroutineScope = rememberCoroutineScope()
    val navController: NavController by remember { mutableStateOf(findNavController()) }
    var showResetDialog: Boolean by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val cameraPermissionState: PermissionState = rememberPermissionState(permission = android.Manifest.permission.CAMERA) {
      viewModel.onTabSelected(ActiveTab.Scan)
    }
    val linkCopiedEvent: UUID? by viewModel.linkCopiedEvent

    val linkCopiedString = stringResource(R.string.UsernameLinkSettings_link_copied_toast)

    LaunchedEffect(linkCopiedEvent) {
      if (linkCopiedEvent != null) {
        snackbarHostState.showSnackbar(linkCopiedString)
      }
    }

    Scaffold(
      snackbarHost = { Snackbars.Host(snackbarHostState) },
      topBar = {
        TopAppBarContent(
          activeTab = state.activeTab,
          scrollBehavior = scrollBehavior,
          onCodeTabSelected = { viewModel.onTabSelected(ActiveTab.Code) },
          onScanTabSelected = { viewModel.onTabSelected(ActiveTab.Scan) },
          cameraPermissionState = cameraPermissionState
        )
      },
      modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { contentPadding ->

      if (state.indeterminateProgress) {
        Dialogs.IndeterminateProgressDialog()
      }

      AnimatedVisibility(
        visible = state.activeTab == ActiveTab.Code,
        enter = slideInHorizontally(initialOffsetX = { fullWidth -> -fullWidth }),
        exit = slideOutHorizontally(targetOffsetX = { fullWidth -> -fullWidth })
      ) {
        val helpText = stringResource(id = R.string.UsernameLinkSettings_scan_this_qr_code)
        UsernameLinkShareScreen(
          state = state,
          snackbarHostState = snackbarHostState,
          scope = scope,
          modifier = Modifier.padding(contentPadding),
          navController = navController,
          onShareBadge = {
            shareQrBadge(viewModel.generateQrCodeImage(helpText))
          },
          onResetClicked = { showResetDialog = true },
          onLinkResultHandled = { viewModel.onUsernameLinkResetResultHandled() }
        )
      }

      AnimatedVisibility(
        visible = state.activeTab == ActiveTab.Scan,
        enter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }),
        exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth })
      ) {
        UsernameQrScanScreen(
          lifecycleOwner = viewLifecycleOwner,
          disposables = disposables.disposables,
          qrScanResult = state.qrScanResult,
          onQrCodeScanned = { data -> viewModel.onQrCodeScanned(data) },
          onQrResultHandled = { viewModel.onQrResultHandled() },
          modifier = Modifier.padding(contentPadding)
        )
      }
    }

    if (showResetDialog) {
      ResetDialog(
        onConfirm = {
          viewModel.onUsernameLinkReset()
          showResetDialog = false
        },
        onDismiss = { showResetDialog = false }
      )
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    disposables.bindTo(viewLifecycleOwner)
  }

  override fun onResume() {
    super.onResume()
    viewModel.onResume()
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun TopAppBarContent(
    activeTab: ActiveTab,
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
    onCodeTabSelected: () -> Unit = {},
    onScanTabSelected: () -> Unit = {},
    cameraPermissionState: PermissionState = previewPermissionState()
  ) {
    CenterAlignedTopAppBar(
      modifier = Modifier
        .fillMaxWidth(),
      title = {
        Row(
          modifier = Modifier
            .fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          TabButton(
            label = stringResource(R.string.UsernameLinkSettings_code_tab_name),
            active = activeTab == ActiveTab.Code,
            onClick = onCodeTabSelected,
            modifier = Modifier.padding(end = 8.dp)
          )
          TabButton(
            label = stringResource(R.string.UsernameLinkSettings_scan_tab_name),
            active = activeTab == ActiveTab.Scan,
            onClick = {
              if (cameraPermissionState.status.isGranted) {
                onScanTabSelected()
              } else {
                cameraPermissionState.launchPermissionRequest()
              }
            },
            modifier = Modifier.padding(end = 8.dp)
          )
        }
      },
      navigationIcon = {
        IconButton(
          onClick = { requireActivity().onBackPressed() }
        ) {
          Icon(
            painter = painterResource(R.drawable.symbol_x_24),
            contentDescription = stringResource(android.R.string.cancel)
          )
        }
      },
      scrollBehavior = scrollBehavior
    )
  }

  @Composable
  private fun TabButton(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = if (active) {
      ButtonDefaults.filledTonalButtonColors()
    } else {
      ButtonDefaults.buttonColors(
        containerColor = SignalTheme.colors.colorSurface2,
        contentColor = MaterialTheme.colorScheme.onSurface
      )
    }
    Buttons.Small(
      onClick = onClick,
      modifier = modifier.defaultMinSize(minWidth = 100.dp),
      shape = RoundedCornerShape(12.dp),
      colors = colors
    ) {
      Text(label)
    }
  }

  @Composable
  private fun ResetDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialogs.SimpleAlertDialog(
      title = stringResource(id = R.string.UsernameLinkSettings_reset_link_dialog_title),
      body = stringResource(id = R.string.UsernameLinkSettings_reset_link_dialog_body),
      confirm = stringResource(id = R.string.UsernameLinkSettings_reset_link_dialog_confirm_button),
      dismiss = stringResource(id = android.R.string.cancel),
      onConfirm = onConfirm,
      onDismiss = onDismiss
    )
  }

  @Preview
  @Composable
  private fun PreviewAppBar() {
    SignalTheme {
      Surface {
        TopAppBarContent(activeTab = ActiveTab.Code)
      }
    }
  }

  @Preview(name = "Light Theme", uiMode = Configuration.UI_MODE_NIGHT_NO)
  @Preview(name = "Dark Theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
  @Composable
  private fun PreviewAll() {
    FragmentContent()
  }

  @Preview
  @Composable
  private fun PreviewResetDialog() {
    SignalTheme {
      Surface {
        ResetDialog(onConfirm = {}, onDismiss = {})
      }
    }
  }

  private fun previewPermissionState(): PermissionState {
    return object : PermissionState {
      override val permission: String = ""
      override val status: PermissionStatus = PermissionStatus.Granted
      override fun launchPermissionRequest() = Unit
    }
  }

  private fun shareQrBadge(badge: Bitmap?) {
    if (badge == null) {
      return
    }

    try {
      ByteArrayOutputStream().use { byteArrayOutputStream ->
        badge.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        byteArrayOutputStream.flush()
        val bytes = byteArrayOutputStream.toByteArray()
        val shareUri = BlobProvider.getInstance()
          .forData(bytes)
          .withMimeType("image/png")
          .withFileName("SignalGroupQr.png")
          .createForSingleSessionInMemory()

        val intent = ShareCompat.IntentBuilder.from(requireActivity())
          .setType("image/png")
          .setStream(shareUri)
          .createChooserIntent()
          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        startActivity(intent)
      }
    } finally {
      badge.recycle()
    }
  }
}
