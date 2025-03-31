/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.rememberFragmentState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.getSerializableCompat
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.calls.YouAreAlreadyInACallSnackbar.show
import org.thoughtcrime.securesms.calls.log.CallLogFilter
import org.thoughtcrime.securesms.components.ConnectivityWarningBottomSheet
import org.thoughtcrime.securesms.components.DebugLogsPromptDialogFragment
import org.thoughtcrime.securesms.components.DeviceSpecificNotificationBottomSheet
import org.thoughtcrime.securesms.components.PromptBatterySaverDialogFragment
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity.Companion.manageSubscriptions
import org.thoughtcrime.securesms.components.settings.app.notifications.manual.NotificationProfileSelectionFragment
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.conversationlist.RelinkDevicesReminderBottomSheetFragment
import org.thoughtcrime.securesms.conversationlist.RestoreCompleteBottomSheetDialog
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.devicetransfer.olddevice.OldDeviceExitActivity
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupActivity
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.main.MainActivityListHostFragment
import org.thoughtcrime.securesms.main.MainNavigationDestination
import org.thoughtcrime.securesms.main.MainNavigationDetailLocation
import org.thoughtcrime.securesms.main.MainToolbar
import org.thoughtcrime.securesms.main.MainToolbarCallback
import org.thoughtcrime.securesms.main.MainToolbarMode
import org.thoughtcrime.securesms.main.MainToolbarViewModel
import org.thoughtcrime.securesms.net.DeviceTransferBlockingInterceptor
import org.thoughtcrime.securesms.notifications.VitalsViewModel
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.stories.settings.StorySettingsActivity
import org.thoughtcrime.securesms.stories.tabs.ConversationListTabRepository
import org.thoughtcrime.securesms.stories.tabs.ConversationListTabsFragment
import org.thoughtcrime.securesms.stories.tabs.ConversationListTabsViewModel
import org.thoughtcrime.securesms.util.AppStartup
import org.thoughtcrime.securesms.util.CachedInflater
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.SplashScreenUtil
import org.thoughtcrime.securesms.util.WindowUtil
import org.thoughtcrime.securesms.util.viewModel
import org.thoughtcrime.securesms.window.AppScaffold

class MainActivity : PassphraseRequiredActivity(), VoiceNoteMediaControllerOwner, MainNavigator.NavigatorProvider {

  companion object {
    private const val KEY_STARTING_TAB = "STARTING_TAB"
    const val RESULT_CONFIG_CHANGED = Activity.RESULT_FIRST_USER + 901

    @JvmStatic
    fun clearTop(context: Context): Intent {
      return Intent(context, MainActivity::class.java)
        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    @JvmStatic
    fun clearTopAndOpenTab(context: Context, startingTab: MainNavigationDestination): Intent {
      return clearTop(context).putExtra(KEY_STARTING_TAB, startingTab)
    }
  }

  private val dynamicTheme = DynamicNoActionBarTheme()
  private val navigator = MainNavigator(this)
  private val lifecycleDisposable = LifecycleDisposable()

  private lateinit var mediaController: VoiceNoteMediaController

  override val voiceNoteMediaController: VoiceNoteMediaController
    get() = mediaController

  private val conversationListTabsViewModel: ConversationListTabsViewModel by viewModel {
    val startingTab = intent.extras?.getSerializableCompat(KEY_STARTING_TAB, MainNavigationDestination::class.java)
    ConversationListTabsViewModel(startingTab ?: MainNavigationDestination.CHATS, ConversationListTabRepository())
  }

  private val vitalsViewModel: VitalsViewModel by viewModel {
    VitalsViewModel(application)
  }

  private val openSettings: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    if (result.resultCode == RESULT_CONFIG_CHANGED) {
      recreate()
    }
  }

  private val toolbarViewModel: MainToolbarViewModel by viewModels()
  private val toolbarCallback = ToolbarCallback()

  private var onFirstRender = false

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    AppStartup.getInstance().onCriticalRenderEventStart()
    super.onCreate(savedInstanceState, ready)
    conversationListTabsViewModel

    setContent {
      val navState = rememberFragmentState()
      val listHostState = rememberFragmentState()
      val detailLocation by navigator.viewModel.detailLocation.collectAsStateWithLifecycle()

      LaunchedEffect(detailLocation) {
        if (detailLocation is MainNavigationDetailLocation.Conversation) {
          startActivity((detailLocation as MainNavigationDetailLocation.Conversation).intent)
          overridePendingTransition(R.anim.slide_from_end, R.anim.fade_scale_out)
        }
      }

      AppScaffold(
        bottomNavContent = {
          AndroidFragment(
            clazz = ConversationListTabsFragment::class.java,
            fragmentState = navState
          )
        },
        navRailContent = {
          AndroidFragment(
            clazz = ConversationListTabsFragment::class.java,
            fragmentState = navState
          )
        }
      ) {
        Column {
          val state by toolbarViewModel.state.collectAsStateWithLifecycle()

          SignalTheme(isDarkMode = DynamicTheme.isDarkTheme(LocalContext.current)) {
            MainToolbar(
              state = state,
              callback = toolbarCallback
            )
          }

          AndroidFragment(
            clazz = MainActivityListHostFragment::class.java,
            fragmentState = listHostState,
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }

    val content: View = findViewById(android.R.id.content)
    content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
      override fun onPreDraw(): Boolean {
        // Use pre draw listener to delay drawing frames till conversation list is ready
        return if (onFirstRender) {
          content.viewTreeObserver.removeOnPreDrawListener(this)
          true
        } else {
          false
        }
      }
    })

    lifecycleDisposable.bindTo(this)

    mediaController = VoiceNoteMediaController(this, true)

    handleDeepLinkIntent(intent)
    CachedInflater.from(this).clear()
    updateNavigationBarColor()

    lifecycleDisposable += vitalsViewModel.vitalsState.subscribe(this::presentVitalsState)
  }

  override fun getIntent(): Intent {
    return super.getIntent().setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleDeepLinkIntent(intent)

    val extras = intent.extras ?: return
    val startingTab = extras.getSerializableCompat(KEY_STARTING_TAB, MainNavigationDestination::class.java)

    when (startingTab) {
      MainNavigationDestination.CHATS -> conversationListTabsViewModel.onChatsSelected()
      MainNavigationDestination.CALLS -> conversationListTabsViewModel.onCallsSelected()
      MainNavigationDestination.STORIES -> {
        if (Stories.isFeatureEnabled()) {
          conversationListTabsViewModel.onStoriesSelected()
        }
      }

      null -> Unit
    }
  }

  override fun onPreCreate() {
    super.onPreCreate()
    dynamicTheme.onCreate(this)
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)

    if (SignalStore.misc.shouldShowLinkedDevicesReminder) {
      SignalStore.misc.shouldShowLinkedDevicesReminder = false
      RelinkDevicesReminderBottomSheetFragment.show(supportFragmentManager)
    }

    if (SignalStore.registration.restoringOnNewDevice) {
      SignalStore.registration.restoringOnNewDevice = false
      RestoreCompleteBottomSheetDialog.show(supportFragmentManager)
    } else if (SignalStore.misc.isOldDeviceTransferLocked) {
      MaterialAlertDialogBuilder(this)
        .setTitle(R.string.OldDeviceTransferLockedDialog__complete_registration_on_your_new_device)
        .setMessage(R.string.OldDeviceTransferLockedDialog__your_signal_account_has_been_transferred_to_your_new_device)
        .setPositiveButton(R.string.OldDeviceTransferLockedDialog__done) { _, _ -> OldDeviceExitActivity.exit(this) }
        .setNegativeButton(R.string.OldDeviceTransferLockedDialog__cancel_and_activate_this_device) { _, _ ->
          SignalStore.misc.isOldDeviceTransferLocked = false
          DeviceTransferBlockingInterceptor.getInstance().unblockNetwork()
        }
        .setCancelable(false)
        .show()
    }

    updateNavigationBarColor()

    vitalsViewModel.checkSlowNotificationHeuristics()
  }

  override fun onStop() {
    super.onStop()
    SplashScreenUtil.setSplashScreenThemeIfNecessary(this, SignalStore.settings.theme)
  }

  override fun onBackPressed() {
    if (!navigator.onBackPressed()) {
      super.onBackPressed()
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == MainNavigator.REQUEST_CONFIG_CHANGES && resultCode == RESULT_CONFIG_CHANGED) {
      recreate()
    }
  }

  override fun onFirstRender() {
    onFirstRender = true
  }

  override fun getNavigator(): MainNavigator {
    return navigator
  }

  private fun handleDeepLinkIntent(intent: Intent) {
    handleGroupLinkInIntent(intent)
    handleProxyInIntent(intent)
    handleSignalMeIntent(intent)
    handleCallLinkInIntent(intent)
    handleDonateReturnIntent(intent)
  }

  private fun updateNavigationBarColor() {
    WindowUtil.setNavigationBarColor(this, ContextCompat.getColor(this, R.color.signal_colorSurface2))
  }

  @SuppressLint("NewApi")
  private fun presentVitalsState(state: VitalsViewModel.State) {
    when (state) {
      VitalsViewModel.State.NONE -> Unit
      VitalsViewModel.State.PROMPT_SPECIFIC_BATTERY_SAVER_DIALOG -> DeviceSpecificNotificationBottomSheet.show(supportFragmentManager)
      VitalsViewModel.State.PROMPT_GENERAL_BATTERY_SAVER_DIALOG -> PromptBatterySaverDialogFragment.show(supportFragmentManager)
      VitalsViewModel.State.PROMPT_DEBUGLOGS_FOR_NOTIFICATIONS -> DebugLogsPromptDialogFragment.show(this, DebugLogsPromptDialogFragment.Purpose.NOTIFICATIONS)
      VitalsViewModel.State.PROMPT_DEBUGLOGS_FOR_CRASH -> DebugLogsPromptDialogFragment.show(this, DebugLogsPromptDialogFragment.Purpose.CRASH)
      VitalsViewModel.State.PROMPT_CONNECTIVITY_WARNING -> ConnectivityWarningBottomSheet.show(supportFragmentManager)
      VitalsViewModel.State.PROMPT_DEBUGLOGS_FOR_CONNECTIVITY_WARNING -> DebugLogsPromptDialogFragment.show(this, DebugLogsPromptDialogFragment.Purpose.CONNECTIVITY_WARNING)
    }
  }

  private fun handleGroupLinkInIntent(intent: Intent) {
    intent.data?.let { data ->
      CommunicationActions.handlePotentialGroupLinkUrl(this, data.toString())
    }
  }

  private fun handleProxyInIntent(intent: Intent) {
    intent.data?.let { data ->
      CommunicationActions.handlePotentialProxyLinkUrl(this, data.toString())
    }
  }

  private fun handleSignalMeIntent(intent: Intent) {
    intent.data?.let { data ->
      CommunicationActions.handlePotentialSignalMeUrl(this, data.toString())
    }
  }

  private fun handleCallLinkInIntent(intent: Intent) {
    intent.data?.let { data ->
      CommunicationActions.handlePotentialCallLinkUrl(this, data.toString()) {
        show(findViewById(android.R.id.content))
      }
    }
  }

  private fun handleDonateReturnIntent(intent: Intent) {
    intent.data?.let { data ->
      if (data.toString().startsWith(StripeApi.RETURN_URL_IDEAL)) {
        startActivity(manageSubscriptions(this))
      }
    }
  }

  inner class ToolbarCallback : MainToolbarCallback {

    override fun onNewGroupClick() {
      startActivity(CreateGroupActivity.newIntent(this@MainActivity))
    }

    override fun onClearPassphraseClick() {
      val intent = Intent(this@MainActivity, KeyCachingService::class.java)
      intent.setAction(KeyCachingService.CLEAR_KEY_ACTION)
      startService(intent)
    }

    override fun onMarkReadClick() {
      toolbarViewModel.markAllMessagesRead()
    }

    override fun onInviteFriendsClick() {
      val intent = Intent(this@MainActivity, InviteActivity::class.java)
      startActivity(intent)
    }

    override fun onFilterUnreadChatsClick() {
      toolbarViewModel.setChatFilter(ConversationFilter.UNREAD)
    }

    override fun onClearUnreadChatsFilterClick() {
      toolbarViewModel.setChatFilter(ConversationFilter.OFF)
    }

    override fun onSettingsClick() {
      openSettings.launch(AppSettingsActivity.home(this@MainActivity))
    }

    override fun onNotificationProfileClick() {
      NotificationProfileSelectionFragment.show(supportFragmentManager)
    }

    override fun onProxyClick() {
      startActivity(AppSettingsActivity.proxy(this@MainActivity))
    }

    override fun onSearchClick() {
      conversationListTabsViewModel.onSearchOpened()
      toolbarViewModel.setToolbarMode(MainToolbarMode.SEARCH)
      toolbarViewModel.emitEvent(MainToolbarViewModel.Event.Search.Open)
    }

    override fun onClearCallHistoryClick() {
      toolbarViewModel.clearCallHistory()
    }

    override fun onFilterMissedCallsClick() {
      toolbarViewModel.setCallLogFilter(CallLogFilter.MISSED)
    }

    override fun onClearCallFilterClick() {
      toolbarViewModel.setCallLogFilter(CallLogFilter.ALL)
    }

    override fun onStoryPrivacyClick() {
      startActivity(StorySettingsActivity.getIntent(this@MainActivity))
    }

    override fun onCloseSearchClick() {
      conversationListTabsViewModel.onSearchClosed()
      toolbarViewModel.setToolbarMode(MainToolbarMode.FULL)
      toolbarViewModel.emitEvent(MainToolbarViewModel.Event.Search.Close)
    }

    override fun onCloseArchiveClick() {
      toolbarViewModel.emitEvent(MainToolbarViewModel.Event.Chats.CloseArchive)
    }

    override fun onSearchQueryUpdated(query: String) {
      toolbarViewModel.setSearchQuery(query)
    }

    override fun onNotificationProfileTooltipDismissed() {
      SignalStore.notificationProfile.hasSeenTooltip = true
      toolbarViewModel.setShowNotificationProfilesTooltip(false)
    }
  }
}
