/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.rememberFragmentState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.getSerializableCompat
import org.signal.core.util.logging.Log
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgress
import org.thoughtcrime.securesms.backup.v2.ui.verify.VerifyBackupKeyActivity
import org.thoughtcrime.securesms.calls.YouAreAlreadyInACallSnackbar.show
import org.thoughtcrime.securesms.calls.links.details.CallLinkDetailsActivity
import org.thoughtcrime.securesms.calls.log.CallLogFilter
import org.thoughtcrime.securesms.calls.log.CallLogFragment
import org.thoughtcrime.securesms.calls.new.NewCallActivity
import org.thoughtcrime.securesms.components.DebugLogsPromptDialogFragment
import org.thoughtcrime.securesms.components.PromptBatterySaverDialogFragment
import org.thoughtcrime.securesms.components.compose.ConnectivityWarningBottomSheet
import org.thoughtcrime.securesms.components.compose.DeviceSpecificNotificationBottomSheet
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity.Companion.manageSubscriptions
import org.thoughtcrime.securesms.components.settings.app.notifications.manual.NotificationProfileSelectionFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.GooglePayComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.GooglePayRepository
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.compose.SignalTheme
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.NewConversationActivity
import org.thoughtcrime.securesms.conversation.v2.MotionEventRelay
import org.thoughtcrime.securesms.conversation.v2.ShareDataTimestampViewModel
import org.thoughtcrime.securesms.conversationlist.ConversationListArchiveFragment
import org.thoughtcrime.securesms.conversationlist.ConversationListFragment
import org.thoughtcrime.securesms.conversationlist.RelinkDevicesReminderBottomSheetFragment
import org.thoughtcrime.securesms.conversationlist.RestoreCompleteBottomSheetDialog
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.conversationlist.model.UnreadPaymentsLiveData
import org.thoughtcrime.securesms.devicetransfer.olddevice.OldDeviceExitActivity
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupActivity
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.lock.v2.CreateSvrPinActivity
import org.thoughtcrime.securesms.main.ChatNavGraphState
import org.thoughtcrime.securesms.main.DetailsScreenNavHost
import org.thoughtcrime.securesms.main.MainBottomChrome
import org.thoughtcrime.securesms.main.MainBottomChromeCallback
import org.thoughtcrime.securesms.main.MainBottomChromeState
import org.thoughtcrime.securesms.main.MainContentLayoutData
import org.thoughtcrime.securesms.main.MainMegaphoneState
import org.thoughtcrime.securesms.main.MainNavigationBar
import org.thoughtcrime.securesms.main.MainNavigationDetailLocation
import org.thoughtcrime.securesms.main.MainNavigationListLocation
import org.thoughtcrime.securesms.main.MainNavigationRail
import org.thoughtcrime.securesms.main.MainNavigationViewModel
import org.thoughtcrime.securesms.main.MainToolbar
import org.thoughtcrime.securesms.main.MainToolbarCallback
import org.thoughtcrime.securesms.main.MainToolbarMode
import org.thoughtcrime.securesms.main.MainToolbarState
import org.thoughtcrime.securesms.main.MainToolbarViewModel
import org.thoughtcrime.securesms.main.Material3OnScrollHelperBinder
import org.thoughtcrime.securesms.main.SnackbarState
import org.thoughtcrime.securesms.main.callNavGraphBuilder
import org.thoughtcrime.securesms.main.chatNavGraphBuilder
import org.thoughtcrime.securesms.main.navigateToDetailLocation
import org.thoughtcrime.securesms.main.rememberDetailNavHostController
import org.thoughtcrime.securesms.main.rememberFocusRequester
import org.thoughtcrime.securesms.main.rememberMainNavigationDetailLocation
import org.thoughtcrime.securesms.main.storiesNavGraphBuilder
import org.thoughtcrime.securesms.mediasend.camerax.CameraXUtil
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionActivity
import org.thoughtcrime.securesms.megaphone.Megaphone
import org.thoughtcrime.securesms.megaphone.MegaphoneActionController
import org.thoughtcrime.securesms.megaphone.Megaphones
import org.thoughtcrime.securesms.net.DeviceTransferBlockingInterceptor
import org.thoughtcrime.securesms.notifications.VitalsViewModel
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.profiles.manage.UsernameEditFragment
import org.thoughtcrime.securesms.service.BackupMediaRestoreService
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.stories.landing.StoriesLandingFragment
import org.thoughtcrime.securesms.stories.settings.StorySettingsActivity
import org.thoughtcrime.securesms.util.AppForegroundObserver
import org.thoughtcrime.securesms.util.AppStartup
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.CachedInflater
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.Material3OnScrollHelper
import org.thoughtcrime.securesms.util.SplashScreenUtil
import org.thoughtcrime.securesms.util.TopToastPopup
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.viewModel
import org.thoughtcrime.securesms.window.AppPaneDragHandle
import org.thoughtcrime.securesms.window.AppScaffold
import org.thoughtcrime.securesms.window.AppScaffoldAnimationStateFactory
import org.thoughtcrime.securesms.window.AppScaffoldNavigator
import org.thoughtcrime.securesms.window.NavigationType
import org.thoughtcrime.securesms.window.isSplitPane
import org.thoughtcrime.securesms.window.rememberThreePaneScaffoldNavigatorDelegate
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState

class MainActivity : PassphraseRequiredActivity(), VoiceNoteMediaControllerOwner, MainNavigator.NavigatorProvider, Material3OnScrollHelperBinder, ConversationListFragment.Callback, CallLogFragment.Callback, GooglePayComponent {

  companion object {
    private val TAG = Log.tag(MainActivity::class)

    private const val KEY_STARTING_TAB = "STARTING_TAB"
    const val RESULT_CONFIG_CHANGED = Activity.RESULT_FIRST_USER + 901

    @JvmStatic
    fun clearTop(context: Context): Intent {
      return Intent(context, MainActivity::class.java)
        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    @JvmStatic
    fun clearTopAndOpenTab(context: Context, startingTab: MainNavigationListLocation): Intent {
      return clearTop(context).putExtra(KEY_STARTING_TAB, startingTab)
    }
  }

  private val dynamicTheme = DynamicNoActionBarTheme()
  private val lifecycleDisposable = LifecycleDisposable()

  private lateinit var mediaController: VoiceNoteMediaController
  private lateinit var navigator: MainNavigator

  override val voiceNoteMediaController: VoiceNoteMediaController
    get() = mediaController

  private val mainNavigationViewModel: MainNavigationViewModel by viewModel {
    val startingTab = intent.extras?.getSerializableCompat(KEY_STARTING_TAB, MainNavigationListLocation::class.java)
    MainNavigationViewModel(startingTab ?: MainNavigationListLocation.CHATS)
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
  private val shareDataTimestampViewModel: ShareDataTimestampViewModel by viewModels()

  private val motionEventRelay: MotionEventRelay by viewModels()

  private var onFirstRender = false
  private var previousTopToastPopup: TopToastPopup? = null

  private val mainBottomChromeCallback = BottomChromeCallback()
  private val megaphoneActionController = MainMegaphoneActionController()
  private val mainNavigationCallback = MainNavigationCallback()

  override val googlePayRepository: GooglePayRepository by lazy { GooglePayRepository(this) }
  override val googlePayResultPublisher: Subject<GooglePayComponent.GooglePayResult> = PublishSubject.create()

  override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
    return motionEventRelay.offer(ev) || super.dispatchTouchEvent(ev)
  }

  @OptIn(ExperimentalMaterial3AdaptiveApi::class)
  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    AppStartup.getInstance().onCriticalRenderEventStart()

    enableEdgeToEdge(
      navigationBarStyle = if (DynamicTheme.isDarkTheme(this)) {
        SystemBarStyle.dark(0)
      } else {
        SystemBarStyle.light(0, 0)
      }
    )

    super.onCreate(savedInstanceState, ready)
    navigator = MainNavigator(this, mainNavigationViewModel)

    AppForegroundObserver.addListener(object : AppForegroundObserver.Listener {
      override fun onForeground() {
        mainNavigationViewModel.getNextMegaphone()
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
                mainBottomChromeCallback.onCameraClick(MainNavigationListLocation.STORIES)
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
    }

    shareDataTimestampViewModel.setTimestampFromActivityCreation(savedInstanceState, intent)

    setContent {
      val snackbar by mainNavigationViewModel.snackbar.collectAsStateWithLifecycle()
      val mainToolbarState by toolbarViewModel.state.collectAsStateWithLifecycle()
      val megaphone by mainNavigationViewModel.megaphone.collectAsStateWithLifecycle()
      val mainNavigationState by mainNavigationViewModel.mainNavigationState.collectAsStateWithLifecycle()

      LaunchedEffect(mainNavigationState.currentListLocation) {
        when (mainNavigationState.currentListLocation) {
          MainNavigationListLocation.CHATS -> toolbarViewModel.presentToolbarForConversationListFragment()
          MainNavigationListLocation.ARCHIVE -> toolbarViewModel.presentToolbarForConversationListArchiveFragment()
          MainNavigationListLocation.CALLS -> toolbarViewModel.presentToolbarForCallLogFragment()
          MainNavigationListLocation.STORIES -> toolbarViewModel.presentToolbarForStoriesLandingFragment()
        }
      }

      val isActionModeActive = mainToolbarState.mode == MainToolbarMode.ACTION_MODE
      val isSearchModeActive = mainToolbarState.mode == MainToolbarMode.SEARCH
      val isNavigationRailVisible = mainToolbarState.mode != MainToolbarMode.SEARCH
      val isNavigationBarVisible = mainToolbarState.mode == MainToolbarMode.FULL
      val isBackHandlerEnabled = mainToolbarState.destination != MainNavigationListLocation.CHATS && !isActionModeActive && !isSearchModeActive

      BackHandler(enabled = isBackHandlerEnabled) {
        mainNavigationViewModel.setFocusedPane(ThreePaneScaffoldRole.Secondary)
        mainNavigationViewModel.goTo(MainNavigationListLocation.CHATS)
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

      val mainBottomChromeState = remember(mainToolbarState.destination, snackbar, mainToolbarState.mode, megaphone) {
        MainBottomChromeState(
          destination = mainToolbarState.destination,
          snackbarState = snackbar,
          mainToolbarMode = mainToolbarState.mode,
          megaphoneState = MainMegaphoneState(
            megaphone = megaphone,
            mainToolbarMode = mainToolbarState.mode
          )
        )
      }

      val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
      val contentLayoutData = MainContentLayoutData.rememberContentLayoutData(mainToolbarState.mode)

      MainContainer {
        val wrappedNavigator = rememberNavigator(windowSizeClass, contentLayoutData, maxWidth)
        val listPaneWidth = contentLayoutData.rememberDefaultPanePreferredWidth(maxWidth)
        val navigationType = NavigationType.rememberNavigationType()

        val anchors = remember(contentLayoutData, mainToolbarState) {
          val halfPartitionWidth = contentLayoutData.partitionWidth / 2

          val detailOffset = when {
            mainToolbarState.mode == MainToolbarMode.SEARCH -> 0.dp
            navigationType == NavigationType.BAR -> 0.dp
            else -> 80.dp
          }

          val detailOnlyAnchor = PaneExpansionAnchor.Offset.fromStart(detailOffset + contentLayoutData.listPaddingStart + halfPartitionWidth)
          val detailAndListAnchor = PaneExpansionAnchor.Offset.fromStart(listPaneWidth + halfPartitionWidth)
          val listOnlyAnchor = PaneExpansionAnchor.Offset.fromEnd(contentLayoutData.detailPaddingEnd - halfPartitionWidth)

          listOf(detailOnlyAnchor, detailAndListAnchor, listOnlyAnchor)
        }

        val (detailOnlyAnchor, detailAndListAnchor, listOnlyAnchor) = anchors

        val paneExpansionState = rememberPaneExpansionState(
          key = wrappedNavigator.scaffoldValue.paneExpansionStateKey,
          anchors = anchors,
          initialAnchoredIndex = 1
        )

        val paneAnchorIndex = rememberSaveable(paneExpansionState.currentAnchor) {
          anchors.indexOf(paneExpansionState.currentAnchor)
        }

        LaunchedEffect(windowSizeClass) {
          val index = when {
            paneAnchorIndex < 0 -> 1
            paneAnchorIndex > anchors.lastIndex -> anchors.lastIndex
            else -> paneAnchorIndex
          }

          if (index in anchors.indices) {
            val anchor = anchors[index]
            paneExpansionState.animateTo(anchor)
          }
        }

        val chatNavGraphState = ChatNavGraphState.remember(windowSizeClass)
        val mutableInteractionSource = remember { MutableInteractionSource() }
        val mainNavigationDetailLocation by rememberMainNavigationDetailLocation(mainNavigationViewModel, chatNavGraphState::writeGraphicsLayerToBitmap)

        val chatsNavHostController = rememberDetailNavHostController(
          onRequestFocus = rememberFocusRequester(
            mainNavigationViewModel = mainNavigationViewModel,
            currentListLocation = mainNavigationState.currentListLocation,
            isTargetListLocation = { it in listOf(MainNavigationListLocation.CHATS, MainNavigationListLocation.ARCHIVE) }
          )
        ) {
          chatNavGraphBuilder(chatNavGraphState)
        }

        val callsNavHostController = rememberDetailNavHostController(
          onRequestFocus = rememberFocusRequester(
            mainNavigationViewModel = mainNavigationViewModel,
            currentListLocation = mainNavigationState.currentListLocation
          ) { it == MainNavigationListLocation.CALLS }
        ) {
          callNavGraphBuilder(it)
        }

        val storiesNavHostController = rememberDetailNavHostController(
          onRequestFocus = rememberFocusRequester(
            mainNavigationViewModel = mainNavigationViewModel,
            currentListLocation = mainNavigationState.currentListLocation
          ) { it == MainNavigationListLocation.STORIES }
        ) {
          storiesNavGraphBuilder()
        }

        LaunchedEffect(mainNavigationDetailLocation) {
          mainNavigationViewModel.clearEarlyDetailLocation()
          when (mainNavigationDetailLocation) {
            is MainNavigationDetailLocation.Empty -> {
              when (mainNavigationState.currentListLocation) {
                MainNavigationListLocation.CHATS, MainNavigationListLocation.ARCHIVE -> chatsNavHostController
                MainNavigationListLocation.CALLS -> callsNavHostController
                MainNavigationListLocation.STORIES -> storiesNavHostController
              }.navigateToDetailLocation(mainNavigationDetailLocation)
            }

            is MainNavigationDetailLocation.Chats -> {
              chatNavGraphState.writeGraphicsLayerToBitmap()
              chatsNavHostController.navigateToDetailLocation(mainNavigationDetailLocation)
            }

            is MainNavigationDetailLocation.Calls -> callsNavHostController.navigateToDetailLocation(mainNavigationDetailLocation)
            is MainNavigationDetailLocation.Stories -> storiesNavHostController.navigateToDetailLocation(mainNavigationDetailLocation)
          }
        }

        val scope = rememberCoroutineScope()
        BackHandler(paneExpansionState.currentAnchor == detailOnlyAnchor) {
          scope.launch {
            paneExpansionState.animateTo(listOnlyAnchor)
          }
        }

        LaunchedEffect(paneExpansionState.currentAnchor, detailOnlyAnchor, listOnlyAnchor, detailAndListAnchor) {
          val isFullScreenPane = when (paneExpansionState.currentAnchor) {
            listOnlyAnchor, detailOnlyAnchor -> {
              true
            }

            else -> {
              false
            }
          }

          mainNavigationViewModel.onPaneAnchorChanged(isFullScreenPane)
        }

        LaunchedEffect(paneExpansionState.currentAnchor) {
          when (paneExpansionState.currentAnchor) {
            listOnlyAnchor -> {
              mainNavigationViewModel.setFocusedPane(ThreePaneScaffoldRole.Secondary)
            }

            detailOnlyAnchor -> {
              mainNavigationViewModel.setFocusedPane(ThreePaneScaffoldRole.Primary)
            }

            else -> Unit
          }
        }

        val paneFocusRequest by mainNavigationViewModel.paneFocusRequests.collectAsStateWithLifecycle(null)
        LaunchedEffect(paneFocusRequest) {
          if (paneFocusRequest == null) {
            return@LaunchedEffect
          }

          if (paneFocusRequest == ThreePaneScaffoldRole.Secondary && paneExpansionState.currentAnchor == detailOnlyAnchor) {
            paneExpansionState.animateTo(listOnlyAnchor)
          }

          if (paneFocusRequest == ThreePaneScaffoldRole.Primary && paneExpansionState.currentAnchor == listOnlyAnchor) {
            paneExpansionState.animateTo(detailOnlyAnchor)
          }
        }

        val noEnterTransitionFactory = remember {
          AppScaffoldAnimationStateFactory(
            enabledStates = AppScaffoldNavigator.NavigationState.entries.filterNot {
              it == AppScaffoldNavigator.NavigationState.ENTER
            }.toSet()
          )
        }

        AppScaffold(
          navigator = wrappedNavigator,
          modifier = chatNavGraphState.writeContentToGraphicsLayer(),
          paneExpansionState = paneExpansionState,
          contentWindowInsets = WindowInsets(),
          bottomNavContent = {
            if (isNavigationBarVisible) {
              Column(
                modifier = Modifier
                  .clip(contentLayoutData.navigationBarShape)
                  .background(color = SignalTheme.colors.colorSurface2)
              ) {
                MainNavigationBar(
                  state = mainNavigationState,
                  onDestinationSelected = mainNavigationCallback
                )

                if (!windowSizeClass.isSplitPane()) {
                  Spacer(Modifier.navigationBarsPadding())
                }
              }
            }
          },
          navRailContent = {
            if (isNavigationRailVisible) {
              MainNavigationRail(
                state = mainNavigationState,
                mainFloatingActionButtonsCallback = mainBottomChromeCallback,
                onDestinationSelected = mainNavigationCallback
              )
            }
          },
          secondaryContent = {
            val listContainerColor = if (windowSizeClass.isSplitPane() && windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM) {
              SignalTheme.colors.colorSurface1
            } else {
              MaterialTheme.colorScheme.surface
            }

            Column(
              modifier = Modifier
                .padding(start = contentLayoutData.listPaddingStart)
                .fillMaxSize()
                .background(listContainerColor, contentLayoutData.shape)
                .clip(contentLayoutData.shape)
            ) {
              MainToolbar(
                state = mainToolbarState,
                callback = toolbarCallback
              )

              Box(
                modifier = Modifier.weight(1f)
              ) {
                when (val destination = mainNavigationState.currentListLocation) {
                  MainNavigationListLocation.CHATS -> {
                    val state = key(destination) { rememberFragmentState() }
                    AndroidFragment(
                      clazz = ConversationListFragment::class.java,
                      fragmentState = state,
                      modifier = Modifier.fillMaxSize()
                    )
                  }

                  MainNavigationListLocation.ARCHIVE -> {
                    val state = key(destination) { rememberFragmentState() }
                    AndroidFragment(
                      clazz = ConversationListArchiveFragment::class.java,
                      fragmentState = state,
                      modifier = Modifier.fillMaxSize()
                    )
                  }

                  MainNavigationListLocation.CALLS -> {
                    val state = key(destination) { rememberFragmentState() }
                    AndroidFragment(
                      clazz = CallLogFragment::class.java,
                      fragmentState = state,
                      modifier = Modifier.fillMaxSize()
                    )
                  }

                  MainNavigationListLocation.STORIES -> {
                    val state = key(destination) { rememberFragmentState() }
                    AndroidFragment(
                      clazz = StoriesLandingFragment::class.java,
                      fragmentState = state,
                      modifier = Modifier.fillMaxSize()
                    )
                  }
                }

                MainBottomChrome(
                  state = mainBottomChromeState,
                  callback = mainBottomChromeCallback,
                  megaphoneActionController = megaphoneActionController,
                  modifier = Modifier.align(Alignment.BottomCenter)
                )
              }
            }
          },
          primaryContent = {
            when (mainNavigationState.currentListLocation) {
              MainNavigationListLocation.CHATS, MainNavigationListLocation.ARCHIVE -> {
                DetailsScreenNavHost(
                  navHostController = chatsNavHostController,
                  contentLayoutData = contentLayoutData
                )
              }

              MainNavigationListLocation.CALLS -> {
                DetailsScreenNavHost(
                  navHostController = callsNavHostController,
                  contentLayoutData = contentLayoutData
                )
              }

              MainNavigationListLocation.STORIES -> {
                DetailsScreenNavHost(
                  navHostController = storiesNavHostController,
                  contentLayoutData = contentLayoutData
                )
              }
            }
          },
          paneExpansionDragHandle = if (contentLayoutData.hasDragHandle()) {
            {
              AppPaneDragHandle(
                paneExpansionState = paneExpansionState,
                mutableInteractionSource = mutableInteractionSource
              )
            }
          } else {
            null
          },
          animatorFactory = if (mainNavigationState.currentListLocation == MainNavigationListLocation.CHATS || mainNavigationState.currentListLocation == MainNavigationListLocation.ARCHIVE) {
            noEnterTransitionFactory
          } else {
            AppScaffoldAnimationStateFactory.Default
          }
        )
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
   * Creates and wraps a scaffold navigator such that we can use it to operate with both
   * our split pane and legacy activities.
   */
  @OptIn(ExperimentalMaterial3AdaptiveApi::class)
  @Composable
  private fun rememberNavigator(
    windowSizeClass: WindowSizeClass,
    contentLayoutData: MainContentLayoutData,
    maxWidth: Dp
  ): AppScaffoldNavigator<Any> {
    val scaffoldNavigator = rememberThreePaneScaffoldNavigatorDelegate(
      isSplitPane = windowSizeClass.isSplitPane(),
      horizontalPartitionSpacerSize = contentLayoutData.partitionWidth,
      defaultPanePreferredWidth = contentLayoutData.rememberDefaultPanePreferredWidth(maxWidth)
    )

    val coroutine = rememberCoroutineScope()

    return remember(scaffoldNavigator, coroutine) {
      mainNavigationViewModel.wrapNavigator(coroutine, scaffoldNavigator) { detailLocation ->
        when (detailLocation) {
          is MainNavigationDetailLocation.Chats.Conversation -> {
            startActivity(
              ConversationIntents.createBuilderSync(this, detailLocation.conversationArgs.recipientId, detailLocation.conversationArgs.threadId)
                .withArgs(detailLocation.conversationArgs)
                .build()
            )
          }

          is MainNavigationDetailLocation.Calls.CallLinks.CallLinkDetails -> {
            startActivity(CallLinkDetailsActivity.createIntent(this, detailLocation.callLinkRoomId))
          }

          is MainNavigationDetailLocation.Calls.CallLinks.EditCallLinkName -> {
            error("Unexpected subroute EditCallLinkName.")
          }

          MainNavigationDetailLocation.Empty -> Unit
        }
      }
    }
  }

  @Composable
  private fun MainContainer(content: @Composable BoxWithConstraintsScope.() -> Unit) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    SignalTheme(isDarkMode = DynamicTheme.isDarkTheme(this)) {
      val backgroundColor = if (!windowSizeClass.isSplitPane()) {
        MaterialTheme.colorScheme.surface
      } else {
        SignalTheme.colors.colorSurface1
      }

      val modifier = when {
        windowSizeClass.isSplitPane() -> {
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

  override fun getIntent(): Intent {
    return super.getIntent().setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleDeepLinkIntent(intent)

    val extras = intent.extras ?: return
    val startingTab = extras.getSerializableCompat(KEY_STARTING_TAB, MainNavigationListLocation::class.java)

    when (startingTab) {
      MainNavigationListLocation.CHATS -> mainNavigationViewModel.onChatsSelected()
      MainNavigationListLocation.ARCHIVE -> mainNavigationViewModel.onArchiveSelected()
      MainNavigationListLocation.CALLS -> mainNavigationViewModel.onCallsSelected()
      MainNavigationListLocation.STORIES -> {
        if (Stories.isFeatureEnabled()) {
          mainNavigationViewModel.onStoriesSelected()
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
    mainNavigationViewModel.refreshNavigationBarState()
  }

  override fun onStop() {
    super.onStop()
    SplashScreenUtil.setSplashScreenThemeIfNecessary(this, SignalStore.settings.theme)
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray, deviceId: Int) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == MainNavigator.REQUEST_CONFIG_CHANGES && resultCode == RESULT_CONFIG_CHANGED) {
      recreate()
    }

    if (resultCode == RESULT_OK && requestCode == CreateSvrPinActivity.REQUEST_NEW_PIN) {
      mainNavigationViewModel.setSnackbar(SnackbarState(message = getString(R.string.ConfirmKbsPinFragment__pin_created)))
      mainNavigationViewModel.onMegaphoneCompleted(Megaphones.Event.PINS_FOR_ALL)
    }

    if (resultCode == RESULT_OK && requestCode == UsernameEditFragment.REQUEST_CODE) {
      val snackbarString = getString(R.string.ConversationListFragment_username_recovered_toast, SignalStore.account.username)
      mainNavigationViewModel.setSnackbar(
        SnackbarState(
          message = snackbarString
        )
      )
    }

    if (resultCode == RESULT_OK && requestCode == VerifyBackupKeyActivity.REQUEST_CODE) {
      mainNavigationViewModel.setSnackbar(
        SnackbarState(
          message = getString(R.string.VerifyBackupKey__backup_key_correct),
          duration = SnackbarDuration.Short
        )
      )
      mainNavigationViewModel.onMegaphoneSnoozed(Megaphones.Event.VERIFY_BACKUP_KEY)
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
      setStatusBarColor = {},
      lifecycleOwner = lifecycleOwner
    ).attach(recyclerView)
  }

  override fun bindScrollHelper(recyclerView: RecyclerView, lifecycleOwner: LifecycleOwner, chatFolders: RecyclerView, setChatFolder: (Int) -> Unit) {
    Material3OnScrollHelper(
      activity = this,
      views = listOf(chatFolders),
      viewStubs = listOf(),
      setStatusBarColor = {},
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
      mainNavigationViewModel.goTo(MainNavigationListLocation.CHATS)
      mainNavigationViewModel.goTo(MainNavigationDetailLocation.Chats.Conversation(ConversationIntents.readArgsFromBundle(intent.extras!!)))
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

  private fun updateNotificationProfileStatus(notificationProfiles: List<NotificationProfile>) {
    val activeProfile = NotificationProfiles.getActiveProfile(notificationProfiles)
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

    override fun onInviteFriendsClick() {
      openSettings.launch(AppSettingsActivity.invite(this@MainActivity))
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
      startActivity(StorySettingsActivity.getIntent(this@MainActivity))
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

    override fun onCameraClick(destination: MainNavigationListLocation) {
      val onGranted = {
        startActivity(
          MediaSelectionActivity.camera(
            context = this@MainActivity,
            isStory = destination == MainNavigationListLocation.STORIES
          )
        )
      }

      if (CameraXUtil.isSupported()) {
        onGranted()
      } else {
        Permissions.with(this@MainActivity)
          .request(Manifest.permission.CAMERA)
          .ifNecessary()
          .withRationaleDialog(getString(R.string.CameraXFragment_allow_access_camera), getString(R.string.CameraXFragment_to_capture_photos_and_video_allow_camera), R.drawable.symbol_camera_24)
          .withPermanentDenialDialog(
            getString(R.string.CameraXFragment_signal_needs_camera_access_capture_photos),
            null,
            R.string.CameraXFragment_allow_access_camera,
            R.string.CameraXFragment_to_capture_photos_videos,
            supportFragmentManager
          )
          .onAllGranted(onGranted)
          .onAnyDenied { Toast.makeText(this@MainActivity, R.string.CameraXFragment_signal_needs_camera_access_capture_photos, Toast.LENGTH_LONG).show() }
          .execute()
      }
    }

    override fun onMegaphoneVisible(megaphone: Megaphone) {
      mainNavigationViewModel.onMegaphoneVisible(megaphone)
    }

    override fun onSnackbarDismissed() {
      mainNavigationViewModel.setSnackbar(null)
    }
  }

  inner class MainMegaphoneActionController : MegaphoneActionController {
    override fun onMegaphoneNavigationRequested(intent: Intent) {
      startActivity(intent)
    }

    override fun onMegaphoneNavigationRequested(intent: Intent, requestCode: Int) {
      startActivityForResult(intent, requestCode)
    }

    override fun onMegaphoneToastRequested(string: String) {
      mainNavigationViewModel.setSnackbar(
        SnackbarState(
          message = string
        )
      )
    }

    override fun getMegaphoneActivity(): Activity {
      return this@MainActivity
    }

    override fun onMegaphoneSnooze(event: Megaphones.Event) {
      mainNavigationViewModel.onMegaphoneSnoozed(event)
    }

    override fun onMegaphoneCompleted(event: Megaphones.Event) {
      mainNavigationViewModel.onMegaphoneCompleted(event)
    }

    override fun onMegaphoneDialogFragmentRequested(dialogFragment: DialogFragment) {
      dialogFragment.show(supportFragmentManager, "megaphone_dialog")
    }
  }

  private inner class MainNavigationCallback : (MainNavigationListLocation) -> Unit {
    override fun invoke(location: MainNavigationListLocation) {
      when (location) {
        MainNavigationListLocation.CHATS -> mainNavigationViewModel.onChatsSelected()
        MainNavigationListLocation.CALLS -> mainNavigationViewModel.onCallsSelected()
        MainNavigationListLocation.STORIES -> mainNavigationViewModel.onStoriesSelected()
        MainNavigationListLocation.ARCHIVE -> mainNavigationViewModel.onArchiveSelected()
      }
    }
  }
}
