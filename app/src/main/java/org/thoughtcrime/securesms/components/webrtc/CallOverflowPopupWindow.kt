/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc

import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.PopupWindowCompat
import androidx.fragment.app.FragmentActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.visible

/**
 * A popup window for calls that holds extra actions, such as reactions, raise hand, and screen sharing.
 *
 */
class CallOverflowPopupWindow(private val activity: FragmentActivity, parentViewGroup: ViewGroup, private val raisedHandDelegate: RaisedHandDelegate) : PopupWindow(
  LayoutInflater.from(activity).inflate(R.layout.call_overflow_holder, parentViewGroup, false),
  activity.resources.getDimension(R.dimen.calling_reaction_popup_menu_width).toInt(),
  activity.resources.getDimension(R.dimen.calling_reaction_popup_menu_height).toInt()
) {
  private val raiseHandLabel: TextView = (contentView as LinearLayout).findViewById(R.id.raise_hand_label)

  init {
    val root = (contentView as LinearLayout)
    val reactionScrubber = root.findViewById<CallReactionScrubber>(R.id.reaction_scrubber)
    reactionScrubber.initialize(activity.supportFragmentManager) {
      AppDependencies.signalCallManager.react(it)
      dismiss()
    }
    val raiseHand = root.findViewById<ConstraintLayout>(R.id.raise_hand_layout_parent)
    raiseHand.visible = true
    raiseHand.setOnClickListener {
      AppDependencies.signalCallManager.raiseHand(!raisedHandDelegate.isSelfHandRaised())
      dismiss()
    }
  }

  fun show(anchor: View) {
    isFocusable = true

    val resources = activity.resources

    val margin = resources.getDimension(R.dimen.calling_reaction_scrubber_margin).toInt()

    val windowRect = Rect()
    contentView.getWindowVisibleDisplayFrame(windowRect)
    val windowWidth = windowRect.width()
    val popupWidth = resources.getDimension(R.dimen.reaction_scrubber_width).toInt()

    val popupHeight = resources.getDimension(R.dimen.calling_reaction_popup_menu_height).toInt()

    val xOffset = windowWidth - popupWidth - margin
    val yOffset = -popupHeight - margin

    raiseHandLabel.setText(if (raisedHandDelegate.isSelfHandRaised()) R.string.CallOverflowPopupWindow__lower_hand else R.string.CallOverflowPopupWindow__raise_hand)

    PopupWindowCompat.showAsDropDown(this, anchor, xOffset, yOffset, Gravity.NO_GRAVITY)
  }

  interface RaisedHandDelegate {
    fun isSelfHandRaised(): Boolean
  }
}
