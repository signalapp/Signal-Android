/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.transition.ChangeBounds
import android.transition.Slide
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.transition.addListener
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.identity.UnverifiedBannerView
import org.thoughtcrime.securesms.components.reminder.Reminder
import org.thoughtcrime.securesms.components.reminder.ReminderView
import org.thoughtcrime.securesms.database.identity.IdentityRecordList
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.util.IdentityUtil

/**
 * Responsible for showing the various "banner" views at the top of a conversation
 *
 * - Expired Build
 * - Unregistered
 * - Group join requests
 * - GroupV1 suggestions
 * - Disable Chat Bubbles setting
 * - Service outage
 */
class ConversationBannerView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayoutCompat(context, attrs, defStyleAttr) {

  private val inflater: LayoutInflater by lazy { LayoutInflater.from(context) }

  private var reminderView: ReminderView? = null
  private var unverifiedBannerView: UnverifiedBannerView? = null

  var listener: Listener? = null

  init {
    orientation = VERTICAL
  }

  fun showReminder(reminder: Reminder) {
    reminderView = show(
      position = -1,
      existingView = reminderView,
      create = { ReminderView(context) },
      bind = {
        showReminder(reminder)
        setOnActionClickListener {
          when (it) {
            R.id.reminder_action_update_now -> listener?.updateAppAction()
            R.id.reminder_action_re_register -> listener?.reRegisterAction()
            R.id.reminder_action_review_join_requests -> listener?.reviewJoinRequestsAction()
            R.id.reminder_action_gv1_suggestion_no_thanks -> listener?.gv1SuggestionsAction(it)
            R.id.reminder_action_bubble_not_now, R.id.reminder_action_bubble_turn_off -> {
              listener?.changeBubbleSettingAction(disableSetting = it == R.id.reminder_action_bubble_turn_off)
            }
          }
        }
        setOnHideListener {
          clearReminder()
          true
        }
      }
    )
  }

  fun clearReminder() {
    removeIfNotNull(reminderView)
    reminderView = null
  }

  fun showUnverifiedBanner(identityRecords: IdentityRecordList) {
    unverifiedBannerView = show(
      position = 0,
      existingView = null,
      create = { UnverifiedBannerView(context) },
      bind = {
        setOnHideListener {
          clearUnverifiedBanner()
          true
        }
        display(
          IdentityUtil.getUnverifiedBannerDescription(context, identityRecords.unverifiedRecipients)!!,
          identityRecords.unverifiedRecords,
          { listener?.onUnverifiedBannerClicked(identityRecords.unverifiedRecords) },
          { listener?.onUnverifiedBannerDismissed(identityRecords.unverifiedRecords) }
        )
      }
    )
  }

  fun clearUnverifiedBanner() {
    removeIfNotNull(unverifiedBannerView)
    unverifiedBannerView = null
  }

  private fun <V : View> show(position: Int, existingView: V?, create: () -> V, bind: V.() -> Unit = {}): V {
    val view: V = if (existingView != null) {
      existingView
    } else {
      val newView: V = create()

      TransitionManager.beginDelayedTransition(this, Slide(Gravity.TOP))
      if (position in 0..childCount) {
        addView(newView, position, defaultLayoutParams())
      } else {
        addView(newView, defaultLayoutParams())
      }
      newView
    }

    view.bind()

    return view
  }

  private fun defaultLayoutParams(): LayoutParams {
    return LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
  }

  private fun removeIfNotNull(view: View?) {
    if (view != null) {
      val slideTransition = Slide(Gravity.TOP).apply {
        addListener(
          onEnd = {
            layoutParams = layoutParams.apply { height = LayoutParams.WRAP_CONTENT }
          }
        )
      }

      val changeTransition = ChangeBounds().apply {
        if (reminderView != null) {
          addTarget(reminderView)
        }

        if (unverifiedBannerView != null) {
          addTarget(unverifiedBannerView)
        }
      }

      val transition = TransitionSet().apply {
        addTransition(slideTransition)
        addTransition(changeTransition)
      }

      layoutParams = layoutParams.apply { height = this@ConversationBannerView.height }
      TransitionManager.beginDelayedTransition(this, transition)
      removeView(view)
    }
  }

  interface Listener {
    fun updateAppAction()
    fun reRegisterAction()
    fun reviewJoinRequestsAction()
    fun gv1SuggestionsAction(actionId: Int)
    fun changeBubbleSettingAction(disableSetting: Boolean)
    fun onUnverifiedBannerClicked(unverifiedIdentities: List<IdentityRecord>)
    fun onUnverifiedBannerDismissed(unverifiedIdentities: List<IdentityRecord>)
  }
}
