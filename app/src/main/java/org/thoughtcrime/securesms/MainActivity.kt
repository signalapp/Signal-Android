/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.NavigationType
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.split.ListDetailEvents
import org.signal.core.ui.compose.split.ListDetailNavDisplay
import org.signal.core.ui.compose.split.ListDetailPaneLayout
import org.signal.core.ui.compose.split.ListDetailPaneMetrics
import org.signal.core.ui.compose.split.ListPaneChrome
import org.signal.core.ui.compose.split.PaneAnchor
import org.signal.core.ui.compose.split.rememberListDetailPaneLayout
import org.signal.core.ui.compose.split.rememberListDetailPaneMetrics
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.permissions.Permissions
import org.signal.core.ui.rememberIsSplitPane
import org.signal.core.util.AppForegroundObserver
import org.signal.core.util.Util
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.getParcelableCompat
import org.signal.core.util.getSerializableCompat
import org.signal.core.util.logging.Log
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgress
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgressState
import org.thoughtcrime.securesms.backup.v2.ui.CouldNotCompleteBackupRestoreSheet
import org.thoughtcrime.securesms.backup.v2.ui.verify.VerifyBackupKeyActivity
import org.thoughtcrime.securesms.calls.YouAreAlreadyInACallSnackbar.show
import org.thoughtcrime.securesms.calls.log.CallLogFilter
import org.thoughtcrime.securesms.calls.log.CallLogFragment
import org.thoughtcrime.securesms.calls.new.NewCallActivity
import org.thoughtcrime.securesms.calls.quality.CallQuality
import org.thoughtcrime.securesms.calls.quality.CallQualityBottomSheetFragment
import org.thoughtcrime.securesms.chats.ConversationTransitionState
import org.thoughtcrime.securesms.components.DebugLogsPromptDialogFragment
import org.thoughtcrime.securesms.components.PromptBatterySaverDialogFragment
import org.thoughtcrime.securesms.components.compose.ConnectivityWarningBottomSheet
import org.thoughtcrime.securesms.components.compose.DeviceSpecificNotificationBottomSheet
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity.Companion.manageSubscriptions
import org.thoughtcrime.securesms.components.settings.app.notifications.manual.NotificationProfileSelectionFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.GooglePayComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.GooglePayRepository
import org.thoughtcrime.securesms.components.snackbars.LocalSnackbarStateConsumerRegistry
import org.thoughtcrime.securesms.components.snackbars.SnackbarHostKey
import org.thoughtcrime.securesms.components.snackbars.SnackbarState
import org.thoughtcrime.securesms.components.verificationrequested.VerificationCodeRequestedBottomSheet
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.NewConversationActivity
import org.thoughtcrime.securesms.conversation.v2.MotionEventRelay
import org.thoughtcrime.securesms.conversationlist.ConversationListFragment
import org.thoughtcrime.securesms.conversationlist.RelinkDevicesReminderBottomSheetFragment
import org.thoughtcrime.securesms.conversationlist.RestoreCompleteBottomSheetDialog
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.conversationlist.model.UnreadPaymentsLiveData
import org.thoughtcrime.securesms.devicetransfer.olddevice.OldDeviceExitActivity
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupActivity
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.lock.v2.CreateSvrPinActivity
import org.thoughtcrime.securesms.main.EmptyDetailScreen
import org.thoughtcrime.securesms.main.MainBottomChrome
import org.thoughtcrime.securesms.main.MainBottomChromeCallback
import org.thoughtcrime.securesms.main.MainBottomChromeState
import org.thoughtcrime.securesms.main.MainDetailRoute
import org.thoughtcrime.securesms.main.MainListRoute
import org.thoughtcrime.securesms.main.MainMegaphoneState
import org.thoughtcrime.securesms.main.MainNavigationBar
import org.thoughtcrime.securesms.main.MainNavigationEventSink
import org.thoughtcrime.securesms.main.MainNavigationEvents
import org.thoughtcrime.securesms.main.MainNavigationRail
import org.thoughtcrime.securesms.main.MainNavigationViewModel
import org.thoughtcrime.securesms.main.MainSnackbar
import org.thoughtcrime.securesms.main.MainSnackbarHostKey
import org.thoughtcrime.securesms.main.MainToolbar
import org.thoughtcrime.securesms.main.MainToolbarCallback
import org.thoughtcrime.securesms.main.MainToolbarMode
import org.thoughtcrime.securesms.main.MainToolbarState
import org.thoughtcrime.securesms.main.MainToolbarViewModel
import org.thoughtcrime.securesms.main.Material3OnScrollHelperBinder
import org.thoughtcrime.securesms.main.rememberDecoratedDetailEntries
import org.thoughtcrime.securesms.mediasend.MediaSendLauncher
import org.thoughtcrime.securesms.megaphone.Megaphone
import org.thoughtcrime.securesms.megaphone.MegaphoneActionController
import org.thoughtcrime.securesms.megaphone.Megaphones
import org.thoughtcrime.securesms.net.DeviceTransferBlockingInterceptor
import org.thoughtcrime.securesms.notifications.VitalsViewModel
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.profiles.manage.UsernameEditFragment
import org.thoughtcrime.securesms.service.BackupMediaRestoreService
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.starred.StarredMessagesActivity
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.AppStartup
import org.thoughtcrime.securesms.util.CachedInflater
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.Material3OnScrollHelper
import org.thoughtcrime.securesms.util.SplashScreenUtil
import org.thoughtcrime.securesms.util.TopToastPopup
import org.thoughtcrime.securesms.util.viewModel
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import kotlin.time.Duration.Companion.minutes
import org.signal.core.ui.R as CoreUiR

class MainActivity :
  PassphraseRequiredActivity(),
  VoiceNoteMediaControllerOwner,
  MainNavigator.NavigatorProvider,
  Material3OnScrollHelperBinder,
  ConversationListFragment.Callback,
  MainNavigationEventSink,
  CallLogFragment.Callback,
  GooglePayComponent {

  companion object {
    private val TAG = Log.tag(MainActivity::class)

    private const val KEY_STARTING_TAB = "STARTING_TAB"
    private const val KEY_DETAIL_LOCATION = "DETAIL_LOCATION"
    private const val KEY_EXIT_DETAIL = "EXIT_DETAIL"
    const val RESULT_CONFIG_CHANGED = RESULT_FIRST_USER + 901

    /** Width the navigation rail occupies inside the list pane. */
    private val RAIL_WIDTH = 80.dp

    @JvmStatic
    fun clearTop(context: Context): Intent {
      return Intent(context, MainActivity::class.java)
        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    @JvmStatic
    fun clearTopAndOpenTab(context: Context, startingTab: MainListRoute): Intent {
      return clearTop(context).putExtra(KEY_STARTING_TAB, startingTab)
    }

    @JvmStatic
    fun clearTopAndOpenDetail(context: Context, location: MainDetailRoute): Intent {
      return clearTop(context).putExtra(KEY_DETAIL_LOCATION, location)
    }

    /**
     * Opens the main screen with the current tab's detail content dropped, leaving its list displayed.
     * Used by screens that finish having invalidated whatever the detail pane was showing.
     */
    @JvmStatic
    fun clearTopAndExitDetail(context: Context): Intent {
      return clearTop(context).putExtra(KEY_EXIT_DETAIL, true)
    }
  }

  private val dynamicTheme = DynamicNoActionBarTheme()
  private val lifecycleDisposable = LifecycleDisposable()

  private lateinit var mediaController: VoiceNoteMediaController
  private lateinit var navigator: MainNavigator

  override val voiceNoteMediaController: VoiceNoteMediaController
    get() = mediaController

  private val mainNavigationViewModel: MainNavigationViewModel by viewModel {
    val startingTab = intent.extras?.getSerializableCompat(KEY_STARTING_TAB, MainListRoute::class.java)
    MainNavigationViewModel(it.createSavedStateHandle(), startingTab ?: MainListRoute.Chats)
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

  private val motionEventRelay: MotionEventRelay by viewModels()

  private var onFirstRender = false
  private var previousTopToastPopup: TopToastPopup? = null

  private val mainBottomChromeCallback = BottomChromeCallback()
  private val megaphoneActionController = MainMegaphoneActionController()
  private val mainNavigationCallback: (MainListRoute) -> Unit = { mainNavigationViewModel.onEvent(MainNavigationEvents.GoToTab(it)) }

  override val googlePayRepository: GooglePayRepository by lazy { GooglePayRepository(this) }
  override val googlePayResultPublisher: Subject<GooglePayComponent.GooglePayResult> = PublishSubject.create()

  override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
    return motionEventRelay.offer(ev) || super.dispatchTouchEvent(ev)
  }

  @OptIn(ExperimentalMaterial3AdaptiveApi::class)
  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    if (!isTaskRoot && intent.hasCategory(Intent.CATEGORY_LAUNCHER) && Intent.ACTION_MAIN == intent.action) {
      Log.w(TAG, "Duplicate launcher intent received, finishing duplicate instance.")
      finish()
      return
    }

    AppStartup.getInstance().onCriticalRenderEventStart()

    super.onCreate(savedInstanceState, ready)
    navigator = MainNavigator(this, mainNavigationViewModel)

    AppForegroundObserver.addListener(object : AppForegroundObserver.Listener {
      override fun onForeground() {
        mainNavigationViewModel.onEvent(MainNavigationEvents.RequestNextMegaphone)
      }
    })

    UnreadPaymentsLiveData().observe(this) { unread ->
      toolbarViewModel.setHasUnreadPayments(unread.isPresent)
    }

    lifecycleScope.launch {
      launch {
        repeatOnLifecycle(Lifecycle.State.RESUMED) {
          mainNavigationViewModel.navigationEvents.collectLatest {
            when (it) {
              MainNavigationViewModel.NavigationEvent.STORY_CAMERA_FIRST -> {
                mainBottomChromeCallback.onCameraClick(MainListRoute.Stories)
              }
            }
          }
        }
      }

      launch {
        mainNavigationViewModel.getNotificationProfiles().collectLatest { profiles ->
          withContext(Dispatchers.Main) {
            updateNotificationProfileStatus(profiles)
          }
        }
      }

      launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
          ArchiveRestoreProgress
            .stateFlow
            .distinctUntilChangedBy { it.needRestoreMediaService() }
            .filter { it.needRestoreMediaService() }
            .collect {
              Log.i(TAG, "Still restoring media, launching a service. Remaining restoration size: ${it.remainingRestoreSize} out of ${it.totalRestoreSize} ")
              BackupMediaRestoreService.resetTimeout()
              BackupMediaRestoreService.start(this@MainActivity, resources.getString(R.string.BackupStatus__restoring_media))
            }
        }
      }

      launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
          ArchiveRestoreProgress
            .stateFlow
            .filter { it.restoreStatus == ArchiveRestoreProgressState.RestoreStatus.LOCAL_RESTORE_DIRECTORY_UNAVAILABLE }
            .collect {
              ArchiveRestoreProgress.clearLocalRestoreDirectoryError()
              CouldNotCompleteBackupRestoreSheet().show(supportFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
              Log.i(TAG, "Local restore directory became unavailable.")
            }
        }
      }

      launch {
        repeatOnLifecycle(Lifecycle.State.RESUMED) {
          SignalStore
            .account
            .verificationCodeRequestedAtMsFlow
            .filter { it > 0L }
            .collect { requestedAt ->
              val notificationThreshold = requestedAt + 10.minutes.inWholeMilliseconds
              if (System.currentTimeMillis() < notificationThreshold) {
                VerificationCodeRequestedBottomSheet.show(supportFragmentManager, requestedAt)
              } else {
                Log.i(TAG, "Verification code requested but is older than 10 minutes, not showing sheet")
              }

              SignalStore.account.verificationCodeRequestedAtMs = 0L
            }
        }
      }
    }

    supportFragmentManager.setFragmentResultListener(
      CallQualityBottomSheetFragment.REQUEST_KEY,
      this
    ) { _, bundle ->
      if (bundle.getBoolean(CallQualityBottomSheetFragment.REQUEST_KEY, false)) {
        mainNavigationViewModel.snackbarRegistry.emit(
          SnackbarState(
            message = getString(R.string.CallQualitySheet__thanks_for_your_feedback),
            duration = Snackbars.Duration.SHORT,
            hostKey = MainSnackbarHostKey.Chat,
            fallbackKey = MainSnackbarHostKey.MainChrome
          )
        )
      }
    }

    setContent {
      val mainToolbarState by toolbarViewModel.state.collectAsStateWithLifecycle()
      val mainNavigationState by mainNavigationViewModel.mainNavigationBarState.collectAsStateWithLifecycle()

      LaunchedEffect(mainNavigationState.currentListLocation) {
        when (mainNavigationState.currentListLocation) {
          MainListRoute.Chats -> toolbarViewModel.presentToolbarForConversationListFragment()
          MainListRoute.Archive -> toolbarViewModel.presentToolbarForConversationListArchiveFragment()
          MainListRoute.Calls -> toolbarViewModel.presentToolbarForCallLogFragment()
          MainListRoute.Stories -> toolbarViewModel.presentToolbarForStoriesLandingFragment()
        }
      }

      val isActionModeActive = mainToolbarState.mode == MainToolbarMode.ACTION_MODE
      val isSearchModeActive = mainToolbarState.mode == MainToolbarMode.SEARCH
      val isBackHandlerEnabled = mainToolbarState.destination != MainListRoute.Chats && !isActionModeActive && !isSearchModeActive

      BackHandler(enabled = isBackHandlerEnabled) {
        mainNavigationViewModel.onEvent(MainNavigationEvents.GoToList(MainListRoute.Chats))
      }

      BackHandler(enabled = isActionModeActive) {
        toolbarCallback.onCloseActionModeClick()
      }

      BackHandler(enabled = isSearchModeActive) {
        toolbarCallback.onCloseSearchClick()
      }

      val focusManager = LocalFocusManager.current
      LaunchedEffect(mainToolbarState.mode) {
        if (mainToolbarState.mode == MainToolbarMode.ACTION_MODE) {
          focusManager.clearFocus()
        }
      }

      val isSplitPane = LocalResources.current.rememberIsSplitPane()
      val contentLayoutData = rememberListDetailPaneMetrics(listPaddingStart = mainToolbarState.mode.listPaddingStart)

      MainContainer {
        val detailLocation by mainNavigationViewModel.detailLocation.collectAsStateWithLifecycle()
        val isConversationFullscreen = !isSplitPane && detailLocation is MainDetailRoute.Conversation

        val context = LocalContext.current
        val isDarkTheme = isSystemInDarkTheme()
        val navBarColor = when {
          isSplitPane -> SignalTheme.colors.colorSurface1.toArgb()
          isConversationFullscreen -> Color.Transparent.toArgb()
          else -> ContextCompat.getColor(context, CoreUiR.color.signal_colorSurface2)
        }

        LaunchedEffect(isDarkTheme, navBarColor) {
          if (Build.VERSION.SDK_INT >= 26) {
            enableEdgeToEdge(
              navigationBarStyle = if (isDarkTheme) {
                SystemBarStyle.dark(navBarColor)
              } else {
                SystemBarStyle.light(navBarColor, navBarColor)
              }
            )
          } else {
            enableEdgeToEdge()
          }
        }

        val convoTransitionState = ConversationTransitionState.remember(isSplitPane)

        DisposableEffect(convoTransitionState) {
          mainNavigationViewModel.setChatListSnapshotCaptureProvider { convoTransitionState.writeGraphicsLayerToBitmap() }
          onDispose { mainNavigationViewModel.setChatListSnapshotCaptureProvider(null) }
        }

        val paneAnchor by mainNavigationViewModel.paneAnchor.collectAsStateWithLifecycle()
        val hasDetailContent by mainNavigationViewModel.hasDetailContent.collectAsStateWithLifecycle()

        val tabEntries = rememberDecoratedDetailEntries(mainNavigationViewModel, convoTransitionState, isSplitPane)

        val paneLayout = rememberMainPaneLayout(
          contentLayoutData = contentLayoutData,
          maxWidth = maxWidth,
          toolbarMode = mainToolbarState.mode,
          paneAnchor = paneAnchor
        )

        val listPaneChrome: ListPaneChrome = remember {
          { content -> MainListPaneChrome(content = content) }
        }

        val emptyDetailContent: @Composable () -> Unit = remember {
          { EmptyDetailScreen() }
        }

        Scaffold(
          containerColor = Color.Transparent,
          contentWindowInsets = WindowInsets(),
          snackbarHost = {
            // MainBottomChrome renders its own host over the list, but only in single pane, so this one
            // has to cover both split pane and whatever fills the window in single pane.
            if (isSplitPane || hasDetailContent) {
              MainSnackbar(
                hostKey = SnackbarHostKey.Global,
                onDismissed = mainBottomChromeCallback::onSnackbarDismissed,
                modifier = Modifier.navigationBarsPadding()
              )
            }
          },
          modifier = convoTransitionState.writeContentToGraphicsLayer()
        ) { paddingValues ->
          ListDetailNavDisplay(
            entries = tabEntries,
            isSplitPane = isSplitPane,
            paneAnchor = paneAnchor,
            onBack = { mainNavigationViewModel.onEvent(MainNavigationEvents.ListDetailEvent(ListDetailEvents.Back)) },
            onExitDetail = { mainNavigationViewModel.onEvent(MainNavigationEvents.ExitDetail) },
            layout = paneLayout,
            listPaneChrome = listPaneChrome,
            emptyDetailContent = emptyDetailContent,
            modifier = Modifier.padding(paddingValues)
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

    lifecycleDisposable += vitalsViewModel.vitalsState.subscribe(this::presentVitalsState)
  }

  /**
   * Builds the geometry for the list/detail split and keeps it following the view-model's anchor.
   */
  @Composable
  private fun rememberMainPaneLayout(
    contentLayoutData: ListDetailPaneMetrics,
    maxWidth: Dp,
    toolbarMode: MainToolbarMode,
    paneAnchor: PaneAnchor
  ): ListDetailPaneLayout {
    val navigationType = NavigationType.rememberNavigationType()

    return rememberListDetailPaneLayout(
      paneAnchor = paneAnchor,
      maxWidth = maxWidth,
      onAnchorSelected = { mainNavigationViewModel.onEvent(MainNavigationEvents.ListDetailEvent(ListDetailEvents.AnchorSelected(it))) },
      metrics = contentLayoutData,
      // Searching hides the rail, leaving nothing of the list pane behind once the detail fills the window.
      collapsedListWidth = when {
        toolbarMode == MainToolbarMode.SEARCH -> 0.dp
        navigationType == NavigationType.BAR -> 0.dp
        else -> RAIL_WIDTH
      }
    )
  }

  /**
   * The chrome belonging to the list pane — navigation rail or bar, toolbar, and the floating buttons and
   * megaphones layered over the list — wrapped around [content].
   *
   * Handed to [ListDetailNavDisplay] as a [ListPaneChrome] rather than as a scene parameter: a scene excludes
   * its content lambda from equality, so anything captured there would go stale when an equal instance is
   * retained.
   */
  @Composable
  private fun MainListPaneChrome(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
  ) {
    val mainToolbarState by toolbarViewModel.state.collectAsStateWithLifecycle()
    val mainNavigationState by mainNavigationViewModel.mainNavigationBarState.collectAsStateWithLifecycle()
    val megaphone by mainNavigationViewModel.megaphone.collectAsStateWithLifecycle()

    val isSplitPane = LocalResources.current.rememberIsSplitPane()
    val contentLayoutData = rememberListDetailPaneMetrics(listPaddingStart = mainToolbarState.mode.listPaddingStart)
    val navigationType = NavigationType.rememberNavigationType()

    val bottomChromeState = remember(mainToolbarState.destination, mainToolbarState.mode, megaphone) {
      MainBottomChromeState(
        destination = mainToolbarState.destination,
        mainToolbarMode = mainToolbarState.mode,
        megaphoneState = MainMegaphoneState(
          megaphone = megaphone,
          mainToolbarMode = mainToolbarState.mode
        )
      )
    }

    val listContainerColor = if (isSplitPane) {
      SignalTheme.colors.colorSurface1
    } else {
      MaterialTheme.colorScheme.surface
    }

    Row(modifier = modifier.fillMaxSize()) {
      if (navigationType == NavigationType.RAIL && mainToolbarState.mode != MainToolbarMode.SEARCH) {
        MainNavigationRail(
          state = mainNavigationState,
          mainFloatingActionButtonsCallback = mainBottomChromeCallback,
          onDestinationSelected = mainNavigationCallback
        )
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxSize()
          .background(listContainerColor, contentLayoutData.shape)
          .clip(contentLayoutData.shape)
      ) {
        MainToolbar(
          state = mainToolbarState,
          callback = toolbarCallback
        )

        Box(modifier = Modifier.weight(1f)) {
          content()

          MainBottomChrome(
            state = bottomChromeState,
            callback = mainBottomChromeCallback,
            megaphoneActionController = megaphoneActionController,
            modifier = Modifier.align(Alignment.BottomCenter)
          )
        }

        if (navigationType == NavigationType.BAR && mainToolbarState.mode == MainToolbarMode.FULL) {
          Column(
            modifier = Modifier
              .clip(contentLayoutData.navigationBarShape)
              .background(color = SignalTheme.colors.colorSurface2)
          ) {
            MainNavigationBar(
              state = mainNavigationState,
              onDestinationSelected = mainNavigationCallback
            )

            if (!isSplitPane) {
              Spacer(Modifier.navigationBarsPadding())
            }
          }
        }
      }
    }
  }

  @Composable
  private fun MainContainer(content: @Composable BoxWithConstraintsScope.() -> Unit) {
    val isSplitPane = LocalResources.current.rememberIsSplitPane()

    CompositionLocalProvider(LocalSnackbarStateConsumerRegistry provides mainNavigationViewModel.snackbarRegistry) {
      SignalTheme {
        val backgroundColor = if (!isSplitPane) {
          MaterialTheme.colorScheme.surface
        } else {
          SignalTheme.colors.colorSurface1
        }

        val modifier = when {
          isSplitPane -> {
            Modifier
              .systemBarsPadding()
              .displayCutoutPadding()
          }

          else ->
            Modifier
              .windowInsetsPadding(
                WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                  .add(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
              )
        }

        BoxWithConstraints(
          modifier = Modifier
            .background(color = backgroundColor)
            .then(modifier)
        ) {
          content()
        }
      }
    }
  }

  override fun getIntent(): Intent {
    return super.getIntent().setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleDeepLinkIntent(intent)

    val extras = intent.extras ?: return

    if (extras.getBoolean(KEY_EXIT_DETAIL, false)) {
      mainNavigationViewModel.onEvent(MainNavigationEvents.ExitDetail)
      return
    }

    val detailLocation = extras.getParcelableCompat(KEY_DETAIL_LOCATION, MainDetailRoute::class.java)
    if (detailLocation != null) {
      mainNavigationViewModel.onEvent(MainNavigationEvents.GoToDetail(detailLocation))
      return
    }

    val startingTab = extras.getSerializableCompat(KEY_STARTING_TAB, MainListRoute::class.java) ?: return

    if (startingTab != MainListRoute.Stories || Stories.isFeatureEnabled()) {
      mainNavigationViewModel.onEvent(MainNavigationEvents.GoToTab(startingTab))
    }
  }

  override fun onPreCreate() {
    super.onPreCreate()
    dynamicTheme.onCreate(this)
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)

    toolbarViewModel.refresh()

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

    vitalsViewModel.checkSlowNotificationHeuristics()
    mainNavigationViewModel.onEvent(MainNavigationEvents.RefreshNavigationBar)

    CallQuality.consumeQualityRequest()?.let {
      CallQualityBottomSheetFragment.create(it).show(supportFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  override fun onStop() {
    super.onStop()
    SplashScreenUtil.setSplashScreenThemeIfNecessary(this, SignalStore.settings.theme)
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == MainNavigator.REQUEST_CONFIG_CHANGES && resultCode == RESULT_CONFIG_CHANGED) {
      recreate()
    }

    if (resultCode == RESULT_OK && requestCode == CreateSvrPinActivity.REQUEST_NEW_PIN) {
      mainNavigationViewModel.snackbarRegistry.emit(SnackbarState(message = getString(R.string.ConfirmKbsPinFragment__pin_created), hostKey = MainSnackbarHostKey.MainChrome))
      mainNavigationViewModel.onEvent(MainNavigationEvents.MegaphoneCompleted(Megaphones.Event.PINS_FOR_ALL))
    }

    if (resultCode == RESULT_OK && requestCode == UsernameEditFragment.REQUEST_CODE) {
      val snackbarString = getString(R.string.ConversationListFragment_username_recovered_toast, SignalStore.account.username)
      mainNavigationViewModel.snackbarRegistry.emit(
        SnackbarState(
          message = snackbarString,
          hostKey = MainSnackbarHostKey.MainChrome
        )
      )
    }

    if (resultCode == RESULT_OK && requestCode == VerifyBackupKeyActivity.REQUEST_CODE) {
      mainNavigationViewModel.snackbarRegistry.emit(
        SnackbarState(
          message = getString(R.string.VerifyBackupKey__backup_key_correct),
          duration = Snackbars.Duration.SHORT,
          hostKey = MainSnackbarHostKey.MainChrome
        )
      )
      mainNavigationViewModel.onEvent(MainNavigationEvents.MegaphoneSnoozed(Megaphones.Event.VERIFY_BACKUP_KEY))
    }

    if (requestCode == AppSettingsActivity.REQUEST_CODE_UPGRADE_LOCAL_BACKUPS) {
      mainNavigationViewModel.onEvent(MainNavigationEvents.MegaphoneSnoozed(Megaphones.Event.USE_NEW_ON_DEVICE_BACKUPS))
    }
  }

  override fun onFirstRender() {
    onFirstRender = true
  }

  override fun getNavigator(): MainNavigator {
    return navigator
  }

  override fun bindScrollHelper(recyclerView: RecyclerView, lifecycleOwner: LifecycleOwner) {
    Material3OnScrollHelper(
      activity = this,
      views = listOf(),
      viewStubs = listOf(),
      onSetToolbarColor = {
        toolbarViewModel.setToolbarColor(it)
      },
      lifecycleOwner = lifecycleOwner
    ).attach(recyclerView)
  }

  override fun bindScrollHelper(recyclerView: RecyclerView, lifecycleOwner: LifecycleOwner, chatFolders: RecyclerView, setChatFolder: (Int) -> Unit) {
    Material3OnScrollHelper(
      activity = this,
      views = listOf(chatFolders),
      viewStubs = listOf(),
      onSetToolbarColor = {
        toolbarViewModel.setToolbarColor(it)
      },
      lifecycleOwner = lifecycleOwner,
      setChatFolderColor = setChatFolder
    ).attach(recyclerView)
  }

  override fun updateProxyStatus(state: WebSocketConnectionState) {
    if (SignalStore.proxy.isProxyEnabled) {
      val proxyState: MainToolbarState.ProxyState = when (state) {
        WebSocketConnectionState.CONNECTING, WebSocketConnectionState.DISCONNECTING, WebSocketConnectionState.DISCONNECTED -> MainToolbarState.ProxyState.CONNECTING
        WebSocketConnectionState.CONNECTED -> MainToolbarState.ProxyState.CONNECTED
        WebSocketConnectionState.AUTHENTICATION_FAILED, WebSocketConnectionState.FAILED, WebSocketConnectionState.REMOTE_DEPRECATED -> MainToolbarState.ProxyState.FAILED
        else -> MainToolbarState.ProxyState.NONE
      }

      toolbarViewModel.setProxyState(proxyState = proxyState)
    } else {
      toolbarViewModel.setProxyState(proxyState = MainToolbarState.ProxyState.NONE)
    }
  }

  override fun onMultiSelectStarted() {
    toolbarViewModel.presentToolbarForMultiselect()
  }

  override fun onMultiSelectFinished() {
    toolbarViewModel.presentToolbarForCurrentDestination()
  }

  private fun handleDeepLinkIntent(intent: Intent) {
    handleConversationIntent(intent)
    handleGroupLinkInIntent(intent)
    handleProxyInIntent(intent)
    handleSignalMeIntent(intent)
    handleCallLinkInIntent(intent)
    handleDonateReturnIntent(intent)
    handleQuickRestoreIntent(intent)
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

  private fun handleConversationIntent(intent: Intent) {
    if (ConversationIntents.isConversationIntent(intent)) {
      if (!isTrustedConversationIntent(intent)) {
        Log.w(TAG, "Received a conversation intent through an exported entry point. Ignoring its extras.")
        intent.action = null
        setIntent(intent)
        return
      }

      val extras = intent.extras
      if (extras == null) {
        Log.w(TAG, "Received a conversation intent with no extras. Ignoring it.")
        intent.action = null
        setIntent(intent)
        return
      }

      mainNavigationViewModel.onEvent(MainNavigationEvents.GoToList(MainListRoute.Chats))
      mainNavigationViewModel.onEvent(MainNavigationEvents.GoToDetail(MainDetailRoute.Conversation(ConversationIntents.readArgsFromBundle(extras))))
      intent.action = null
      setIntent(intent)
    }
  }

  /**
   * While MainActivity isn't exporting, we have launcher aliases that are, so we verify that someone isn't launching us through those befre
   * respecting various intent attributes.
   */
  private fun isTrustedConversationIntent(intent: Intent): Boolean {
    return intent.component?.className == MainActivity::class.java.name
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

  private fun handleQuickRestoreIntent(intent: Intent) {
    intent.data?.let { data ->
      CommunicationActions.handlePotentialQuickRestoreUrl(this, data.toString()) {
        onCameraClick(MainListRoute.Chats, isForQuickRestore = true)
      }
    }
  }

  private fun updateNotificationProfileStatus(notificationProfiles: List<NotificationProfile>) {
    val activeProfile = NotificationProfiles.getActiveProfile(profiles = notificationProfiles, shouldSync = true)
    if (activeProfile != null) {
      if (activeProfile.id != SignalStore.notificationProfile.lastProfilePopup) {
        val view = findViewById<ViewGroup>(android.R.id.content)

        view.postDelayed({
          try {
            var fragmentView = view ?: return@postDelayed

            SignalStore.notificationProfile.lastProfilePopup = activeProfile.id
            SignalStore.notificationProfile.lastProfilePopupTime = System.currentTimeMillis()

            if (previousTopToastPopup?.isShowing == true) {
              previousTopToastPopup?.dismiss()
            }

            val fragment = supportFragmentManager.findFragmentByTag(BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
            if (fragment != null && fragment.isAdded && fragment.view != null) {
              fragmentView = fragment.requireView() as ViewGroup
            }

            previousTopToastPopup = TopToastPopup.show(fragmentView, R.drawable.ic_moon_16, getString(R.string.ConversationListFragment__s_on, activeProfile.name))
          } catch (e: Exception) {
            Log.w(TAG, "Unable to show toast popup", e)
          }
        }, 500L)
      }
      toolbarViewModel.setNotificationProfileEnabled(true)
    } else {
      toolbarViewModel.setNotificationProfileEnabled(false)
    }

    if (!SignalStore.notificationProfile.hasSeenTooltip && Util.hasItems(notificationProfiles)) {
      toolbarViewModel.setShowNotificationProfilesTooltip(true)
    }
  }

  private fun onCameraClick(destination: MainListRoute, isForQuickRestore: Boolean) {
    val onGranted = {
      if (isForQuickRestore) {
        startActivity(MediaSendLauncher.cameraForQuickRestore(context = this@MainActivity))
      } else {
        startActivity(
          MediaSendLauncher.camera(
            context = this@MainActivity,
            isStory = destination == MainListRoute.Stories
          )
        )
      }
    }

    onGranted()
  }

  inner class ToolbarCallback : MainToolbarCallback {

    override fun onNewGroupClick() {
      startActivity(CreateGroupActivity.createIntent(this@MainActivity))
    }

    override fun onClearPassphraseClick() {
      val intent = Intent(this@MainActivity, KeyCachingService::class.java)
      intent.setAction(KeyCachingService.CLEAR_KEY_ACTION)
      startService(intent)
    }

    override fun onMarkReadClick() {
      toolbarViewModel.markAllMessagesRead()
    }

    override fun onFilterUnreadChatsClick() {
      toolbarViewModel.setChatFilter(ConversationFilter.UNREAD)
    }

    override fun onClearUnreadChatsFilterClick() {
      toolbarViewModel.setChatFilter(ConversationFilter.OFF)
    }

    override fun onOpenArchiveClick() {
      mainNavigationViewModel.onEvent(MainNavigationEvents.GoToTab(MainListRoute.Archive))
    }

    override fun onStarredMessagesClick() {
      startActivity(StarredMessagesActivity.createIntent(this@MainActivity))
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
      toolbarViewModel.setToolbarMode(MainToolbarMode.SEARCH)
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
      mainNavigationViewModel.onEvent(MainNavigationEvents.GoToDetail(MainDetailRoute.Stories.PrivacySettings))
    }

    override fun onStoryArchiveClick() {
      mainNavigationViewModel.onEvent(MainNavigationEvents.GoToDetail(MainDetailRoute.Stories.Archive))
    }

    override fun onCloseSearchClick() {
      toolbarViewModel.setToolbarMode(MainToolbarMode.FULL)
    }

    override fun onCloseArchiveClick() {
      toolbarViewModel.emitEvent(MainToolbarViewModel.Event.Chats.CloseArchive)
    }

    override fun onCloseActionModeClick() {
      supportFragmentManager.fragments.forEach { fragment ->
        when (fragment) {
          is ConversationListFragment -> fragment.endActionModeIfActive()
          is CallLogFragment -> fragment.CallLogActionModeCallback().onActionModeWillEnd()
        }
      }
    }

    override fun onSearchQueryUpdated(query: String) {
      toolbarViewModel.setSearchQuery(query)
    }

    override fun onSearchFilterClick() {
      supportFragmentManager.fragments.forEach { fragment ->
        if (fragment is ConversationListFragment) {
          fragment.showSearchFilterBottomSheet()
        }
      }
    }

    override fun onNotificationProfileTooltipDismissed() {
      SignalStore.notificationProfile.hasSeenTooltip = true
      toolbarViewModel.setShowNotificationProfilesTooltip(false)
    }
  }

  inner class BottomChromeCallback : MainBottomChromeCallback {
    override fun onNewChatClick() {
      startActivity(NewConversationActivity.createIntent(this@MainActivity))
    }

    override fun onNewCallClick() {
      startActivity(NewCallActivity.createIntent(this@MainActivity))
    }

    override fun onCameraClick(destination: MainListRoute) {
      onCameraClick(destination, false)
    }

    override fun onMegaphoneVisible(megaphone: Megaphone) {
      mainNavigationViewModel.onEvent(MainNavigationEvents.MegaphoneVisible(megaphone))
    }

    override fun onSnackbarDismissed() = Unit
  }

  inner class MainMegaphoneActionController : MegaphoneActionController {
    override fun onMegaphoneNavigationRequested(intent: Intent) {
      startActivity(intent)
    }

    override fun onMegaphoneNavigationRequested(intent: Intent, requestCode: Int) {
      startActivityForResult(intent, requestCode)
    }

    override fun onMegaphoneToastRequested(string: String) {
      mainNavigationViewModel.snackbarRegistry.emit(
        SnackbarState(
          message = string,
          hostKey = MainSnackbarHostKey.MainChrome
        )
      )
    }

    override fun getMegaphoneActivity(): Activity {
      return this@MainActivity
    }

    override fun onMegaphoneSnooze(event: Megaphones.Event) {
      mainNavigationViewModel.onEvent(MainNavigationEvents.MegaphoneSnoozed(event))
    }

    override fun onMegaphoneCompleted(event: Megaphones.Event) {
      mainNavigationViewModel.onEvent(MainNavigationEvents.MegaphoneCompleted(event))
    }

    override fun onMegaphoneDialogFragmentRequested(dialogFragment: DialogFragment) {
      dialogFragment.show(supportFragmentManager, "megaphone_dialog")
    }
  }

  override fun onEvent(event: MainNavigationEvents) = mainNavigationViewModel.onEvent(event)
}
