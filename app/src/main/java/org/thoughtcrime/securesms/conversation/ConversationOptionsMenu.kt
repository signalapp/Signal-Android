package org.thoughtcrime.securesms.conversation

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.annotation.IdRes
import androidx.core.view.MenuProvider
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.ConversationGroupViewModel.GroupActiveState
import org.thoughtcrime.securesms.conversation.ui.groupcall.GroupCallViewModel
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.LiveRecipient
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.LifecycleDisposable

/**
 * Delegate object for managing the conversation options menu
 */
internal object ConversationOptionsMenu {

  /**
   * MenuProvider implementation for the conversation options menu.
   */
  class Provider(
    private val dependencies: Dependencies,
    private val optionsMenuProviderCallback: Callback,
    private val lifecycleDisposable: LifecycleDisposable
  ) : MenuProvider, Dependencies by dependencies {

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
      menu.clear()

      val recipient: Recipient? = liveRecipient?.get()
      val groupActiveState: GroupActiveState? = groupViewModel.groupActiveState.value
      val isActiveGroup = groupActiveState != null && groupActiveState.isActiveGroup
      val isActiveV2Group = groupActiveState != null && groupActiveState.isActiveV2Group
      val isInActiveGroup = groupActiveState != null && !groupActiveState.isActiveGroup

      if (isInMessageRequest() && (recipient != null) && !recipient.isBlocked) {
        if (isActiveGroup) {
          menuInflater.inflate(R.menu.conversation_message_requests_group, menu)
        }
      }

      if (viewModel.isPushAvailable) {
        if (recipient!!.expiresInSeconds > 0) {
          if (!isInActiveGroup) {
            menuInflater.inflate(R.menu.conversation_expiring_on, menu)
          }
          titleView.showExpiring(liveRecipient!!)
        } else {
          if (!isInActiveGroup) {
            menuInflater.inflate(R.menu.conversation_expiring_off, menu)
          }
          titleView.clearExpiring()
        }
      }

      if (isSingleConversation()) {
        if (viewModel.isPushAvailable) {
          menuInflater.inflate(R.menu.conversation_callable_secure, menu)
        } else if (!recipient!!.isReleaseNotes && SignalStore.misc().smsExportPhase.allowSmsFeatures()) {
          menuInflater.inflate(R.menu.conversation_callable_insecure, menu)
        }
      } else if (isGroupConversation()) {
        if (isActiveV2Group) {
          menuInflater.inflate(R.menu.conversation_callable_groupv2, menu)
          if (groupCallViewModel != null && java.lang.Boolean.TRUE == groupCallViewModel.hasActiveGroupCall().getValue()) {
            hideMenuItem(menu, R.id.menu_video_secure)
          }
          showGroupCallingTooltip()
        }
        menuInflater.inflate(R.menu.conversation_group_options, menu)
        if (!isPushGroupConversation()) {
          menuInflater.inflate(R.menu.conversation_mms_group_options, menu)
          if (distributionType == ThreadTable.DistributionTypes.BROADCAST) {
            menu.findItem(R.id.menu_distribution_broadcast).isChecked = true
          } else {
            menu.findItem(R.id.menu_distribution_conversation).isChecked = true
          }
        }
        menuInflater.inflate(R.menu.conversation_active_group_options, menu)
      }

      menuInflater.inflate(R.menu.conversation, menu)

      if (isInMessageRequest() && !recipient!!.isBlocked) {
        hideMenuItem(menu, R.id.menu_conversation_settings)
      }

      if (isSingleConversation() && !viewModel.isPushAvailable && !recipient!!.isReleaseNotes) {
        menuInflater.inflate(R.menu.conversation_insecure, menu)
      }

      if (recipient != null && recipient.isMuted) menuInflater.inflate(R.menu.conversation_muted, menu) else menuInflater.inflate(R.menu.conversation_unmuted, menu)

      if (isSingleConversation() && getRecipient()!!.contactUri == null && !recipient!!.isReleaseNotes && !recipient.isSelf && recipient.hasE164()) {
        menuInflater.inflate(R.menu.conversation_add_to_contacts, menu)
      }

      if (recipient != null && recipient.isSelf) {
        if (viewModel.isPushAvailable) {
          hideMenuItem(menu, R.id.menu_call_secure)
          hideMenuItem(menu, R.id.menu_video_secure)
        } else {
          hideMenuItem(menu, R.id.menu_call_insecure)
        }
        hideMenuItem(menu, R.id.menu_mute_notifications)
      }

      if (recipient != null && recipient.isBlocked) {
        if (viewModel.isPushAvailable) {
          hideMenuItem(menu, R.id.menu_call_secure)
          hideMenuItem(menu, R.id.menu_video_secure)
          hideMenuItem(menu, R.id.menu_expiring_messages)
          hideMenuItem(menu, R.id.menu_expiring_messages_off)
        } else {
          hideMenuItem(menu, R.id.menu_call_insecure)
        }
        hideMenuItem(menu, R.id.menu_mute_notifications)
      }

      if (recipient != null && recipient.isReleaseNotes) {
        hideMenuItem(menu, R.id.menu_add_shortcut)
      }

      hideMenuItem(menu, R.id.menu_group_recipients)

      if (isActiveV2Group) {
        hideMenuItem(menu, R.id.menu_mute_notifications)
        hideMenuItem(menu, R.id.menu_conversation_settings)
      } else if (isGroupConversation()) {
        hideMenuItem(menu, R.id.menu_conversation_settings)
      }

      hideMenuItem(menu, R.id.menu_create_bubble)
      lifecycleDisposable += viewModel.canShowAsBubble().subscribeBy(onNext = { canShowAsBubble: Boolean ->
        val item = menu.findItem(R.id.menu_create_bubble)
        if (item != null) {
          item.isVisible = canShowAsBubble && !isInBubble()
        }
      })

      if (threadId == -1L) {
        hideMenuItem(menu, R.id.menu_view_media)
      }

      optionsMenuProviderCallback.onOptionsMenuCreated(menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
      when (menuItem.itemId) {
        R.id.menu_call_secure -> optionsMenuProviderCallback.handleDial(getRecipient(), true)
        R.id.menu_video_secure -> optionsMenuProviderCallback.handleVideo(getRecipient())
        R.id.menu_call_insecure -> optionsMenuProviderCallback.handleDial(getRecipient(), false)
        R.id.menu_view_media -> optionsMenuProviderCallback.handleViewMedia()
        R.id.menu_add_shortcut -> optionsMenuProviderCallback.handleAddShortcut()
        R.id.menu_search -> optionsMenuProviderCallback.handleSearch()
        R.id.menu_add_to_contacts -> optionsMenuProviderCallback.handleAddToContacts()
        R.id.menu_group_recipients -> optionsMenuProviderCallback.handleDisplayGroupRecipients()
        R.id.menu_distribution_broadcast -> optionsMenuProviderCallback.handleDistributionBroadcastEnabled(menuItem)
        R.id.menu_distribution_conversation -> optionsMenuProviderCallback.handleDistributionConversationEnabled(menuItem)
        R.id.menu_group_settings -> optionsMenuProviderCallback.handleManageGroup()
        R.id.menu_leave -> optionsMenuProviderCallback.handleLeavePushGroup()
        R.id.menu_invite -> optionsMenuProviderCallback.handleInviteLink()
        R.id.menu_mute_notifications -> optionsMenuProviderCallback.handleMuteNotifications()
        R.id.menu_unmute_notifications -> optionsMenuProviderCallback.handleUnmuteNotifications()
        R.id.menu_conversation_settings -> optionsMenuProviderCallback.handleConversationSettings()
        R.id.menu_expiring_messages_off, R.id.menu_expiring_messages -> optionsMenuProviderCallback.handleSelectMessageExpiration()
        R.id.menu_create_bubble -> optionsMenuProviderCallback.handleCreateBubble()
        R.id.home -> optionsMenuProviderCallback.handleGoHome()
        else -> return false
      }

      return true
    }

    private fun getRecipient(): Recipient? {
      return liveRecipient?.get()
    }

    private fun hideMenuItem(menu: Menu, @IdRes menuItem: Int) {
      if (menu.findItem(menuItem) != null) {
        menu.findItem(menuItem).isVisible = false
      }
    }

    private fun isSingleConversation(): Boolean = getRecipient()?.isGroup == false

    private fun isGroupConversation(): Boolean = getRecipient()?.isGroup == true

    private fun isPushGroupConversation(): Boolean = getRecipient()?.isPushGroup == true
  }

  /**
   * Dependencies abstraction for the conversation options menu
   */
  interface Dependencies {
    val liveRecipient: LiveRecipient?
    val viewModel: ConversationViewModel
    val groupViewModel: ConversationGroupViewModel
    val groupCallViewModel: GroupCallViewModel?
    val titleView: ConversationTitleView
    val distributionType: Int
    val threadId: Long
    fun isInMessageRequest(): Boolean
    fun showGroupCallingTooltip()
    fun isInBubble(): Boolean
  }

  /**
   * Callbacks abstraction for the converstaion options menu
   */
  interface Callback {
    fun onOptionsMenuCreated(menu: Menu)

    fun handleVideo(recipient: Recipient?)
    fun handleDial(recipient: Recipient?, isSecure: Boolean)
    fun handleViewMedia()
    fun handleAddShortcut()
    fun handleSearch()
    fun handleAddToContacts()
    fun handleDisplayGroupRecipients()
    fun handleDistributionBroadcastEnabled(menuItem: MenuItem)
    fun handleDistributionConversationEnabled(menuItem: MenuItem)
    fun handleManageGroup()
    fun handleLeavePushGroup()
    fun handleInviteLink()
    fun handleMuteNotifications()
    fun handleUnmuteNotifications()
    fun handleConversationSettings()
    fun handleSelectMessageExpiration()
    fun handleCreateBubble()
    fun handleGoHome()
  }
}
