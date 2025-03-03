package org.thoughtcrime.securesms.components.settings.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.navigation.NavDirections
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.chats.folders.CreateFoldersFragmentArgs
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.EditNotificationProfileScheduleFragmentArgs
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.StripeRepository
import org.thoughtcrime.securesms.help.HelpFragment
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.CachedInflater
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.SignalE164Util
import org.thoughtcrime.securesms.util.navigation.safeNavigate

private const val START_LOCATION = "app.settings.start.location"
private const val START_ARGUMENTS = "app.settings.start.arguments"
private const val NOTIFICATION_CATEGORY = "android.intent.category.NOTIFICATION_PREFERENCES"
private const val STATE_WAS_CONFIGURATION_UPDATED = "app.settings.state.configuration.updated"
private const val EXTRA_PERFORM_ACTION_ON_CREATE = "extra_perform_action_on_create"

class AppSettingsActivity : DSLSettingsActivity(), InAppPaymentComponent {

  private var wasConfigurationUpdated = false

  override val stripeRepository: StripeRepository by lazy { StripeRepository(this) }
  override val googlePayResultPublisher: Subject<InAppPaymentComponent.GooglePayResult> = PublishSubject.create()

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    if (intent?.hasExtra(ARG_NAV_GRAPH) != true) {
      intent?.putExtra(ARG_NAV_GRAPH, R.navigation.app_settings_with_change_number)
    }

    super.onCreate(savedInstanceState, ready)

    val startingAction: NavDirections? = if (intent?.categories?.contains(NOTIFICATION_CATEGORY) == true) {
      AppSettingsFragmentDirections.actionDirectToNotificationsSettingsFragment()
    } else {
      when (StartLocation.fromCode(intent?.getIntExtra(START_LOCATION, StartLocation.HOME.code))) {
        StartLocation.HOME -> null
        StartLocation.BACKUPS -> AppSettingsFragmentDirections.actionDirectToBackupsPreferenceFragment()
        StartLocation.HELP -> AppSettingsFragmentDirections.actionDirectToHelpFragment()
          .setStartCategoryIndex(intent.getIntExtra(HelpFragment.START_CATEGORY_INDEX, 0))
        StartLocation.PROXY -> AppSettingsFragmentDirections.actionDirectToEditProxyFragment()
        StartLocation.NOTIFICATIONS -> AppSettingsFragmentDirections.actionDirectToNotificationsSettingsFragment()
        StartLocation.CHANGE_NUMBER -> AppSettingsFragmentDirections.actionDirectToChangeNumberFragment()
        StartLocation.SUBSCRIPTIONS -> AppSettingsFragmentDirections.actionDirectToManageDonations().setDirectToCheckoutType(InAppPaymentType.RECURRING_DONATION)
        StartLocation.BOOST -> AppSettingsFragmentDirections.actionDirectToManageDonations().setDirectToCheckoutType(InAppPaymentType.ONE_TIME_DONATION)
        StartLocation.MANAGE_SUBSCRIPTIONS -> AppSettingsFragmentDirections.actionDirectToManageDonations()
        StartLocation.NOTIFICATION_PROFILES -> AppSettingsFragmentDirections.actionDirectToNotificationProfiles()
        StartLocation.CREATE_NOTIFICATION_PROFILE -> AppSettingsFragmentDirections.actionDirectToCreateNotificationProfiles()
        StartLocation.NOTIFICATION_PROFILE_DETAILS -> AppSettingsFragmentDirections.actionDirectToNotificationProfileDetails(
          EditNotificationProfileScheduleFragmentArgs.fromBundle(intent.getBundleExtra(START_ARGUMENTS)!!).profileId
        )
        StartLocation.PRIVACY -> AppSettingsFragmentDirections.actionDirectToPrivacy()
        StartLocation.LINKED_DEVICES -> AppSettingsFragmentDirections.actionDirectToDevices()
        StartLocation.USERNAME_LINK -> AppSettingsFragmentDirections.actionDirectToUsernameLinkSettings()
        StartLocation.RECOVER_USERNAME -> AppSettingsFragmentDirections.actionDirectToUsernameRecovery()
        StartLocation.REMOTE_BACKUPS -> AppSettingsFragmentDirections.actionDirectToRemoteBackupsSettingsFragment()
        StartLocation.CHAT_FOLDERS -> AppSettingsFragmentDirections.actionDirectToChatFoldersFragment()
        StartLocation.CREATE_CHAT_FOLDER -> AppSettingsFragmentDirections.actionDirectToCreateFoldersFragment(
          CreateFoldersFragmentArgs.fromBundle(intent.getBundleExtra(START_ARGUMENTS)!!).folderId,
          CreateFoldersFragmentArgs.fromBundle(intent.getBundleExtra(START_ARGUMENTS)!!).threadIds
        )
        StartLocation.BACKUPS_SETTINGS -> AppSettingsFragmentDirections.actionDirectToBackupsSettingsFragment()
      }
    }

    intent = intent.putExtra(START_LOCATION, StartLocation.HOME)

    if (startingAction == null && savedInstanceState != null) {
      wasConfigurationUpdated = savedInstanceState.getBoolean(STATE_WAS_CONFIGURATION_UPDATED)
    }

    startingAction?.let {
      navController.safeNavigate(it)
    }

    SignalStore.settings.onConfigurationSettingChanged.observe(this) { key ->
      if (key == SettingsValues.THEME) {
        DynamicTheme.setDefaultDayNightMode(this)
        recreate()
      } else if (key == SettingsValues.LANGUAGE) {
        CachedInflater.from(this).clear()
        wasConfigurationUpdated = true
        recreate()
        val intent = Intent(this, KeyCachingService::class.java)
        intent.action = KeyCachingService.LOCALE_CHANGE_EVENT
        startService(intent)
      }
    }

    if (savedInstanceState == null) {
      when (intent.getStringExtra(EXTRA_PERFORM_ACTION_ON_CREATE)) {
        ACTION_CHANGE_NUMBER_SUCCESS -> {
          MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.ChangeNumber__your_phone_number_has_changed_to_s, SignalE164Util.prettyPrint(Recipient.self().requireE164())))
            .setPositiveButton(R.string.ChangeNumber__okay, null)
            .show()
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    finish()
    startActivity(intent)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(STATE_WAS_CONFIGURATION_UPDATED, wasConfigurationUpdated)
  }

  override fun onWillFinish() {
    if (wasConfigurationUpdated) {
      setResult(MainActivity.RESULT_CONFIG_CHANGED)
    }
  }

  @Suppress("DEPRECATION")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    googlePayResultPublisher.onNext(InAppPaymentComponent.GooglePayResult(requestCode, resultCode, data))
  }

  companion object {
    const val ACTION_CHANGE_NUMBER_SUCCESS = "action_change_number_success"

    @JvmStatic
    @JvmOverloads
    fun home(context: Context, action: String? = null): Intent {
      return getIntentForStartLocation(context, StartLocation.HOME)
        .putExtra(EXTRA_PERFORM_ACTION_ON_CREATE, action)
    }

    @JvmStatic
    fun backups(context: Context): Intent = getIntentForStartLocation(context, StartLocation.BACKUPS)

    @JvmStatic
    fun help(context: Context, startCategoryIndex: Int = 0): Intent {
      return getIntentForStartLocation(context, StartLocation.HELP)
        .putExtra(HelpFragment.START_CATEGORY_INDEX, startCategoryIndex)
    }

    @JvmStatic
    fun proxy(context: Context): Intent = getIntentForStartLocation(context, StartLocation.PROXY)

    @JvmStatic
    fun notifications(context: Context): Intent = getIntentForStartLocation(context, StartLocation.NOTIFICATIONS)

    @JvmStatic
    fun changeNumber(context: Context): Intent = getIntentForStartLocation(context, StartLocation.CHANGE_NUMBER)

    @JvmStatic
    fun subscriptions(context: Context): Intent = getIntentForStartLocation(context, StartLocation.SUBSCRIPTIONS)

    @JvmStatic
    fun boost(context: Context): Intent = getIntentForStartLocation(context, StartLocation.BOOST)

    @JvmStatic
    fun manageSubscriptions(context: Context): Intent = getIntentForStartLocation(context, StartLocation.MANAGE_SUBSCRIPTIONS)

    @JvmStatic
    fun notificationProfiles(context: Context): Intent = getIntentForStartLocation(context, StartLocation.NOTIFICATION_PROFILES)

    @JvmStatic
    fun createNotificationProfile(context: Context): Intent = getIntentForStartLocation(context, StartLocation.CREATE_NOTIFICATION_PROFILE)

    @JvmStatic
    fun privacy(context: Context): Intent = getIntentForStartLocation(context, StartLocation.PRIVACY)

    @JvmStatic
    fun notificationProfileDetails(context: Context, profileId: Long): Intent {
      val arguments = EditNotificationProfileScheduleFragmentArgs.Builder(profileId, false)
        .build()
        .toBundle()

      return getIntentForStartLocation(context, StartLocation.NOTIFICATION_PROFILE_DETAILS)
        .putExtra(START_ARGUMENTS, arguments)
    }

    @JvmStatic
    fun linkedDevices(context: Context): Intent = getIntentForStartLocation(context, StartLocation.LINKED_DEVICES)

    @JvmStatic
    fun usernameLinkSettings(context: Context): Intent = getIntentForStartLocation(context, StartLocation.USERNAME_LINK)

    @JvmStatic
    fun usernameRecovery(context: Context): Intent = getIntentForStartLocation(context, StartLocation.RECOVER_USERNAME)

    @JvmStatic
    fun remoteBackups(context: Context): Intent = getIntentForStartLocation(context, StartLocation.REMOTE_BACKUPS)

    @JvmStatic
    fun chatFolders(context: Context): Intent = getIntentForStartLocation(context, StartLocation.CHAT_FOLDERS)

    @JvmStatic
    fun createChatFolder(context: Context, id: Long = -1, threadIds: LongArray?): Intent {
      val arguments = CreateFoldersFragmentArgs.Builder(id, threadIds ?: longArrayOf())
        .build()
        .toBundle()

      return getIntentForStartLocation(context, StartLocation.CREATE_CHAT_FOLDER).putExtra(START_ARGUMENTS, arguments)
    }

    @JvmStatic
    fun backupsSettings(context: Context): Intent = getIntentForStartLocation(context, StartLocation.BACKUPS_SETTINGS)

    private fun getIntentForStartLocation(context: Context, startLocation: StartLocation): Intent {
      return Intent(context, AppSettingsActivity::class.java)
        .putExtra(ARG_NAV_GRAPH, R.navigation.app_settings_with_change_number)
        .putExtra(START_LOCATION, startLocation.code)
    }
  }

  private enum class StartLocation(val code: Int) {
    HOME(0),
    BACKUPS(1),
    HELP(2),
    PROXY(3),
    NOTIFICATIONS(4),
    CHANGE_NUMBER(5),
    SUBSCRIPTIONS(6),
    BOOST(7),
    MANAGE_SUBSCRIPTIONS(8),
    NOTIFICATION_PROFILES(9),
    CREATE_NOTIFICATION_PROFILE(10),
    NOTIFICATION_PROFILE_DETAILS(11),
    PRIVACY(12),
    LINKED_DEVICES(13),
    USERNAME_LINK(14),
    RECOVER_USERNAME(15),
    REMOTE_BACKUPS(16),
    CHAT_FOLDERS(17),
    CREATE_CHAT_FOLDER(18),
    BACKUPS_SETTINGS(19);

    companion object {
      fun fromCode(code: Int?): StartLocation {
        return entries.find { code == it.code } ?: HOME
      }
    }
  }
}
