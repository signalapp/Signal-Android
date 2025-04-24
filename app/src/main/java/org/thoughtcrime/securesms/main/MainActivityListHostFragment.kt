package org.thoughtcrime.securesms.main

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.log.CallLogFragment
import org.thoughtcrime.securesms.conversationlist.ConversationListFragment
import org.thoughtcrime.securesms.conversationlist.model.UnreadPaymentsLiveData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.Material3OnScrollHelper
import org.thoughtcrime.securesms.util.TopToastPopup
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState

class MainActivityListHostFragment : Fragment(R.layout.main_activity_list_host_fragment), ConversationListFragment.Callback, Material3OnScrollHelperBinder, CallLogFragment.Callback {

  companion object {
    private val TAG = Log.tag(MainActivityListHostFragment::class.java)
  }

  private val disposables: LifecycleDisposable = LifecycleDisposable()

  private var previousTopToastPopup: TopToastPopup? = null

  private val destinationChangedListener = DestinationChangedListener()
  private val toolbarViewModel: MainToolbarViewModel by activityViewModels()
  private val mainNavigationViewModel: MainNavigationViewModel by activityViewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    disposables.bindTo(viewLifecycleOwner)

    UnreadPaymentsLiveData().observe(viewLifecycleOwner) { unread ->
      toolbarViewModel.setHasUnreadPayments(unread.isPresent)
    }

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.RESUMED) {
        launch {
          mainNavigationViewModel.mainNavigationState.collectLatest { state ->
            withContext(Dispatchers.Main) {
              val controller: NavController = getChildNavController()
              when (controller.currentDestination?.id) {
                R.id.conversationListFragment -> goToStateFromConversationList(state, controller)
                R.id.conversationListArchiveFragment -> Unit
                R.id.storiesLandingFragment -> goToStateFromStories(state, controller)
                R.id.callLogFragment -> goToStateFromCalling(state, controller)
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
      }
    }
  }

  private fun getChildNavController(): NavController {
    return requireView().findViewById<View>(R.id.fragment_container).findNavController()
  }

  private fun goToStateFromConversationList(state: MainNavigationState, navController: NavController) {
    if (state.selectedDestination == MainNavigationListLocation.CHATS) {
      return
    } else {
      val destination = if (state.selectedDestination == MainNavigationListLocation.STORIES) {
        R.id.action_conversationListFragment_to_storiesLandingFragment
      } else {
        R.id.action_conversationListFragment_to_callLogFragment
      }

      navController.navigate(
        destination,
        null,
        null
      )
    }
  }

  private fun goToStateFromCalling(state: MainNavigationState, navController: NavController) {
    when (state.selectedDestination) {
      MainNavigationListLocation.CALLS -> return
      MainNavigationListLocation.CHATS -> navController.popBackStack(R.id.conversationListFragment, false)
      MainNavigationListLocation.STORIES -> navController.navigate(R.id.action_callLogFragment_to_storiesLandingFragment)
    }
  }

  private fun goToStateFromStories(state: MainNavigationState, navController: NavController) {
    when (state.selectedDestination) {
      MainNavigationListLocation.STORIES -> return
      MainNavigationListLocation.CHATS -> navController.popBackStack(R.id.conversationListFragment, false)
      MainNavigationListLocation.CALLS -> navController.navigate(R.id.action_storiesLandingFragment_to_callLogFragment)
    }
  }

  override fun onResume() {
    super.onResume()
    toolbarViewModel.refresh()

    requireView()
      .findViewById<View>(R.id.fragment_container)
      .findNavController()
      .addOnDestinationChangedListener(destinationChangedListener)

    if (toolbarViewModel.state.value.mode == MainToolbarMode.ACTION_MODE) {
      presentToolbarForMultiselect()
    }
  }

  override fun onPause() {
    super.onPause()
    requireView()
      .findViewById<View>(R.id.fragment_container)
      .findNavController()
      .removeOnDestinationChangedListener(destinationChangedListener)
  }

  private fun presentToolbarForConversationListFragment() {
    toolbarViewModel.setToolbarMode(MainToolbarMode.FULL, destination = MainNavigationListLocation.CHATS, overwriteSearchMode = false)
  }

  private fun presentToolbarForConversationListArchiveFragment() {
    toolbarViewModel.setToolbarMode(MainToolbarMode.BASIC, destination = MainNavigationListLocation.CHATS)
  }

  private fun presentToolbarForStoriesLandingFragment() {
    toolbarViewModel.setToolbarMode(MainToolbarMode.FULL, destination = MainNavigationListLocation.STORIES)
  }

  private fun presentToolbarForCallLogFragment() {
    toolbarViewModel.setToolbarMode(MainToolbarMode.FULL, destination = MainNavigationListLocation.CALLS)
  }

  private fun presentToolbarForMultiselect() {
    toolbarViewModel.setToolbarMode(MainToolbarMode.ACTION_MODE)
  }

  override fun onDestroyView() {
    previousTopToastPopup = null
    super.onDestroyView()
  }

  override fun onMultiSelectStarted() {
    presentToolbarForMultiselect()
  }

  override fun onMultiSelectFinished() {
    val currentDestination: NavDestination? = requireView().findViewById<View>(R.id.fragment_container).findNavController().currentDestination
    if (currentDestination != null) {
      presentToolbarForDestination(currentDestination)
    }
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

  private fun updateNotificationProfileStatus(notificationProfiles: List<NotificationProfile>) {
    val activeProfile = NotificationProfiles.getActiveProfile(notificationProfiles)
    if (activeProfile != null) {
      if (activeProfile.id != SignalStore.notificationProfile.lastProfilePopup) {
        view?.postDelayed({
          try {
            var fragmentView = view as? ViewGroup ?: return@postDelayed

            SignalStore.notificationProfile.lastProfilePopup = activeProfile.id
            SignalStore.notificationProfile.lastProfilePopupTime = System.currentTimeMillis()

            if (previousTopToastPopup?.isShowing == true) {
              previousTopToastPopup?.dismiss()
            }

            val fragment = parentFragmentManager.findFragmentByTag(BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
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

  private fun presentToolbarForDestination(destination: NavDestination) {
    when (destination.id) {
      R.id.conversationListFragment -> {
        presentToolbarForConversationListFragment()
      }

      R.id.conversationListArchiveFragment -> {
        presentToolbarForConversationListArchiveFragment()
      }

      R.id.storiesLandingFragment -> {
        presentToolbarForStoriesLandingFragment()
      }

      R.id.callLogFragment -> {
        presentToolbarForCallLogFragment()
      }
    }
  }

  private inner class DestinationChangedListener : NavController.OnDestinationChangedListener {
    override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
      presentToolbarForDestination(destination)
    }
  }

  override fun bindScrollHelper(recyclerView: RecyclerView, lifecycleOwner: LifecycleOwner) {
    Material3OnScrollHelper(
      activity = requireActivity(),
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
      activity = requireActivity(),
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
}
