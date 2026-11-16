package org.thoughtcrime.securesms.linkdevice

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.LocalActivity
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.core.ui.biometrics.rememberBiometricsAuthentication
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.util.logging.Log
import org.signal.remoteconfig.RemoteConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.linkdevice.LinkDeviceSettingsState.DialogState
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.SupportEmailUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.signal.core.ui.R as CoreUiR

private const val PLACEHOLDER = "__ICON_PLACEHOLDER__"
private const val FINISHED_SHEET_TAG = "LinkDeviceFinishedSheet"
private val TAG = Log.tag(LinkDeviceFragment::class)

/**
 * Fragment that shows current linked devices
 */
class LinkDeviceFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  @Composable
  override fun FragmentContent() {
    LinkDeviceEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      AppSettingsRoute.LinkDeviceRoute.Add -> findNavController().safeNavigate(R.id.action_linkDeviceFragment_to_addLinkDeviceFragment)
      AppSettingsRoute.LinkDeviceRoute.EditName -> findNavController().safeNavigate(R.id.action_linkDeviceFragment_to_editDeviceNameFragment)
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}

/**
 * The linked device list, wired to the view model it shares with the rest of the linking flow, leaving through
 * [parentEventEmitter]. Its sheets are shown on the activity's fragment manager, where the sheets find the same view model.
 */
@Composable
fun LinkDeviceEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val context = LocalContext.current
  val resources = LocalResources.current
  val view = LocalView.current
  val activity = requireNotNull(LocalActivity.current) as FragmentActivity
  val viewModel = linkDeviceViewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val atDeviceLimit = state.devices.size >= RemoteConfig.maxLinkedDevices
  val linkDeviceWakeLock = remember(activity) { LinkDeviceWakeLock(activity) }
  val learnMoreUrl = stringResource(R.string.LinkDeviceFragment__learn_more_url)
  val supportEmailSubject = stringResource(R.string.LinkDeviceFragment__link_sync_failure_support_email)
  val debugLogLabel = stringResource(R.string.HelpFragment__debug_log)
  val biometrics = rememberBiometricsAuthentication(
    promptTitle = stringResource(R.string.LinkDeviceFragment__unlock_to_link),
    educationSheetMessage = stringResource(R.string.LinkDeviceFragment__before_linking),
    fallbackToDeviceCredentialOnCancel = true
  )
  val openQrScanner = { biometrics.withBiometricsAuthentication { parentEventEmitter.navigateTo(AppSettingsRoute.LinkDeviceRoute.Add) } }

  LaunchedEffect(viewModel) {
    viewModel.initialize()
  }

  LifecycleResumeEffect(biometrics) {
    onPauseOrDispose { biometrics.cancelAuthentication() }
  }

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
        Toast.makeText(context, resources.getString(R.string.LinkDeviceFragment__s_linked, event.name), Toast.LENGTH_LONG).show()
      }
      is LinkDeviceSettingsState.OneTimeEvent.ToastUnlinked -> {
        Toast.makeText(context, resources.getString(R.string.LinkDeviceFragment__s_unlinked, event.name), Toast.LENGTH_LONG).show()
      }
      LinkDeviceSettingsState.OneTimeEvent.SnackbarLinkCancelled -> {
        Snackbar.make(view, resources.getString(R.string.LinkDeviceFragment__linking_cancelled), Snackbar.LENGTH_LONG).show()
      }
      LinkDeviceSettingsState.OneTimeEvent.SnackbarDeviceLimitReached -> {
        Snackbar.make(view, resources.getString(R.string.LinkDeviceFragment__you_can_only_have_d_linked_devices, RemoteConfig.maxLinkedDevices), Snackbar.LENGTH_LONG).show()
      }
      LinkDeviceSettingsState.OneTimeEvent.ToastNetworkFailed -> {
        Toast.makeText(context, resources.getString(R.string.DeviceListActivity_network_failed), Toast.LENGTH_LONG).show()
      }
      LinkDeviceSettingsState.OneTimeEvent.LaunchQrCodeScanner -> {
        openQrScanner()
      }
      LinkDeviceSettingsState.OneTimeEvent.ShowFinishedSheet -> {
        LinkDeviceFinishedSheet().show(activity.supportFragmentManager, FINISHED_SHEET_TAG)
      }
      LinkDeviceSettingsState.OneTimeEvent.HideFinishedSheet -> {
        (activity.supportFragmentManager.findFragmentByTag(FINISHED_SHEET_TAG) as? DialogFragment)?.dismissAllowingStateLoss()
      }
      LinkDeviceSettingsState.OneTimeEvent.SnackbarNameChangeFailure -> Unit
      LinkDeviceSettingsState.OneTimeEvent.SnackbarNameChangeSuccess -> Unit
      LinkDeviceSettingsState.OneTimeEvent.LaunchEmail -> {
        val body = getEmailBody(context, debugLogLabel, state.debugLogUrl)
        CommunicationActions.openEmail(context, SupportEmailUtil.getSupportEmailAddress(context), supportEmailSubject, body)
      }
    }

    if (state.oneTimeEvent != LinkDeviceSettingsState.OneTimeEvent.None) {
      viewModel.clearOneTimeEvent()
    }
  }

  Scaffolds.Settings(
    title = stringResource(id = R.string.preferences__linked_devices),
    onNavigationClick = { parentEventEmitter.navigateBack() },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(id = CoreUiR.string.CloseButton__content_description)
  ) { contentPadding: PaddingValues ->
    DeviceListScreen(
      state = state,
      modifier = Modifier.padding(contentPadding),
      atDeviceLimit = atDeviceLimit,
      onLearnMoreClicked = { LinkDeviceLearnMoreBottomSheetFragment().show(activity.supportFragmentManager, null) },
      onLinkNewDeviceClicked = {
        if (atDeviceLimit) {
          viewModel.onDeviceLimitReached()
        } else {
          viewModel.stopExistingPolling()
          openQrScanner()
        }
      },
      onDeviceSelectedForRemoval = { device -> viewModel.setDeviceToRemove(device) },
      onDeviceRemovalConfirmed = { device -> viewModel.removeDevice(device) },
      onSyncFailureRetryRequested = { viewModel.onSyncErrorRetryRequested() },
      onSyncFailureIgnored = { viewModel.onSyncErrorIgnored() },
      onSyncFailureLearnMore = { CommunicationActions.openBrowserLink(context, learnMoreUrl) },
      onSyncFailureContactSupport = { viewModel.onSyncErrorContactSupport() },
      onSyncCancelled = { viewModel.onSyncCancelled() },
      onEditDevice = { device ->
        viewModel.setDeviceToEdit(device)
        parentEventEmitter.navigateTo(AppSettingsRoute.LinkDeviceRoute.EditName)
      },
      onDialogDismissed = { viewModel.onDialogDismissed() },
      onContactWithLogs = { viewModel.onContactSupport(includeLogs = true) },
      onContactWithoutLogs = { viewModel.onContactSupport(includeLogs = false) }
    )
  }
}

/**
 * The view model the linked device screens and sheets share. Scoped to the activity, where the sheets' `activityViewModels()` find it.
 */
@Composable
internal fun linkDeviceViewModel(): LinkDeviceViewModel {
  return viewModel(viewModelStoreOwner = requireNotNull(LocalActivity.current) as FragmentActivity)
}

private fun getEmailBody(context: Context, debugLogLabel: String, debugLog: String?): String {
  val filter = R.string.LinkDeviceFragment__link_sync_failure_support_email_filter
  val prefix = StringBuilder()
  if (debugLog != null) {
    prefix.append("\n")
    prefix.append(debugLogLabel).append(" ").append(debugLog).append("\n\n")
  }
  return SupportEmailUtil.generateSupportEmailBody(context, filter, prefix.toString(), null)
}

@Composable
fun DeviceListScreen(
  state: LinkDeviceSettingsState,
  modifier: Modifier = Modifier,
  atDeviceLimit: Boolean = false,
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
        when (state.dialogState.syncFailType) {
          LinkDeviceSettingsState.SyncFailType.NOT_RETRYABLE -> {
            Dialogs.AdvancedAlertDialog(
              title = stringResource(R.string.LinkDeviceFragment__sync_failure_title),
              body = stringResource(R.string.LinkDeviceFragment__sync_failure_body_unretryable),
              positive = stringResource(R.string.LinkDeviceFragment__contact_support),
              onPositive = onSyncFailureContactSupport,
              neutral = stringResource(R.string.LinkDeviceFragment__learn_more),
              onNeutral = onSyncFailureLearnMore,
              negative = stringResource(CoreUiR.string.LinkDeviceFragment__continue),
              onNegative = onSyncFailureIgnored
            )
          }
          LinkDeviceSettingsState.SyncFailType.RETRYABLE -> {
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
          LinkDeviceSettingsState.SyncFailType.NOT_ENOUGH_SPACE -> {
            Dialogs.SimpleMessageDialog(
              message = stringResource(R.string.LinkDeviceFragment__you_dont_have_enough),
              dismiss = stringResource(id = R.string.LinkDeviceFragment__ok),
              onDismiss = onSyncFailureRetryRequested,
              title = stringResource(R.string.LinkDeviceFragment__not_enough_storage_space)
            )
          }
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
        val locale = LocalLocale.current.platformLocale
        val createdAt = DateUtils.getDateTimeString(LocalContext.current, locale, state.dialogState.deviceCreatedAt)
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
      text = AnnotatedString(stringResource(id = CoreUiR.string.LearnMoreTextView_learn_more)),
      style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary)
    ) {
      onLearnMoreClicked()
    }

    Spacer(modifier = Modifier.size(20.dp))

    val linkButtonColors = ButtonDefaults.filledTonalButtonColors()
    Buttons.LargeTonal(
      onClick = onLinkNewDeviceClicked,
      colors = if (atDeviceLimit) {
        linkButtonColors.copy(
          containerColor = linkButtonColors.disabledContainerColor,
          contentColor = linkButtonColors.disabledContentColor
        )
      } else {
        linkButtonColors
      },
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
          DeviceRow(device, state.isInternalUser, onDeviceSelectedForRemoval, onEditDevice)
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
              imageVector = SignalIcons.Lock.imageVector,
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
fun DeviceRow(device: Device, isInternalUser: Boolean, setDeviceToRemove: (Device) -> Unit, onEditDevice: (Device) -> Unit) {
  val titleString = if (device.name.isNullOrEmpty()) stringResource(R.string.DeviceListItem_unnamed_device) else device.name
  val linkedDate = device.createdMillis?.let { DateUtils.getDayPrecisionTimeSpanString(LocalContext.current, LocalLocale.current.platformLocale, device.createdMillis) }
  val lastActive = DateUtils.getDayPrecisionTimeSpanString(LocalContext.current, LocalLocale.current.platformLocale, device.lastSeenMillis)
  val menuController = remember { DropdownMenus.MenuController() }
  Row(
    modifier = Modifier
      .fillMaxWidth()
  ) {
    Image(
      painter = painterResource(id = CoreUiR.drawable.symbol_devices_24),
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
      if (isInternalUser) {
        Text("[Internal] DeviceId: ${device.id}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (linkedDate != null) {
        Text(stringResource(R.string.DeviceListItem_linked_s, linkedDate), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
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
                modifier = Modifier.padding(horizontal = 16.dp)
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
                painter = SignalIcons.Edit.painter,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = stringResource(R.string.LinkDeviceFragment__edit_name),
                modifier = Modifier.padding(horizontal = 16.dp)
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

@DayNightPreviews
@Composable
private fun DeviceListScreenPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        devices = listOf(
          Device(1, "Sam's Macbook Pro", 1715793982000, 1716053182000, 0),
          Device(1, "Sam's iPad", 1715793182000, 1716053122000, 0)
        ),
        seenQrEducationSheet = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenLoadingPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        deviceListLoading = true,
        seenQrEducationSheet = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenLinkingPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.Linking,
        seenQrEducationSheet = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenUnlinkingPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.Unlinking,
        seenQrEducationSheet = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenSyncingMessagesPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.SyncingMessages(1),
        seenQrEducationSheet = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenSyncingFailedRetryPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.SyncingTimedOut,
        seenQrEducationSheet = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenSyncingFailedPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.SyncingFailed(
          deviceId = 1,
          deviceRegistrationId = 1,
          syncFailType = LinkDeviceSettingsState.SyncFailType.NOT_RETRYABLE
        ),
        seenQrEducationSheet = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenContactSupportPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.ContactSupport,
        seenQrEducationSheet = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenDeviceUnlinkedPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.DeviceUnlinked(1736454440342),
        seenQrEducationSheet = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenNotEnoughStoragePreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.SyncingFailed(
          deviceId = 1,
          deviceRegistrationId = 1,
          syncFailType = LinkDeviceSettingsState.SyncFailType.NOT_ENOUGH_SPACE
        ),
        seenQrEducationSheet = true
      )
    )
  }
}
