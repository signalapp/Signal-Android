package org.thoughtcrime.securesms.components.settings.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.navigation.NavDirections
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.signal.core.util.getParcelableExtraCompat
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.routes.AppSettingsRoute
import org.thoughtcrime.securesms.components.settings.app.subscription.GooglePayComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.GooglePayRepository
import org.thoughtcrime.securesms.help.HelpFragment
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.manage.UsernameEditMode
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.CachedInflater
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.SignalE164Util
import org.thoughtcrime.securesms.util.navigation.safeNavigate

private const val START_ROUTE = "app.settings.args.START_ROUTE"
private const val NOTIFICATION_CATEGORY = "android.intent.category.NOTIFICATION_PREFERENCES"
private const val STATE_WAS_CONFIGURATION_UPDATED = "app.settings.state.configuration.updated"
private const val EXTRA_PERFORM_ACTION_ON_CREATE = "extra_perform_action_on_create"

class AppSettingsActivity : DSLSettingsActivity(), GooglePayComponent {

  private var wasConfigurationUpdated = false

  override val googlePayRepository: GooglePayRepository by lazy { GooglePayRepository(this) }
  override val googlePayResultPublisher: Subject<GooglePayComponent.GooglePayResult> = PublishSubject.create()

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    if (intent?.hasExtra(ARG_NAV_GRAPH) != true) {
      intent?.putExtra(ARG_NAV_GRAPH, R.navigation.app_settings_with_change_number)
    }

    super.onCreate(savedInstanceState, ready)

    val startingAction: NavDirections? = if (intent?.categories?.contains(NOTIFICATION_CATEGORY) == true) {
      AppSettingsFragmentDirections.actionDirectToNotificationsSettingsFragment()
    } else {
      val appSettingsRoute: AppSettingsRoute? = intent?.getParcelableExtraCompat(START_ROUTE, AppSettingsRoute::class.java)
      when (appSettingsRoute) {
        AppSettingsRoute.Empty -> null
        AppSettingsRoute.BackupsRoute.Local -> AppSettingsFragmentDirections.actionDirectToBackupsPreferenceFragment()
        is AppSettingsRoute.HelpRoute.Settings -> AppSettingsFragmentDirections.actionDirectToHelpFragment()
          .setStartCategoryIndex(appSettingsRoute.startCategoryIndex)
        AppSettingsRoute.DataAndStorageRoute.Proxy -> AppSettingsFragmentDirections.actionDirectToEditProxyFragment()
        AppSettingsRoute.NotificationsRoute.Notifications -> AppSettingsFragmentDirections.actionDirectToNotificationsSettingsFragment()
        AppSettingsRoute.ChangeNumberRoute.Start -> AppSettingsFragmentDirections.actionDirectToChangeNumberFragment()
        is AppSettingsRoute.DonationsRoute.Donations -> AppSettingsFragmentDirections.actionDirectToManageDonations().setDirectToCheckoutType(appSettingsRoute.directToCheckoutType)
        AppSettingsRoute.NotificationsRoute.NotificationProfiles -> AppSettingsFragmentDirections.actionDirectToNotificationProfiles()
        is AppSettingsRoute.NotificationsRoute.EditProfile -> AppSettingsFragmentDirections.actionDirectToCreateNotificationProfiles()
        is AppSettingsRoute.NotificationsRoute.ProfileDetails -> AppSettingsFragmentDirections.actionDirectToNotificationProfileDetails(
          appSettingsRoute.profileId
        )

        AppSettingsRoute.PrivacyRoute.Privacy -> AppSettingsFragmentDirections.actionDirectToPrivacy()
        AppSettingsRoute.LinkDeviceRoute.LinkDevice -> AppSettingsFragmentDirections.actionDirectToDevices()
        AppSettingsRoute.UsernameLinkRoute.UsernameLink -> AppSettingsFragmentDirections.actionDirectToUsernameLinkSettings()
        is AppSettingsRoute.AccountRoute.Username -> AppSettingsFragmentDirections.actionDirectToUsernameRecovery()
        is AppSettingsRoute.BackupsRoute.Remote -> AppSettingsFragmentDirections.actionDirectToRemoteBackupsSettingsFragment()
        AppSettingsRoute.ChatFoldersRoute.ChatFolders -> AppSettingsFragmentDirections.actionDirectToChatFoldersFragment()
        is AppSettingsRoute.ChatFoldersRoute.CreateChatFolders -> AppSettingsFragmentDirections.actionDirectToCreateFoldersFragment(
          appSettingsRoute.folderId,
          appSettingsRoute.threadIds
        )

        AppSettingsRoute.BackupsRoute.Backups -> AppSettingsFragmentDirections.actionDirectToBackupsSettingsFragment()
        AppSettingsRoute.Invite -> AppSettingsFragmentDirections.actionDirectToInviteFragment()
        AppSettingsRoute.DataAndStorageRoute.DataAndStorage -> AppSettingsFragmentDirections.actionDirectToStoragePreferenceFragment()
        else -> error("Unsupported start location: ${appSettingsRoute?.javaClass?.name}")
      }
    }

    intent = intent.putExtra(START_ROUTE, AppSettingsRoute.Empty)

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
    googlePayResultPublisher.onNext(GooglePayComponent.GooglePayResult(requestCode, resultCode, data))
  }

  companion object {
    const val ACTION_CHANGE_NUMBER_SUCCESS = "action_change_number_success"

    @JvmStatic
    @JvmOverloads
    fun home(context: Context, action: String? = null): Intent {
      return getIntentForStartLocation(context, AppSettingsRoute.Empty)
        .putExtra(EXTRA_PERFORM_ACTION_ON_CREATE, action)
    }

    @JvmStatic
    fun backups(context: Context): Intent = getIntentForStartLocation(context, AppSettingsRoute.BackupsRoute.Local)

    @JvmStatic
    fun help(context: Context, startCategoryIndex: Int = 0): Intent {
      return getIntentForStartLocation(context, AppSettingsRoute.HelpRoute.Settings(startCategoryIndex = startCategoryIndex))
        .putExtra(HelpFragment.START_CATEGORY_INDEX, startCategoryIndex)
    }

    @JvmStatic
    fun proxy(context: Context): Intent = getIntentForStartLocation(context, AppSettingsRoute.DataAndStorageRoute.Proxy)

    @JvmStatic
    fun notifications(context: Context): Intent = getIntentForStartLocation(context, AppSettingsRoute.NotificationsRoute.Notifications)

    @JvmStatic
    fun changeNumber(context: Context): Intent = getIntentForStartLocation(context, AppSettingsRoute.ChangeNumberRoute.Start)

    @JvmStatic
    fun subscriptions(context: Context): Intent = getIntentForStartLocation(context, AppSettingsRoute.DonationsRoute.Donations(directToCheckoutType = InAppPaymentType.RECURRING_DONATION))

    @JvmStatic
    fun boost(context: Context): Intent = getIntentForStartLocation(context, AppSettingsRoute.DonationsRoute.Donations(directToCheckoutType = InAppPaymentType.ONE_TIME_DONATION))

    @JvmStatic
    fun manageSubscriptions(context: Context): Intent = getIntentForStartLocation(context, AppSettingsRoute.DonationsRoute.Donations())

    fun manageStorage(context: Context): Intent = getIntentForStartLocation(context, AppSettingsRoute.DataAndStorageRoute.DataAndStorage)

    @JvmStatic
    fun notificationProfiles(context: Context): Intent = getIntentForStartLocation(context, AppSettingsRoute.NotificationsRoute.NotificationProfiles)

    @JvmStatic
    fun createNotificationProfile(context: Context): Intent = getIntentForStartLocation(context, AppSettingsRoute.NotificationsRoute.EditProfile())

    @JvmStatic
    fun privacy(context: Context): Intent = getIntentForStartLocation(context, AppSettingsRoute.PrivacyRoute.Privacy)

    @JvmStatic
    fun notificationProfileDetails(context: Context, profileId: Long): Intent {
      return getIntentForStartLocation(context, AppSettingsRoute.NotificationsRoute.ProfileDetails(profileId = profileId))
    }

    @JvmStatic
    fun linkedDevices(context: Context): Intent = getIntentForStartLocation(context, AppSettingsRoute.LinkDeviceRoute.LinkDevice)

    @JvmStatic
    fun usernameLinkSettings(context: Context): Intent = getIntentForStartLocation(context, AppSettingsRoute.UsernameLinkRoute.UsernameLink)

    @JvmStatic
    fun usernameRecovery(context: Context): Intent = getIntentForStartLocation(context, AppSettingsRoute.AccountRoute.Username(mode = UsernameEditMode.RECOVERY))

    @JvmStatic
    fun remoteBackups(context: Context): Intent = getIntentForStartLocation(context, AppSettingsRoute.BackupsRoute.Remote())

    @JvmStatic
    fun chatFolders(context: Context): Intent = getIntentForStartLocation(context, AppSettingsRoute.ChatFoldersRoute.ChatFolders)

    @JvmStatic
    fun createChatFolder(context: Context, id: Long = -1, threadIds: LongArray?): Intent {
      return getIntentForStartLocation(
        context,
        AppSettingsRoute.ChatFoldersRoute.CreateChatFolders(
          folderId = id,
          threadIds = threadIds ?: longArrayOf()
        )
      )
    }

    @JvmStatic
    fun backupsSettings(context: Context): Intent = getIntentForStartLocation(context, AppSettingsRoute.BackupsRoute.Backups)

    @JvmStatic
    fun invite(context: Context): Intent = getIntentForStartLocation(context, AppSettingsRoute.Invite)

    private fun getIntentForStartLocation(context: Context, startRoute: AppSettingsRoute): Intent {
      return Intent(context, AppSettingsActivity::class.java)
        .putExtra(ARG_NAV_GRAPH, R.navigation.app_settings_with_change_number)
        .putExtra(START_ROUTE, startRoute)
    }
  }
}
