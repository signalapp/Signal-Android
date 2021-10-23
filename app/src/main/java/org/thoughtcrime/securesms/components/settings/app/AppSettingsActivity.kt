package org.thoughtcrime.securesms.components.settings.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.navigation.NavDirections
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.SubscriptionsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.boost.BoostRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.boost.BoostViewModel
import org.thoughtcrime.securesms.components.settings.app.subscription.subscribe.SubscribeViewModel
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.help.HelpFragment
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.CachedInflater
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.FeatureFlags

private const val START_LOCATION = "app.settings.start.location"
private const val NOTIFICATION_CATEGORY = "android.intent.category.NOTIFICATION_PREFERENCES"
private const val STATE_WAS_CONFIGURATION_UPDATED = "app.settings.state.configuration.updated"

class AppSettingsActivity : DSLSettingsActivity() {

  private var wasConfigurationUpdated = false

  private val donationRepository: DonationPaymentRepository by lazy { DonationPaymentRepository(this) }
  private val subscribeViewModel: SubscribeViewModel by viewModels(
    factoryProducer = {
      SubscribeViewModel.Factory(SubscriptionsRepository(ApplicationDependencies.getDonationsService()), donationRepository, FETCH_SUBSCRIPTION_TOKEN_REQUEST_CODE)
    }
  )

  private val boostViewModel: BoostViewModel by viewModels(
    factoryProducer = {
      BoostViewModel.Factory(BoostRepository(), donationRepository, FETCH_BOOST_TOKEN_REQUEST_CODE)
    }
  )

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    warmDonationViewModels()

    if (intent?.hasExtra(ARG_NAV_GRAPH) != true) {
      intent?.putExtra(ARG_NAV_GRAPH, R.navigation.app_settings)
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
        StartLocation.SUBSCRIPTIONS -> AppSettingsFragmentDirections.actionDirectToSubscriptions()
      }
    }

    if (startingAction == null && savedInstanceState != null) {
      wasConfigurationUpdated = savedInstanceState.getBoolean(STATE_WAS_CONFIGURATION_UPDATED)
    }

    startingAction?.let {
      navController.navigate(it)
    }

    SignalStore.settings().onConfigurationSettingChanged.observe(this) { key ->
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
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(STATE_WAS_CONFIGURATION_UPDATED, wasConfigurationUpdated)
  }

  override fun onWillFinish() {
    if (wasConfigurationUpdated) {
      setResult(MainActivity.RESULT_CONFIG_CHANGED)
    } else {
      setResult(RESULT_OK)
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    subscribeViewModel.onActivityResult(requestCode, resultCode, data)
    boostViewModel.onActivityResult(requestCode, resultCode, data)
  }

  companion object {

    private const val FETCH_SUBSCRIPTION_TOKEN_REQUEST_CODE = 1000
    private const val FETCH_BOOST_TOKEN_REQUEST_CODE = 2000

    @JvmStatic
    fun home(context: Context): Intent = getIntentForStartLocation(context, StartLocation.HOME)

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

    private fun getIntentForStartLocation(context: Context, startLocation: StartLocation): Intent {
      return Intent(context, AppSettingsActivity::class.java)
        .putExtra(ARG_NAV_GRAPH, R.navigation.app_settings)
        .putExtra(START_LOCATION, startLocation.code)
    }
  }

  private fun warmDonationViewModels() {
    if (FeatureFlags.donorBadges()) {
      subscribeViewModel
      boostViewModel
    }
  }

  private enum class StartLocation(val code: Int) {
    HOME(0),
    BACKUPS(1),
    HELP(2),
    PROXY(3),
    NOTIFICATIONS(4),
    CHANGE_NUMBER(5),
    SUBSCRIPTIONS(6);

    companion object {
      fun fromCode(code: Int?): StartLocation {
        return values().find { code == it.code } ?: HOME
      }
    }
  }
}
