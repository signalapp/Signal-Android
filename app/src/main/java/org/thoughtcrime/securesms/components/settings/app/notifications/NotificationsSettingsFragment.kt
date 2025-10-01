package org.thoughtcrime.securesms.components.settings.app.notifications

import android.content.ActivityNotFoundException
import android.media.Ringtone
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.Texts
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.PromptBatterySaverDialogFragment
import org.thoughtcrime.securesms.components.settings.app.routes.AppSettingsRoute
import org.thoughtcrime.securesms.components.settings.app.routes.AppSettingsRouter
import org.thoughtcrime.securesms.components.settings.models.Banner
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.TurnOnNotificationsBottomSheet
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.RingtoneUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.viewModel

class NotificationsSettingsFragment : ComposeFragment() {

  private val viewModel: NotificationsSettingsViewModel by viewModel {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

    NotificationsSettingsViewModel.Factory(sharedPreferences).create(NotificationsSettingsViewModel::class.java)
  }

  private val appSettingsRouter: AppSettingsRouter by viewModel {
    AppSettingsRouter()
  }

  private lateinit var callbacks: DefaultNotificationsSettingsCallbacks

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    callbacks = DefaultNotificationsSettingsCallbacks(requireActivity(), viewModel, appSettingsRouter, DefaultNotificationsSettingsCallbacks.ActivityResultRegisterer.ForFragment(this))

    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.RESUMED) {
        appSettingsRouter.currentRoute.collect {
          when (it) {
            AppSettingsRoute.NotificationsRoute.NotificationProfiles -> {
              findNavController().safeNavigate(R.id.action_notificationsSettingsFragment_to_notificationProfilesFragment)
            }

            else -> error("Unexpected route: ${it.javaClass.name}")
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    NotificationsSettingsScreen(state = state, callbacks = callbacks)
  }
}

/**
 * Default callbacks that package up launcher handling and other logic that was in the original fragment.
 * This must be called during the creation cycle of the component it is attached to.
 */
open class DefaultNotificationsSettingsCallbacks(
  val activity: FragmentActivity,
  val viewModel: NotificationsSettingsViewModel,
  val appSettingsRouter: AppSettingsRouter,
  activityResultRegisterer: ActivityResultRegisterer = ActivityResultRegisterer.ForActivity(activity)
) : NotificationsSettingsCallbacks {

  companion object {
    private val TAG = Log.tag(DefaultNotificationsSettingsCallbacks::class)
  }

  interface ActivityResultRegisterer {
    fun <I, O> registerForActivityResult(
      contract: ActivityResultContract<I, O>,
      callback: ActivityResultCallback<O>
    ): ActivityResultLauncher<I>

    class ForActivity(val activity: FragmentActivity) : ActivityResultRegisterer {
      override fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        callback: ActivityResultCallback<O>
      ): ActivityResultLauncher<I> {
        return activity.registerForActivityResult(contract, callback)
      }
    }

    class ForFragment(val fragment: Fragment) : ActivityResultRegisterer {
      override fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        callback: ActivityResultCallback<O>
      ): ActivityResultLauncher<I> {
        return fragment.registerForActivityResult(contract, callback)
      }
    }
  }

  private val messageSoundSelectionLauncher: ActivityResultLauncher<Unit> = activityResultRegisterer.registerForActivityResult(
    NotificationSoundSelectionContract(NotificationSoundSelectionContract.Target.MESSAGE),
    viewModel::setMessageNotificationsSound
  )

  private val callsSoundSelectionLauncher: ActivityResultLauncher<Unit> = activityResultRegisterer.registerForActivityResult(
    NotificationSoundSelectionContract(NotificationSoundSelectionContract.Target.CALL),
    viewModel::setCallRingtone
  )

  private val notificationPrioritySelectionLauncher: ActivityResultLauncher<Unit> = activityResultRegisterer.registerForActivityResult(
    contract = NotificationPrioritySelectionContract(),
    callback = {}
  )

  override fun onTurnOnNotificationsActionClick() {
    TurnOnNotificationsBottomSheet.turnOnSystemNotificationsFragment(activity).show(activity.supportFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
  }

  override fun onNavigationClick() {
    activity.onBackPressedDispatcher.onBackPressed()
  }

  override fun setMessageNotificationsEnabled(enabled: Boolean) {
    viewModel.setMessageNotificationsEnabled(enabled)
  }

  override fun onCustomizeClick() {
    activity.let {
      NotificationChannels.getInstance().openChannelSettings(it, NotificationChannels.getInstance().messagesChannel, null)
    }
  }

  override fun getRingtoneSummary(uri: Uri): String {
    return if (uri.toString().isBlank()) {
      activity.getString(R.string.preferences__silent)
    } else {
      val tone: Ringtone? = RingtoneUtil.getRingtone(activity, uri)
      if (tone != null) {
        try {
          tone.getTitle(activity) ?: activity.getString(R.string.NotificationsSettingsFragment__unknown_ringtone)
        } catch (e: SecurityException) {
          Log.w(TAG, "Unable to get title for ringtone", e)
          return activity.getString(R.string.NotificationsSettingsFragment__unknown_ringtone)
        }
      } else {
        activity.getString(R.string.preferences__default)
      }
    }
  }

  override fun launchMessageSoundSelectionIntent() {
    try {
      messageSoundSelectionLauncher.launch(Unit)
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(activity, R.string.NotificationSettingsFragment__failed_to_open_picker, Toast.LENGTH_LONG).show()
    }
  }

  override fun launchCallsSoundSelectionIntent() {
    try {
      callsSoundSelectionLauncher.launch(Unit)
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(activity, R.string.NotificationSettingsFragment__failed_to_open_picker, Toast.LENGTH_LONG).show()
    }
  }

  override fun setMessageNotificationVibration(enabled: Boolean) {
    viewModel.setMessageNotificationsEnabled(enabled)
  }

  override fun setMessasgeNotificationLedColor(selection: String) {
    viewModel.setMessageNotificationLedColor(selection)
  }

  override fun setMessasgeNotificationLedBlink(selection: String) {
    viewModel.setMessageNotificationLedBlink(selection)
  }

  override fun setMessageNotificationInChatSoundsEnabled(enabled: Boolean) {
    viewModel.setMessageNotificationInChatSoundsEnabled(enabled)
  }

  override fun setMessageRepeatAlerts(selection: String) {
    viewModel.setMessageRepeatAlerts(selection.toInt())
  }

  override fun setMessageNotificationPrivacy(selection: String) {
    viewModel.setMessageNotificationPrivacy(selection)
  }

  @RequiresApi(23)
  override fun onTroubleshootNotificationsClick() {
    PromptBatterySaverDialogFragment.show(activity.supportFragmentManager)
  }

  override fun launchNotificationPriorityIntent() {
    notificationPrioritySelectionLauncher.launch(Unit)
  }

  override fun setMessageNotificationPriority(selection: String) {
    viewModel.setMessageNotificationPriority(selection.toInt())
  }

  override fun setCallNotificationsEnabled(enabled: Boolean) {
    viewModel.setCallNotificationsEnabled(enabled)
  }

  override fun setCallVibrateEnabled(enabled: Boolean) {
    viewModel.setCallVibrateEnabled(enabled)
  }

  override fun onNavigationProfilesClick() {
    appSettingsRouter.navigateTo(AppSettingsRoute.NotificationsRoute.NotificationProfiles)
  }

  override fun setNotifyWhenContactJoinsSignal(enabled: Boolean) {
    viewModel.setNotifyWhenContactJoinsSignal(enabled)
  }
}

interface NotificationsSettingsCallbacks {
  fun onTurnOnNotificationsActionClick() = Unit
  fun onNavigationClick() = Unit
  fun setMessageNotificationsEnabled(enabled: Boolean) = Unit
  fun onCustomizeClick() = Unit
  fun getRingtoneSummary(uri: Uri): String = "Test Sound"
  fun launchMessageSoundSelectionIntent(): Unit = Unit
  fun launchCallsSoundSelectionIntent(): Unit = Unit
  fun setMessageNotificationVibration(enabled: Boolean) = Unit
  fun setMessasgeNotificationLedColor(selection: String) = Unit
  fun setMessasgeNotificationLedBlink(selection: String) = Unit
  fun setMessageNotificationInChatSoundsEnabled(enabled: Boolean) = Unit
  fun setMessageRepeatAlerts(selection: String) = Unit
  fun setMessageNotificationPrivacy(selection: String) = Unit
  fun onTroubleshootNotificationsClick() = Unit
  fun launchNotificationPriorityIntent() = Unit
  fun setMessageNotificationPriority(selection: String) = Unit
  fun setCallNotificationsEnabled(enabled: Boolean) = Unit
  fun setCallVibrateEnabled(enabled: Boolean) = Unit
  fun onNavigationProfilesClick() = Unit
  fun setNotifyWhenContactJoinsSignal(enabled: Boolean) = Unit

  object Empty : NotificationsSettingsCallbacks
}

@Composable
fun NotificationsSettingsScreen(
  state: NotificationsSettingsState,
  callbacks: NotificationsSettingsCallbacks,
  deviceState: DeviceState = remember { DeviceState() }
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences__notifications),
    onNavigationClick = callbacks::onNavigationClick,
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24)
  ) {
    LazyColumn(
      modifier = Modifier.padding(it)
    ) {
      if (!state.messageNotificationsState.canEnableNotifications) {
        item {
          Banner(
            text = stringResource(R.string.NotificationSettingsFragment__to_enable_notifications),
            action = stringResource(R.string.NotificationSettingsFragment__turn_on),
            onActionClick = callbacks::onTurnOnNotificationsActionClick
          )
        }
      }

      item {
        Texts.SectionHeader(stringResource(R.string.NotificationsSettingsFragment__messages))
      }

      item {
        Rows.ToggleRow(
          text = stringResource(R.string.preferences__notifications),
          enabled = state.messageNotificationsState.canEnableNotifications,
          checked = state.messageNotificationsState.notificationsEnabled,
          onCheckChanged = callbacks::setMessageNotificationsEnabled
        )
      }

      if (deviceState.apiLevel >= 30) {
        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences__customize),
            label = stringResource(R.string.preferences__change_sound_and_vibration),
            enabled = state.messageNotificationsState.notificationsEnabled,
            onClick = callbacks::onCustomizeClick
          )
        }
      } else {
        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences__sound),
            label = remember(state.messageNotificationsState.sound) {
              callbacks.getRingtoneSummary(state.messageNotificationsState.sound)
            },
            enabled = state.messageNotificationsState.notificationsEnabled,
            onClick = callbacks::launchMessageSoundSelectionIntent
          )
        }

        item {
          Rows.ToggleRow(
            text = stringResource(R.string.preferences__vibrate),
            checked = state.messageNotificationsState.vibrateEnabled,
            enabled = state.messageNotificationsState.notificationsEnabled,
            onCheckChanged = callbacks::setMessageNotificationsEnabled
          )
        }

        item {
          Rows.RadioListRow(
            text = {
              Box(
                modifier = Modifier
                  .clip(CircleShape)
                  .size(24.dp)
                  .background(color = getLedColor(state.messageNotificationsState.ledColor))
              )

              Spacer(modifier = Modifier.size(10.dp))

              Text(text = stringResource(R.string.preferences__led_color))
            },
            dialogTitle = stringResource(R.string.preferences__led_color),
            labels = stringArrayResource(R.array.pref_led_color_entries),
            values = stringArrayResource(R.array.pref_led_color_values),
            selectedValue = state.messageNotificationsState.ledColor,
            enabled = state.messageNotificationsState.notificationsEnabled,
            onSelected = callbacks::setMessasgeNotificationLedColor
          )
        }

        if (!deviceState.supportsNotificationChannels) {
          item {
            Rows.RadioListRow(
              text = stringResource(R.string.preferences__pref_led_blink_title),
              labels = stringArrayResource(R.array.pref_led_blink_pattern_entries),
              values = stringArrayResource(R.array.pref_led_blink_pattern_values),
              selectedValue = state.messageNotificationsState.ledBlink,
              enabled = state.messageNotificationsState.notificationsEnabled,
              onSelected = callbacks::setMessasgeNotificationLedBlink
            )
          }
        }
      }

      item {
        Rows.ToggleRow(
          text = stringResource(R.string.preferences_notifications__in_chat_sounds),
          checked = state.messageNotificationsState.inChatSoundsEnabled,
          enabled = state.messageNotificationsState.notificationsEnabled,
          onCheckChanged = callbacks::setMessageNotificationInChatSoundsEnabled
        )
      }

      item {
        Rows.RadioListRow(
          text = stringResource(R.string.preferences__repeat_alerts),
          labels = stringArrayResource(R.array.pref_repeat_alerts_entries),
          values = stringArrayResource(R.array.pref_repeat_alerts_values),
          selectedValue = state.messageNotificationsState.repeatAlerts.toString(),
          enabled = state.messageNotificationsState.notificationsEnabled,
          onSelected = callbacks::setMessageRepeatAlerts
        )
      }

      item {
        Rows.RadioListRow(
          text = stringResource(R.string.preferences_notifications__show),
          labels = stringArrayResource(R.array.pref_notification_privacy_entries),
          values = stringArrayResource(R.array.pref_notification_privacy_values),
          selectedValue = state.messageNotificationsState.messagePrivacy,
          enabled = state.messageNotificationsState.notificationsEnabled,
          onSelected = callbacks::setMessageNotificationPrivacy
        )
      }

      if (deviceState.apiLevel >= 23 && state.messageNotificationsState.troubleshootNotifications) {
        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences_notifications__troubleshoot),
            onClick = callbacks::onTroubleshootNotificationsClick
          )
        }
      }

      if (deviceState.apiLevel < 30) {
        if (deviceState.supportsNotificationChannels) {
          item {
            Rows.TextRow(
              text = stringResource(R.string.preferences_notifications__priority),
              enabled = state.messageNotificationsState.notificationsEnabled,
              onClick = callbacks::launchNotificationPriorityIntent
            )
          }
        } else {
          item {
            Rows.RadioListRow(
              text = stringResource(R.string.preferences_notifications__priority),
              labels = stringArrayResource(R.array.pref_notification_priority_entries),
              values = stringArrayResource(R.array.pref_notification_priority_values),
              selectedValue = state.messageNotificationsState.priority.toString(),
              enabled = state.messageNotificationsState.notificationsEnabled,
              onSelected = callbacks::setMessageNotificationPriority
            )
          }
        }
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(stringResource(R.string.NotificationsSettingsFragment__calls))
      }

      item {
        Rows.ToggleRow(
          text = stringResource(R.string.preferences__notifications),
          enabled = state.callNotificationsState.canEnableNotifications,
          checked = state.callNotificationsState.notificationsEnabled,
          onCheckChanged = callbacks::setCallNotificationsEnabled
        )
      }

      item {
        val ringtoneSummary = remember(state.callNotificationsState.ringtone) {
          callbacks.getRingtoneSummary(state.callNotificationsState.ringtone)
        }

        Rows.TextRow(
          text = stringResource(R.string.preferences_notifications__ringtone),
          label = ringtoneSummary,
          enabled = state.callNotificationsState.notificationsEnabled,
          onClick = callbacks::launchCallsSoundSelectionIntent
        )
      }

      item {
        Rows.ToggleRow(
          text = stringResource(R.string.preferences__vibrate),
          checked = state.callNotificationsState.vibrateEnabled,
          enabled = state.callNotificationsState.notificationsEnabled,
          onCheckChanged = callbacks::setCallVibrateEnabled
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(stringResource(R.string.NotificationsSettingsFragment__notification_profiles))
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.NotificationsSettingsFragment__profiles),
          label = stringResource(R.string.NotificationsSettingsFragment__create_a_profile_to_receive_notifications_only_from_people_and_groups_you_choose),
          onClick = callbacks::onNavigationProfilesClick
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(stringResource(R.string.NotificationsSettingsFragment__notify_when))
      }

      item {
        Rows.ToggleRow(
          text = stringResource(R.string.NotificationsSettingsFragment__contact_joins_signal),
          checked = state.notifyWhenContactJoinsSignal,
          onCheckChanged = callbacks::setNotifyWhenContactJoinsSignal
        )
      }
    }
  }
}

@Composable
private fun getLedColor(ledColorString: String): Color {
  return when (ledColorString) {
    "green" -> colorResource(R.color.green_500)
    "red" -> colorResource(R.color.red_500)
    "blue" -> colorResource(R.color.blue_500)
    "yellow" -> colorResource(R.color.yellow_500)
    "cyan" -> colorResource(R.color.cyan_500)
    "magenta" -> colorResource(R.color.pink_500)
    "white" -> colorResource(R.color.white)
    else -> colorResource(R.color.transparent)
  }
}

@DayNightPreviews
@Composable
private fun NotificationsSettingsScreenPreview() {
  Previews.Preview {
    NotificationsSettingsScreen(
      deviceState = rememberTestDeviceState(),
      state = rememberTestState(),
      callbacks = NotificationsSettingsCallbacks.Empty
    )
  }
}

@DayNightPreviews
@Composable
private fun NotificationsSettingsScreenAPI21Preview() {
  Previews.Preview {
    NotificationsSettingsScreen(
      deviceState = rememberTestDeviceState(apiLevel = 21, supportsNotificationChannels = false),
      state = rememberTestState(),
      callbacks = NotificationsSettingsCallbacks.Empty
    )
  }
}

@Composable
private fun rememberTestDeviceState(
  apiLevel: Int = 35,
  supportsNotificationChannels: Boolean = true
): DeviceState = remember {
  DeviceState(
    apiLevel = apiLevel,
    supportsNotificationChannels = supportsNotificationChannels
  )
}

@Composable
private fun rememberTestState(): NotificationsSettingsState = remember {
  NotificationsSettingsState(
    messageNotificationsState = MessageNotificationsState(
      notificationsEnabled = true,
      canEnableNotifications = true,
      sound = Uri.EMPTY,
      vibrateEnabled = true,
      ledColor = "blue",
      ledBlink = "",
      inChatSoundsEnabled = true,
      repeatAlerts = 1,
      messagePrivacy = "",
      priority = 1,
      troubleshootNotifications = true
    ),
    callNotificationsState = CallNotificationsState(
      notificationsEnabled = true,
      canEnableNotifications = true,
      ringtone = Uri.EMPTY,
      vibrateEnabled = true
    ),
    notifyWhenContactJoinsSignal = true
  )
}

data class DeviceState(
  val apiLevel: Int = Build.VERSION.SDK_INT,
  val supportsNotificationChannels: Boolean = NotificationChannels.supported()
)
