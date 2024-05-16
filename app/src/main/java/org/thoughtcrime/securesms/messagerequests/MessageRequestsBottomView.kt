package org.thoughtcrime.securesms.messagerequests

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.HtmlCompat
import com.google.android.material.button.MaterialButton
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.messagerequests.MessageRequestBarColorTheme.Companion.resolveTheme
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.Debouncer
import org.thoughtcrime.securesms.util.HtmlUtil
import org.thoughtcrime.securesms.util.views.LearnMoreTextView
import org.thoughtcrime.securesms.util.visible

/**
 * View shown in a conversation during a message request state or related state (e.g., blocked).
 */
class MessageRequestsBottomView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {
  private val showProgressDebouncer = Debouncer(250)

  private val question: LearnMoreTextView
  private val accept: MaterialButton
  private val block: MaterialButton
  private val unblock: MaterialButton
  private val delete: MaterialButton
  private val report: MaterialButton
  private val busyIndicator: View
  private val buttonBar: View

  init {
    inflate(context, R.layout.message_request_bottom_bar, this)

    question = findViewById(R.id.message_request_question)
    accept = findViewById(R.id.message_request_accept)
    block = findViewById(R.id.message_request_block)
    unblock = findViewById(R.id.message_request_unblock)
    delete = findViewById(R.id.message_request_delete)
    report = findViewById(R.id.message_request_report)
    busyIndicator = findViewById(R.id.message_request_busy_indicator)
    buttonBar = findViewById(R.id.message_request_button_layout)

    setWallpaperEnabled(false)
  }

  fun setMessageRequestData(recipient: Recipient, messageRequestState: MessageRequestState) {
    question.setLearnMoreVisible(false)
    question.setOnLinkClickListener(null)

    updateButtonVisibility(messageRequestState)

    when (messageRequestState.state) {
      MessageRequestState.State.INDIVIDUAL_BLOCKED -> {
        val message = if (recipient.isReleaseNotes) {
          R.string.MessageRequestBottomView_get_updates_and_news_from_s_you_wont_receive_any_updates_until_you_unblock_them
        } else if (recipient.isRegistered) {
          R.string.MessageRequestBottomView_do_you_want_to_let_s_message_you_wont_receive_any_messages_until_you_unblock_them
        } else {
          R.string.MessageRequestBottomView_do_you_want_to_let_s_message_you_wont_receive_any_messages_until_you_unblock_them_SMS
        }
        question.text = HtmlCompat.fromHtml(
          context.getString(
            message,
            HtmlUtil.bold(recipient.getShortDisplayName(context))
          ),
          0
        )
      }

      MessageRequestState.State.BLOCKED_GROUP -> question.setText(R.string.MessageRequestBottomView_unblock_this_group_and_share_your_name_and_photo_with_its_members)

      MessageRequestState.State.LEGACY_INDIVIDUAL -> {
        question.text = context.getString(R.string.MessageRequestBottomView_continue_your_conversation_with_s_and_share_your_name_and_photo, recipient.getShortDisplayName(context))
        question.setLearnMoreVisible(true)
        question.setOnLinkClickListener { CommunicationActions.openBrowserLink(context, context.getString(R.string.MessageRequestBottomView_legacy_learn_more_url)) }
        accept.setText(R.string.MessageRequestBottomView_continue)
      }

      MessageRequestState.State.DEPRECATED_GROUP_V1 -> question.setText(R.string.MessageRequestBottomView_upgrade_this_group_to_activate_new_features)
      MessageRequestState.State.GROUP_V2_INVITE -> {
        question.setText(R.string.MessageRequestBottomView_do_you_want_to_join_this_group_you_wont_see_their_messages)
        accept.setText(R.string.MessageRequestBottomView_accept)
      }

      MessageRequestState.State.GROUP_V2_ADD -> {
        question.setText(R.string.MessageRequestBottomView_join_this_group_they_wont_know_youve_seen_their_messages_until_you_accept)
        accept.setText(R.string.MessageRequestBottomView_accept)
      }

      MessageRequestState.State.INDIVIDUAL -> {
        question.text = HtmlCompat.fromHtml(
          context.getString(
            R.string.MessageRequestBottomView_do_you_want_to_let_s_message_you_they_wont_know_youve_seen_their_messages_until_you_accept,
            HtmlUtil.bold(recipient.getShortDisplayName(context))
          ),
          0
        )
        accept.setText(R.string.MessageRequestBottomView_accept)
      }

      MessageRequestState.State.INDIVIDUAL_HIDDEN -> {
        question.text = HtmlCompat.fromHtml(
          context.getString(
            R.string.MessageRequestBottomView_do_you_want_to_let_s_message_you_you_removed_them_before,
            HtmlUtil.bold(recipient.getShortDisplayName(context))
          ),
          0
        )
        accept.setText(R.string.MessageRequestBottomView_accept)
      }

      MessageRequestState.State.NONE -> Unit
      MessageRequestState.State.NONE_HIDDEN -> Unit
    }
  }

  private fun updateButtonVisibility(messageState: MessageRequestState) {
    accept.visible = !messageState.isBlocked
    block.visible = !messageState.isBlocked
    unblock.visible = messageState.isBlocked
    delete.visible = messageState.reportedAsSpam || messageState.isBlocked
    report.visible = !messageState.reportedAsSpam
  }

  fun showBusy() {
    showProgressDebouncer.publish { busyIndicator.visibility = VISIBLE }
    buttonBar.visibility = INVISIBLE
  }

  fun hideBusy() {
    showProgressDebouncer.clear()
    busyIndicator.visibility = GONE
    buttonBar.visibility = VISIBLE
  }

  fun setWallpaperEnabled(isEnabled: Boolean) {
    val theme = resolveTheme(isEnabled)
    listOf(delete, block, accept, unblock, report).forEach { it.backgroundTintList = ColorStateList.valueOf(theme.getButtonBackgroundColor(context)) }
    listOf(delete, block, report).forEach { it.setTextColor(theme.getButtonForegroundDenyColor(context)) }
    listOf(accept, unblock).forEach { it.setTextColor(theme.getButtonForegroundAcceptColor(context)) }

    setBackgroundColor(theme.getContainerButtonBackgroundColor(context))
  }

  fun setAcceptOnClickListener(acceptOnClickListener: OnClickListener?) {
    accept.setOnClickListener(acceptOnClickListener)
  }

  fun setDeleteOnClickListener(deleteOnClickListener: OnClickListener?) {
    delete.setOnClickListener(deleteOnClickListener)
  }

  fun setBlockOnClickListener(blockOnClickListener: OnClickListener?) {
    block.setOnClickListener(blockOnClickListener)
  }

  fun setUnblockOnClickListener(unblockOnClickListener: OnClickListener?) {
    unblock.setOnClickListener(unblockOnClickListener)
  }

  fun setReportOnClickListener(reportOnClickListener: OnClickListener?) {
    report.setOnClickListener(reportOnClickListener)
  }
}
