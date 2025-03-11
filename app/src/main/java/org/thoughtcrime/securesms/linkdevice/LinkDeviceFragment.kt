package org.thoughtcrime.securesms.linkdevice

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Dividers
import org.signal.core.ui.DropdownMenus
import org.signal.core.ui.Previews
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BiometricDeviceAuthentication
import org.thoughtcrime.securesms.BiometricDeviceLockContract
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.linkdevice.LinkDeviceSettingsState.DialogState
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.SupportEmailUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import java.util.Locale

private const val PLACEHOLDER = "__ICON_PLACEHOLDER__"

/**
 * Fragment that shows current linked devices
 */
class LinkDeviceFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(LinkDeviceFragment::class)
  }

  private val viewModel: LinkDeviceViewModel by activityViewModels()
  private lateinit var biometricAuth: BiometricDeviceAuthentication
  private lateinit var biometricDeviceLockLauncher: ActivityResultLauncher<String>
  private lateinit var linkDeviceWakeLock: LinkDeviceWakeLock

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewModel.initialize()

    biometricDeviceLockLauncher = registerForActivityResult(BiometricDeviceLockContract()) { result: Int ->
      if (result == BiometricDeviceAuthentication.AUTHENTICATED) {
        findNavController().safeNavigate(R.id.action_linkDeviceFragment_to_addLinkDeviceFragment)
      }
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setAllowedAuthenticators(BiometricDeviceAuthentication.ALLOWED_AUTHENTICATORS)
      .setTitle(requireContext().getString(R.string.LinkDeviceFragment__unlock_to_link))
      .setConfirmationRequired(true)
      .build()
    biometricAuth = BiometricDeviceAuthentication(
      BiometricManager.from(requireActivity()),
      BiometricPrompt(requireActivity(), BiometricAuthenticationListener()),
      promptInfo
    )

    linkDeviceWakeLock = LinkDeviceWakeLock(requireActivity())
  }

  override fun onPause() {
    super.onPause()
    biometricAuth.cancelAuthentication()
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController: NavController by remember { mutableStateOf(findNavController()) }
    val context = LocalContext.current

    LaunchedEffect(state.dialogState) {
      when (state.dialogState) {
        DialogState.None, is DialogState.SyncingFailed, DialogState.SyncingTimedOut -> {
          Log.i(TAG, "Releasing wake lock for linked device")
          linkDeviceWakeLock.release()
        }
        is DialogState.SyncingMessages, DialogState.Linking -> {
          Log.i(TAG, "Acquiring wake lock for linked device")
          linkDeviceWakeLock.acquire()
        }
        DialogState.Unlinking, is DialogState.DeviceUnlinked, DialogState.ContactSupport, DialogState.LoadingDebugLog -> Unit
      }
    }

    LaunchedEffect(state.oneTimeEvent) {
      when (val event = state.oneTimeEvent) {
        LinkDeviceSettingsState.OneTimeEvent.None -> {
          Unit
        }
        is LinkDeviceSettingsState.OneTimeEvent.ToastLinked -> {
          Toast.makeText(context, context.getString(R.string.LinkDeviceFragment__s_linked, event.name), Toast.LENGTH_LONG).show()
        }
        is LinkDeviceSettingsState.OneTimeEvent.ToastUnlinked -> {
          Toast.makeText(context, context.getString(R.string.LinkDeviceFragment__s_unlinked, event.name), Toast.LENGTH_LONG).show()
        }
        LinkDeviceSettingsState.OneTimeEvent.SnackbarLinkCancelled -> {
          Snackbar.make(requireView(), context.getString(R.string.LinkDeviceFragment__linking_cancelled), Snackbar.LENGTH_LONG).show()
        }
        LinkDeviceSettingsState.OneTimeEvent.ToastNetworkFailed -> {
          Toast.makeText(context, context.getString(R.string.DeviceListActivity_network_failed), Toast.LENGTH_LONG).show()
        }
        LinkDeviceSettingsState.OneTimeEvent.LaunchQrCodeScanner -> {
          navController.navigateToQrScannerIfAuthed(state.seenBioAuthEducationSheet)
        }
        LinkDeviceSettingsState.OneTimeEvent.ShowFinishedSheet -> {
          navController.safeNavigate(R.id.action_linkDeviceFragment_to_linkDeviceFinishedSheet)
        }
        LinkDeviceSettingsState.OneTimeEvent.HideFinishedSheet -> {
          if (navController.currentDestination?.id == R.id.linkDeviceFinishedSheet) {
            navController.popBackStack()
          }
        }
        LinkDeviceSettingsState.OneTimeEvent.SnackbarNameChangeFailure -> Unit
        LinkDeviceSettingsState.OneTimeEvent.SnackbarNameChangeSuccess -> Unit
        LinkDeviceSettingsState.OneTimeEvent.LaunchEmail -> {
          val subject = getString(R.string.LinkDeviceFragment__link_sync_failure_support_email)
          val body = getEmailBody(state.debugLogUrl)
          CommunicationActions.openEmail(requireContext(), SupportEmailUtil.getSupportEmailAddress(requireContext()), subject, body)
        }
      }

      if (state.oneTimeEvent != LinkDeviceSettingsState.OneTimeEvent.None) {
        viewModel.clearOneTimeEvent()
      }
    }

    LaunchedEffect(state.seenBioAuthEducationSheet) {
      if (state.seenBioAuthEducationSheet) {
        if (!biometricAuth.authenticate(requireContext(), true) { biometricDeviceLockLauncher.launch(getString(R.string.LinkDeviceFragment__unlock_to_link)) }) {
          navController.safeNavigate(R.id.action_linkDeviceFragment_to_addLinkDeviceFragment)
        }
        viewModel.markBioAuthEducationSheetSeen(false)
      }
    }

    Scaffolds.Settings(
      title = stringResource(id = R.string.preferences__linked_devices),
      onNavigationClick = { navController.popOrFinish() },
      navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24),
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close)
    ) { contentPadding: PaddingValues ->
      DeviceListScreen(
        state = state,
        modifier = Modifier.padding(contentPadding),
        onLearnMoreClicked = { navController.safeNavigate(R.id.action_linkDeviceFragment_to_linkDeviceLearnMoreBottomSheet) },
        onLinkNewDeviceClicked = {
          viewModel.stopExistingPolling()
          navController.navigateToQrScannerIfAuthed(!state.needsBioAuthEducationSheet)
        },
        onDeviceSelectedForRemoval = { device -> viewModel.setDeviceToRemove(device) },
        onDeviceRemovalConfirmed = { device -> viewModel.removeDevice(device) },
        onSyncFailureRetryRequested = { viewModel.onSyncErrorRetryRequested() },
        onSyncFailureIgnored = { viewModel.onSyncErrorIgnored() },
        onSyncFailureLearnMore = { CommunicationActions.openBrowserLink(requireContext(), requireContext().getString(R.string.LinkDeviceFragment__learn_more_url)) },
        onSyncFailureContactSupport = { viewModel.onSyncErrorContactSupport() },
        onSyncCancelled = { viewModel.onSyncCancelled() },
        onEditDevice = { device ->
          viewModel.setDeviceToEdit(device)
          navController.safeNavigate(R.id.action_linkDeviceFragment_to_editDeviceNameFragment)
        },
        onDialogDismissed = { viewModel.onDialogDismissed() },
        onContactWithLogs = { viewModel.onContactSupport(includeLogs = true) },
        onContactWithoutLogs = { viewModel.onContactSupport(includeLogs = false) }
      )
    }
  }

  private fun getEmailBody(debugLog: String?): String {
    val filter = R.string.LinkDeviceFragment__link_sync_failure_support_email_filter
    val prefix = StringBuilder()
    if (debugLog != null) {
      prefix.append("\n")
      prefix.append(getString(R.string.HelpFragment__debug_log)).append(" ").append(debugLog).append("\n\n")
    }
    return SupportEmailUtil.generateSupportEmailBody(requireContext(), filter, prefix.toString(), null)
  }

  private fun NavController.navigateToQrScannerIfAuthed(seenEducation: Boolean) {
    if (seenEducation && biometricAuth.canAuthenticate(requireContext())) {
      if (!biometricAuth.authenticate(requireContext(), true) { biometricDeviceLockLauncher.launch(getString(R.string.LinkDeviceFragment__unlock_to_link)) }) {
        this.safeNavigate(R.id.action_linkDeviceFragment_to_addLinkDeviceFragment)
      }
    } else if (biometricAuth.canAuthenticate(requireContext())) {
      this.safeNavigate(R.id.action_linkDeviceFragment_to_linkDeviceEducationSheet)
    } else {
      this.safeNavigate(R.id.action_linkDeviceFragment_to_addLinkDeviceFragment)
    }
  }

  private fun NavController.popOrFinish() {
    if (!popBackStack()) {
      requireActivity().finishAfterTransition()
    }
  }

  private inner class BiometricAuthenticationListener : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationError(errorCode: Int, errorString: CharSequence) {
      Log.w(TAG, "Authentication error: $errorCode")
      if (errorCode == BiometricPrompt.ERROR_CANCELED) {
        biometricDeviceLockLauncher.launch(getString(R.string.LinkDeviceFragment__unlock_to_link))
      } else {
        onAuthenticationFailed()
      }
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
      Log.i(TAG, "Authentication succeeded")
      findNavController().safeNavigate(R.id.action_linkDeviceFragment_to_addLinkDeviceFragment)
    }

    override fun onAuthenticationFailed() {
      Log.w(TAG, "Unable to authenticate")
    }
  }
}

@Composable
fun DeviceListScreen(
  state: LinkDeviceSettingsState,
  modifier: Modifier = Modifier,
  onLearnMoreClicked: () -> Unit = {},
  onLinkNewDeviceClicked: () -> Unit = {},
  onDeviceSelectedForRemoval: (Device?) -> Unit = {},
  onDeviceRemovalConfirmed: (Device) -> Unit = {},
  onSyncFailureRetryRequested: () -> Unit = {},
  onSyncFailureIgnored: () -> Unit = {},
  onSyncFailureLearnMore: () -> Unit = {},
  onSyncFailureContactSupport: () -> Unit = {},
  onSyncCancelled: () -> Unit = {},
  onEditDevice: (Device) -> Unit = {},
  onDialogDismissed: () -> Unit = {},
  onContactWithLogs: () -> Unit = {},
  onContactWithoutLogs: () -> Unit = {}
) {
  // If a bottom sheet is showing, we don't want the spinner underneath
  if (!state.bottomSheetVisible) {
    when (state.dialogState) {
      DialogState.None -> {
        Unit
      }
      DialogState.Linking -> {
        Dialogs.IndeterminateProgressDialog(stringResource(id = R.string.LinkDeviceFragment__linking_device))
      }
      DialogState.Unlinking -> {
        Dialogs.IndeterminateProgressDialog(stringResource(id = R.string.DeviceListActivity_unlinking_device))
      }
      is DialogState.SyncingMessages -> {
        Dialogs.IndeterminateProgressDialog(
          message = stringResource(id = R.string.LinkDeviceFragment__syncing_messages),
          caption = stringResource(id = R.string.LinkDeviceFragment__do_not_close),
          dismiss = stringResource(id = android.R.string.cancel),
          onDismiss = onSyncCancelled
        )
      }
      is DialogState.SyncingFailed -> {
        if (state.dialogState.canRetry) {
          Dialogs.SimpleAlertDialog(
            title = stringResource(R.string.LinkDeviceFragment__sync_failure_title),
            body = stringResource(R.string.LinkDeviceFragment__sync_failure_body),
            confirm = stringResource(R.string.LinkDeviceFragment__sync_failure_retry_button),
            onConfirm = onSyncFailureRetryRequested,
            dismiss = stringResource(R.string.LinkDeviceFragment__sync_failure_dismiss_button),
            onDismissRequest = onSyncFailureIgnored,
            onDeny = onSyncFailureIgnored
          )
        } else {
          Dialogs.AdvancedAlertDialog(
            title = stringResource(R.string.LinkDeviceFragment__sync_failure_title),
            body = stringResource(R.string.LinkDeviceFragment__sync_failure_body_unretryable),
            positive = stringResource(R.string.LinkDeviceFragment__contact_support),
            onPositive = onSyncFailureContactSupport,
            neutral = stringResource(R.string.LinkDeviceFragment__learn_more),
            onNeutral = onSyncFailureLearnMore,
            negative = stringResource(R.string.LinkDeviceFragment__continue),
            onNegative = onSyncFailureIgnored
          )
        }
      }
      DialogState.SyncingTimedOut -> {
        Dialogs.SimpleAlertDialog(
          title = stringResource(R.string.LinkDeviceFragment__sync_failure_title),
          body = stringResource(R.string.LinkDeviceFragment__sync_failure_body),
          confirm = stringResource(R.string.LinkDeviceFragment__sync_failure_retry_button),
          onConfirm = onSyncFailureRetryRequested,
          dismiss = stringResource(R.string.LinkDeviceFragment__sync_failure_dismiss_button),
          onDismissRequest = onSyncFailureIgnored,
          onDeny = onSyncFailureIgnored
        )
      }
      is DialogState.DeviceUnlinked -> {
        val createdAt = DateUtils.getDateTimeString(LocalContext.current, Locale.getDefault(), state.dialogState.deviceCreatedAt)
        Dialogs.SimpleMessageDialog(
          title = stringResource(id = R.string.LinkDeviceFragment__device_unlinked),
          message = stringResource(id = R.string.LinkDeviceFragment__the_device_that_was, createdAt),
          dismiss = stringResource(id = R.string.LinkDeviceFragment__ok),
          onDismiss = onDialogDismissed
        )
      }
      DialogState.LoadingDebugLog -> { Dialogs.IndeterminateProgressDialog() }
      DialogState.ContactSupport -> {
        Dialogs.AdvancedAlertDialog(
          title = stringResource(R.string.LinkDeviceFragment__submit_debug_log),
          body = stringResource(R.string.LinkDeviceFragment__your_debug_logs),
          positive = stringResource(R.string.LinkDeviceFragment__submit_with_debug),
          onPositive = onContactWithLogs,
          neutral = stringResource(R.string.LinkDeviceFragment__submit_without_debug),
          onNeutral = onContactWithoutLogs,
          negative = stringResource(R.string.LinkDeviceFragment__cancel),
          onNegative = onDialogDismissed
        )
      }
    }
  }

  if (state.deviceToRemove != null) {
    val device: Device = state.deviceToRemove
    val name = if (device.name.isNullOrEmpty()) stringResource(R.string.DeviceListItem_unnamed_device) else device.name
    Dialogs.SimpleAlertDialog(
      title = stringResource(id = R.string.DeviceListActivity_unlink_s, name),
      body = stringResource(id = R.string.DeviceListActivity_by_unlinking_this_device_it_will_no_longer_be_able_to_send_or_receive),
      confirm = stringResource(R.string.LinkDeviceFragment__unlink),
      dismiss = stringResource(android.R.string.cancel),
      onConfirm = { onDeviceRemovalConfirmed(device) },
      onDismiss = { onDeviceSelectedForRemoval(null) }
    )
  }

  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.verticalScroll(rememberScrollState())) {
    Icon(
      painter = painterResource(R.drawable.ic_devices_intro),
      contentDescription = stringResource(R.string.preferences__linked_devices),
      tint = Color.Unspecified
    )
    Text(
      text = stringResource(id = R.string.LinkDeviceFragment__use_signal_on_desktop_ipad),
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)
    )
    ClickableText(
      text = AnnotatedString(stringResource(id = R.string.LearnMoreTextView_learn_more)),
      style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary)
    ) {
      onLearnMoreClicked()
    }

    Spacer(modifier = Modifier.size(20.dp))

    Buttons.LargeTonal(
      onClick = onLinkNewDeviceClicked,
      modifier = Modifier
        .defaultMinSize(300.dp)
        .padding(bottom = 8.dp)
    ) {
      Text(stringResource(id = R.string.LinkDeviceFragment__link_a_new_device))
    }

    Dividers.Default()

    Column(modifier = Modifier.fillMaxWidth()) {
      Text(
        text = stringResource(R.string.LinkDeviceFragment__my_linked_devices),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 12.dp)
      )
      if (state.deviceListLoading) {
        Spacer(modifier = Modifier.size(30.dp))
        CircularProgressIndicator(
          modifier = Modifier
            .size(36.dp)
            .align(Alignment.CenterHorizontally),
          color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.size(30.dp))
      } else if (state.devices.isEmpty()) {
        Text(
          text = stringResource(R.string.LinkDeviceFragment__no_linked_devices),
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 96.dp)
            .wrapContentHeight(align = Alignment.CenterVertically)
        )
      } else {
        state.devices.forEach { device ->
          DeviceRow(device, onDeviceSelectedForRemoval, onEditDevice)
        }
      }
    }

    Row(
      modifier = Modifier.padding(horizontal = 40.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      val message = stringResource(id = R.string.LinkDeviceFragment__messages_and_chat_info_are_protected, PLACEHOLDER)
      val (messageText, messageInline) = remember(message) {
        val parts = message.split(PLACEHOLDER)
        val annotatedString = buildAnnotatedString {
          append(parts[0])
          appendInlineContent("icon")
          append(parts[1])
        }

        val inlineContentMap = mapOf(
          "icon" to InlineTextContent(Placeholder(16.sp, 16.sp, PlaceholderVerticalAlign.Center)) {
            Image(
              imageVector = ImageVector.vectorResource(id = R.drawable.symbol_lock_24),
              contentDescription = null,
              modifier = Modifier.fillMaxSize(),
              colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
            )
          }
        )

        annotatedString to inlineContentMap
      }

      Text(
        text = messageText,
        inlineContent = messageInline,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun DeviceRow(device: Device, setDeviceToRemove: (Device) -> Unit, onEditDevice: (Device) -> Unit) {
  val titleString = if (device.name.isNullOrEmpty()) stringResource(R.string.DeviceListItem_unnamed_device) else device.name
  val linkedDate = DateUtils.getDayPrecisionTimeSpanString(LocalContext.current, Locale.getDefault(), device.createdMillis)
  val lastActive = DateUtils.getDayPrecisionTimeSpanString(LocalContext.current, Locale.getDefault(), device.lastSeenMillis)
  val menuController = remember { DropdownMenus.MenuController() }
  Row(
    modifier = Modifier
      .fillMaxWidth()
  ) {
    Image(
      painter = painterResource(id = R.drawable.symbol_devices_24),
      contentDescription = null,
      colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
      contentScale = ContentScale.Inside,
      modifier = Modifier
        .padding(start = 24.dp, top = 28.dp, bottom = 28.dp)
        .size(40.dp)
        .background(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = CircleShape
        )
        .align(Alignment.CenterVertically)
    )

    Column(
      modifier = Modifier
        .align(Alignment.CenterVertically)
        .padding(start = 16.dp)
        .weight(1f)
    ) {
      Text(text = titleString, style = MaterialTheme.typography.bodyLarge)
      Text(stringResource(R.string.DeviceListItem_linked_s, linkedDate), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(stringResource(R.string.DeviceListItem_last_active_s, lastActive), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Box {
      Icon(
        painterResource(id = R.drawable.symbol_more_vertical),
        contentDescription = null,
        modifier = Modifier
          .padding(top = 16.dp, end = 16.dp)
          .clickable { menuController.show() }
      )

      DropdownMenus.Menu(controller = menuController, offsetX = 16.dp, offsetY = 4.dp) { controller ->
        DropdownMenus.Item(
          contentPadding = PaddingValues(0.dp),
          text = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
              Icon(
                painter = painterResource(id = R.drawable.symbol_link_slash_16),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.LinkDeviceFragment__unlink),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyLarge
              )
            }
          },
          onClick = {
            setDeviceToRemove(device)
            controller.hide()
          }
        )

        DropdownMenus.Item(
          contentPadding = PaddingValues(0.dp),
          text = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
              Icon(
                painter = painterResource(id = R.drawable.symbol_edit_24),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.LinkDeviceFragment__edit_name),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyLarge
              )
            }
          },
          onClick = {
            onEditDevice(device)
            controller.hide()
          }
        )
      }
    }
  }
}

@SignalPreview
@Composable
private fun DeviceListScreenPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        devices = listOf(
          Device(1, "Sam's Macbook Pro", 1715793982000, 1716053182000),
          Device(1, "Sam's iPad", 1715793182000, 1716053122000)
        ),
        seenQrEducationSheet = true,
        seenBioAuthEducationSheet = true
      )
    )
  }
}

@SignalPreview
@Composable
private fun DeviceListScreenLoadingPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        deviceListLoading = true,
        seenQrEducationSheet = true,
        seenBioAuthEducationSheet = true
      )
    )
  }
}

@SignalPreview
@Composable
private fun DeviceListScreenLinkingPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.Linking,
        seenQrEducationSheet = true,
        seenBioAuthEducationSheet = true
      )
    )
  }
}

@SignalPreview
@Composable
private fun DeviceListScreenUnlinkingPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.Unlinking,
        seenQrEducationSheet = true,
        seenBioAuthEducationSheet = true
      )
    )
  }
}

@SignalPreview
@Composable
private fun DeviceListScreenSyncingMessagesPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.SyncingMessages(1, 1),
        seenQrEducationSheet = true,
        seenBioAuthEducationSheet = true
      )
    )
  }
}

@SignalPreview
@Composable
private fun DeviceListScreenSyncingFailedRetryPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.SyncingTimedOut,
        seenQrEducationSheet = true,
        seenBioAuthEducationSheet = true
      )
    )
  }
}

@SignalPreview
@Composable
private fun DeviceListScreenSyncingFailedPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.SyncingFailed(1, 1, false),
        seenQrEducationSheet = true,
        seenBioAuthEducationSheet = true
      )
    )
  }
}

@SignalPreview
@Composable
private fun DeviceListScreenContactSupportPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.ContactSupport,
        seenQrEducationSheet = true,
        seenBioAuthEducationSheet = true
      )
    )
  }
}

@SignalPreview
@Composable
private fun DeviceListScreenDeviceUnlinkedPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.DeviceUnlinked(1736454440342),
        seenBioAuthEducationSheet = true,
        seenQrEducationSheet = true
      )
    )
  }
}
