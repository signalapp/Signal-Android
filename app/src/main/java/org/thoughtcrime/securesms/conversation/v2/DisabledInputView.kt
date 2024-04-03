/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.messagerequests.MessageRequestState
import org.thoughtcrime.securesms.messagerequests.MessageRequestsBottomView
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.visible

/**
 * A one-stop-view for all your conversation input disabled needs.
 *
 * - Expired client
 * - No longer registered
 * - Message Request/Blocked
 * - Requesting group member
 * - No longer group member
 */
class DisabledInputView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  private val inflater: LayoutInflater by lazy { LayoutInflater.from(context) }

  private var expiredOrUnauthorized: View? = null
  private var messageRequestView: MessageRequestsBottomView? = null
  private var noLongerAMember: View? = null
  private var requestingGroup: View? = null
  private var announcementGroupOnly: TextView? = null
  private var inviteToSignal: View? = null
  private var releaseNoteChannel: View? = null

  private var currentView: View? = null

  var color: Int = 0
    private set
  var listener: Listener? = null

  fun showAsExpiredOrUnauthorized(clientExpired: Boolean, unauthorized: Boolean) {
    expiredOrUnauthorized = show(
      existingView = expiredOrUnauthorized,
      create = { inflater.inflate(R.layout.conversation_activity_logged_out_stub, this, false) },
      bind = {
        val message = findViewById<TextView>(R.id.logged_out_message)
        val actionButton = findViewById<MaterialButton>(R.id.logged_out_button)

        message.setText(if (clientExpired) R.string.ExpiredBuildReminder_this_version_of_signal_has_expired else R.string.UnauthorizedReminder_this_is_likely_because_you_registered_your_phone_number_with_Signal_on_a_different_device)
        actionButton.setText(if (clientExpired) R.string.ConversationFragment__update_build else R.string.ConversationFragment__reregister_signal)
        actionButton.setOnClickListener {
          if (clientExpired) {
            listener?.onUpdateAppClicked()
          } else {
            listener?.onReRegisterClicked()
          }
        }
      }
    )
  }

  fun showAsMessageRequest(recipient: Recipient, messageRequestState: MessageRequestState) {
    messageRequestView = show(
      existingView = messageRequestView,
      create = { MessageRequestsBottomView(context) },
      bind = {
        setMessageRequestData(recipient, messageRequestState)
        setWallpaperEnabled(recipient.hasWallpaper)

        setAcceptOnClickListener { listener?.onAcceptMessageRequestClicked() }
        setDeleteOnClickListener { listener?.onDeleteClicked() }
        setBlockOnClickListener { listener?.onBlockClicked() }
        setUnblockOnClickListener { listener?.onUnblockClicked() }
        setReportOnClickListener { listener?.onReportSpamClicked() }
      }
    )
  }

  fun showAsNoLongerAMember() {
    noLongerAMember = show(
      existingView = noLongerAMember,
      create = { inflater.inflate(R.layout.conversation_no_longer_a_member, this, false) }
    )
  }

  fun showAsRequestingMember() {
    requestingGroup = show(
      existingView = requestingGroup,
      create = { inflater.inflate(R.layout.conversation_requesting_bottom_banner, this, false) },
      bind = {
        findViewById<View>(R.id.conversation_cancel_request).setOnClickListener {
          listener?.onCancelGroupRequestClicked()
        }
      }
    )
  }

  fun showAsAnnouncementGroupAdminsOnly() {
    announcementGroupOnly = show(
      existingView = announcementGroupOnly,
      create = { inflater.inflate(R.layout.conversation_cannot_send_announcement_group, this, false) as TextView },
      bind = {
        movementMethod = LinkMovementMethod.getInstance()
        text = SpanUtil.clickSubstring(
          context,
          R.string.ConversationActivity_only_s_can_send_messages,
          R.string.ConversationActivity_admins
        ) {
          listener?.onShowAdminsBottomSheetDialog()
        }
      }
    )
  }

  fun showAsInviteToSignal(context: Context, recipient: Recipient, threadContainsSms: Boolean) {
    inviteToSignal = show(
      existingView = inviteToSignal,
      create = { inflater.inflate(R.layout.conversation_activity_sms_export_stub, this, false) },
      bind = {
        findViewById<TextView>(R.id.export_sms_message).text = if (recipient.isMmsGroup) {
          context.getString(R.string.ConversationActivity__sms_messaging_is_no_longer_supported)
        } else if (threadContainsSms) {
          context.getString(R.string.ConversationActivity__sms_messaging_is_no_longer_supported_in_signal_invite_s_to_to_signal_to_keep_the_conversation_here, recipient.getDisplayName(context))
        } else {
          context.getString(R.string.ConversationActivity__this_person_is_no_longer_using_signal)
        }

        findViewById<MaterialButton>(R.id.export_sms_button).apply {
          setText(R.string.ConversationActivity__invite_to_signal)
          setOnClickListener { listener?.onInviteToSignal(recipient) }
          visible = !recipient.isMmsGroup
        }
      }
    )
  }

  fun showAsReleaseNotesChannel(recipient: Recipient) {
    releaseNoteChannel = show(
      existingView = releaseNoteChannel,
      create = { inflater.inflate(R.layout.conversation_activity_unmute, this, false) },
      bind = {
        if (recipient.isMuted) {
          visible = true
          findViewById<View>(R.id.conversation_activity_unmute_button).setOnClickListener { listener?.onUnmuteReleaseNotesChannel() }
        } else {
          visible = false
        }
      }
    )
  }

  fun setWallpaperEnabled(wallpaperEnabled: Boolean) {
    color = ContextCompat.getColor(context, if (wallpaperEnabled) R.color.wallpaper_bubble_color else R.color.signal_colorBackground)
    setBackgroundColor(color)
  }

  fun showBusy() {
    if (currentView == messageRequestView) {
      messageRequestView?.showBusy()
    }
  }

  fun hideBusy() {
    if (currentView == messageRequestView) {
      messageRequestView?.hideBusy()
    }
  }

  fun clear() {
    removeAllViews()
    currentView = null
    expiredOrUnauthorized = null
    messageRequestView?.hideBusy()
    messageRequestView = null
    noLongerAMember = null
    requestingGroup = null
    announcementGroupOnly = null
  }

  private fun <V : View> show(existingView: V?, create: () -> V, bind: V.() -> Unit = {}): V {
    if (existingView != currentView) {
      removeIfNotNull(currentView)
    }

    val view: V = if (existingView != null) {
      existingView
    } else {
      val newView: V = create()
      addView(newView, defaultLayoutParams())
      newView
    }

    view.bind()

    currentView = view

    return view
  }

  private fun defaultLayoutParams(): LayoutParams {
    return LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
  }

  private fun removeIfNotNull(view: View?) {
    if (view != null) {
      removeView(view)
    }
  }

  interface Listener {
    fun onUpdateAppClicked()
    fun onReRegisterClicked()
    fun onCancelGroupRequestClicked()
    fun onShowAdminsBottomSheetDialog()
    fun onAcceptMessageRequestClicked()
    fun onDeleteClicked()
    fun onBlockClicked()
    fun onUnblockClicked()
    fun onInviteToSignal(recipient: Recipient)
    fun onUnmuteReleaseNotesChannel()
    fun onReportSpamClicked()
  }
}
