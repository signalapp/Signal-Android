@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)

package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.core.app.TaskStackBuilder
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Snackbars
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeData
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeState
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.UsernameQrCodeColorScheme
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.main.UsernameLinkSettingsState.ActiveTab
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.util.CommunicationActions
import java.io.ByteArrayOutputStream
import java.util.UUID

@OptIn(ExperimentalPermissionsApi::class)
class UsernameLinkSettingsFragment : ComposeFragment() {

  private val viewModel: UsernameLinkSettingsViewModel by viewModels()
  private val disposables: LifecycleDisposable = LifecycleDisposable()

  private lateinit var galleryLauncher: ActivityResultLauncher<Unit>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    galleryLauncher = registerForActivityResult(QrImageSelectionActivity.Contract()) { uri ->
      if (uri != null) {
        viewModel.scanImage(requireContext(), uri)
      }
    }
  }

  override fun onStart() {
    super.onStart()
    setFragmentResultListener(UsernameLinkShareBottomSheet.REQUEST_KEY) { key, bundle ->
      if (bundle.getBoolean(UsernameLinkShareBottomSheet.KEY_COPY)) {
        viewModel.onLinkCopied()
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state
    val navController: NavController by remember { mutableStateOf(findNavController()) }
    val linkCopiedEvent: UUID? by viewModel.linkCopiedEvent
    val helpText = stringResource(id = R.string.UsernameLinkSettings_scan_this_qr_code)

    val cameraPermissionState: PermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA) {
      viewModel.onTabSelected(ActiveTab.Scan)
    }

    MainScreen(
      state = state,
      navController = navController,
      lifecycleOwner = viewLifecycleOwner,
      disposables = disposables.disposables,
      cameraPermissionState = cameraPermissionState,
      onCodeTabSelected = { viewModel.onTabSelected(ActiveTab.Code) },
      onScanTabSelected = { viewModel.onTabSelected(ActiveTab.Scan) },
      onUsernameLinkResetResultHandled = { viewModel.onUsernameLinkResetResultHandled() },
      onShareBadge = { shareQrBadge(requireActivity(), viewModel.generateQrCodeImage(helpText)) },
      onQrCodeScanned = { data -> viewModel.onQrCodeScanned(data) },
      onQrResultHandled = { viewModel.onQrResultHandled() },
      onOpenCameraClicked = { askCameraPermissions() },
      onOpenGalleryClicked = { galleryLauncher.launch(Unit) },
      onLinkReset = { viewModel.onUsernameLinkReset() },
      onBackNavigationPressed = { requireActivity().onBackPressed() },
      linkCopiedEvent = linkCopiedEvent
    )
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    disposables.bindTo(viewLifecycleOwner)
  }

  override fun onResume() {
    super.onResume()
    viewModel.onResume()
  }

  private fun askCameraPermissions() {
    Permissions.with(this)
      .request(Manifest.permission.CAMERA)
      .ifNecessary()
      .withPermanentDenialDialog(getString(R.string.CameraXFragment_signal_needs_camera_access_scan_qr_code), null, R.string.CameraXFragment_allow_access_camera, R.string.CameraXFragment_to_scan_qr_codes, parentFragmentManager)
      .onAnyDenied { Toast.makeText(requireContext(), R.string.CameraXFragment_signal_needs_camera_access_scan_qr_code, Toast.LENGTH_LONG).show() }
      .execute()
  }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun MainScreen(
  state: UsernameLinkSettingsState,
  navController: NavController? = null,
  lifecycleOwner: LifecycleOwner = previewLifecycleOwner,
  disposables: CompositeDisposable = CompositeDisposable(),
  cameraPermissionState: PermissionState = previewPermissionState(),
  onCodeTabSelected: () -> Unit = {},
  onScanTabSelected: () -> Unit = {},
  onUsernameLinkResetResultHandled: () -> Unit = {},
  onShareBadge: () -> Unit = {},
  onQrCodeScanned: (String) -> Unit = {},
  onQrResultHandled: () -> Unit = {},
  onOpenCameraClicked: () -> Unit = {},
  onOpenGalleryClicked: () -> Unit = {},
  onLinkReset: () -> Unit = {},
  onBackNavigationPressed: () -> Unit = {},
  linkCopiedEvent: UUID? = null
) {
  val context = LocalContext.current

  val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
  val scope: CoroutineScope = rememberCoroutineScope()
  var showResetDialog: Boolean by remember { mutableStateOf(false) }
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

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
        onCodeTabSelected = onCodeTabSelected,
        onScanTabSelected = onScanTabSelected,
        onBackNavigationPressed = onBackNavigationPressed
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
      UsernameLinkShareScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        scope = scope,
        modifier = Modifier.padding(contentPadding),
        navController = navController,
        onShareBadge = onShareBadge,
        onResetClicked = { showResetDialog = true },
        onLinkResultHandled = onUsernameLinkResetResultHandled
      )
    }

    AnimatedVisibility(
      visible = state.activeTab == ActiveTab.Scan,
      enter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }),
      exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth })
    ) {
      UsernameQrScanScreen(
        lifecycleOwner = lifecycleOwner,
        disposables = disposables,
        qrScanResult = state.qrScanResult,
        onQrCodeScanned = onQrCodeScanned,
        onQrResultHandled = onQrResultHandled,
        onOpenCameraClicked = onOpenCameraClicked,
        onOpenGalleryClicked = onOpenGalleryClicked,
        hasCameraPermission = cameraPermissionState.status.isGranted,
        modifier = Modifier.padding(contentPadding),
        onRecipientFound = { recipient ->
          val taskStack = TaskStackBuilder
            .create(context)
            .addNextIntent(MainActivity.clearTop(context))

          CommunicationActions.startConversation(context, recipient, null, taskStack)
        }
      )
    }
  }

  if (showResetDialog) {
    ResetDialog(
      onConfirm = {
        onLinkReset()
        showResetDialog = false
      },
      onDismiss = { showResetDialog = false }
    )
  }
}

@Composable
private fun TopAppBarContent(
  activeTab: ActiveTab,
  scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
  onCodeTabSelected: () -> Unit = {},
  onScanTabSelected: () -> Unit = {},
  onBackNavigationPressed: () -> Unit = {}
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
          onClick = { onScanTabSelected() },
          modifier = Modifier.padding(end = 8.dp)
        )
      }
    },
    navigationIcon = {
      IconButton(
        onClick = onBackNavigationPressed
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

@Preview(name = "Light Theme", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppBarPreview() {
  SignalTheme {
    Surface {
      Column {
        TopAppBarContent(activeTab = ActiveTab.Code)
        TopAppBarContent(activeTab = ActiveTab.Scan)
      }
    }
  }
}

@Preview(name = "Light Theme", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MainScreenPreview() {
  SignalTheme {
    MainScreen(
      state = UsernameLinkSettingsState(
        activeTab = ActiveTab.Code,
        username = "PeterParker.42",
        usernameLinkState = UsernameLinkState.Present("https://signal.org"),
        qrCodeState = QrCodeState.Present(QrCodeData.forData("PeterParker.42")),
        qrCodeColorScheme = UsernameQrCodeColorScheme.Orange
      )
    )
  }
}

@Preview(name = "Light Theme", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ResetDialogPreview() {
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

private val previewLifecycleOwner: LifecycleOwner = object : LifecycleOwner {
  override val lifecycle: Lifecycle
    get() = throw UnsupportedOperationException("Only for tests")
}

private fun shareQrBadge(activity: Activity, badge: Bitmap?) {
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
        .withFileName("SignalUsernameQr.png")
        .createForSingleSessionInMemory()

      val intent = ShareCompat.IntentBuilder(activity)
        .setType("image/png")
        .setStream(shareUri)
        .createChooserIntent()
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

      activity.startActivity(intent)
    }
  } finally {
    badge.recycle()
  }
}
