/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import org.signal.emoji.EmojiProvider
import org.signal.emoji.EmojiUtil
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.ConversationContextMenu
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.ReactionAction
import org.thoughtcrime.securesms.conversation.ReactionMenu
import org.thoughtcrime.securesms.conversation.ReactionOverlayPlacement
import org.thoughtcrime.securesms.conversation.ReactionScrubber
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import kotlin.math.roundToInt

/**
 * Holds what the long press overlay is showing and carries the activity's raw touch stream into it.
 *
 * [hapticView] and [menuAnchor] are providers because this outlives the fragment's view, which is
 * rebuilt whenever a conversation is re-entered; a captured anchor would by then be detached and
 * carry no window token for the popup to show against.
 */
class ChatReactionOverlayController(
  private val context: Context,
  private val hapticView: () -> View,
  private val menuAnchor: () -> View,
  private val onReactionSelected: (MessageRecord, String) -> Unit,
  private val onCustomReactionSelected: (MessageRecord, Boolean) -> Unit,
  private val onActionSelected: (ReactionAction) -> Unit,
  private val onStartHide: (View?) -> Unit,
  private val onHidden: () -> Unit
) {

  var selection: ReactionOverlaySelection? by mutableStateOf(null)
    private set

  var isRevealed: Boolean by mutableStateOf(false)
    private set

  var selectedIndex: Int by mutableIntStateOf(ReactionScrubber.NO_SELECTION)
    private set

  /** Read during composition to size the placement, so it has to be observable. */
  var menu: ConversationContextMenu? by mutableStateOf(null)
    private set

  /**
   * Where the overlay's composition starts, in window coordinates. Everything the placement works
   * in is relative to this, so a row has to be projected into it before it can be snapshotted.
   */
  var originInWindow: Offset by mutableStateOf(Offset.Zero)
    private set

  val scrubber = ReactionScrubber(REACTION_EMOJI_COUNT)

  val isShowing: Boolean
    get() = selection != null

  private val lastSeenDownPoint = PointF()

  private var messageRecord: MessageRecord? = null
  private var focusedView: View? = null
  private var strip: Strip = Strip.EMPTY
  private var menuPlaced = false
  private var pendingAction: ReactionAction? = null

  private data class Strip(
    val emojiStrings: List<String>,
    val drawables: List<Drawable?>,
    val appliedIndex: Int,
    val customSlotHasEmoji: Boolean
  ) {
    companion object {
      val EMPTY = Strip(emptyList(), emptyList(), ReactionScrubber.NO_SELECTION, false)
    }
  }

  fun messageRecord(): MessageRecord = requireNotNull(messageRecord) { "Nothing is showing." }

  /** The pointer is already down by now, so the scrubber is opened rather than waiting for one. */
  fun show(
    conversationRecipient: Recipient,
    conversationMessage: ConversationMessage,
    snapshot: ReactionOverlaySnapshot,
    isNonAdminInAnnouncementGroup: Boolean,
    canEditGroupInfo: Boolean,
    focusedView: View?
  ) {
    if (isShowing) {
      return
    }

    val record = conversationMessage.messageRecord
    val builtMenu = ReactionMenu.of(
      context = context,
      conversationRecipient = conversationRecipient,
      conversationMessage = conversationMessage,
      isNonAdminInAnnouncementGroup = isNonAdminInAnnouncementGroup,
      canEditGroupInfo = canEditGroupInfo,
      onAction = ::onMenuAction
    )

    this.messageRecord = record
    this.focusedView = focusedView
    this.strip = buildStrip(record)
    this.menuPlaced = false
    this.pendingAction = null
    this.menu = ConversationContextMenu(menuAnchor(), builtMenu.items)

    selectedIndex = ReactionScrubber.NO_SELECTION

    // Holds window coordinates from a view a re-entered conversation has already replaced.
    scrubber.geometry = ReactionScrubber.Geometry()
    scrubber.open()

    selection = ReactionOverlaySelection(
      snapshot = snapshot.bitmap,
      bubbleX = snapshot.bubbleX,
      bubbleY = snapshot.bubbleY,
      bubbleWidth = snapshot.bubbleWidth,
      contextMenuX = snapshot.contextMenuX,
      isMessageOnLeft = snapshot.isMessageOnLeft,
      lastSeenDownY = lastSeenDownPoint.y,
      emoji = strip.drawables,
      appliedEmojiIndex = strip.appliedIndex,
      canReact = builtMenu.canReact,
      returnPosition = snapshot.returnPosition
    )

    isRevealed = true
  }

  fun hide() {
    if (!isShowing || !isRevealed) {
      return
    }

    isRevealed = false
    scrubber.close()
    menu?.dismiss()

    onStartHide(if (pendingAction == ReactionAction.VIEW_INFO) null else focusedView)
  }

  /**
   * Also called if the overlay's composition is disposed part way through a hide, so the teardown
   * runs whether or not the animation got to finish. Idempotent.
   */
  fun onHideFinished() {
    if (selection == null) {
      return
    }

    isRevealed = false
    menu?.dismiss()

    val action = pendingAction

    selection = null
    messageRecord = null
    focusedView = null
    strip = Strip.EMPTY
    menu = null
    pendingAction = null
    selectedIndex = ReactionScrubber.NO_SELECTION

    onHidden()

    if (action != null) {
      onActionSelected(action)
    }
  }

  /** Corrects the offsets from overlay coordinates into the anchor's, which is all the move costs. */
  fun onOriginChanged(origin: Offset) {
    originInWindow = origin
  }

  fun onPlaced(placement: ReactionOverlayPlacement.Placement) {
    val menu = this.menu ?: return

    if (menuPlaced || !isRevealed) {
      return
    }

    placement.menuHeight?.let { menu.height = it }

    val anchorOrigin = IntArray(2).also { menuAnchor().getLocationInWindow(it) }
    val offsetX = originInWindow.x - anchorOrigin[0] + placement.menuOffsetX
    val offsetY = originInWindow.y - anchorOrigin[1] + placement.menuOffsetY

    menu.show(offsetX.roundToInt(), offsetY.roundToInt())
    menuPlaced = true
  }

  /** While nothing is showing this only remembers where a press landed, for the next [show]. */
  fun applyTouchEvent(motionEvent: MotionEvent): Boolean {
    if (!isShowing || !scrubber.isShowing) {
      if (motionEvent.action == MotionEvent.ACTION_DOWN) {
        lastSeenDownPoint.set(motionEvent.x, motionEvent.y)
      }

      return false
    }

    val outcome = scrubber.apply(motionEvent.action, motionEvent.x, motionEvent.y)

    when (outcome) {
      is ReactionScrubber.Outcome.Scrubbing -> {
        if (outcome.index != outcome.previousIndex) {
          selectedIndex = outcome.index

          if (outcome.index != ReactionScrubber.NO_SELECTION) {
            hapticView().performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
          }
        }
      }

      is ReactionScrubber.Outcome.Commit -> commit(outcome.index)
      is ReactionScrubber.Outcome.Dismiss -> hide()
    }

    return outcome.consumed
  }

  private fun commit(index: Int) {
    val record = messageRecord ?: return

    if (index == REACTION_EMOJI_COUNT - 1) {
      onCustomReactionSelected(record, strip.customSlotHasEmoji)
    } else {
      strip.emojiStrings.getOrNull(index)?.let { emoji ->
        onReactionSelected(record, SignalStore.emoji.getPreferredVariation(emoji))
      }
    }
  }

  private fun onMenuAction(action: ReactionAction) {
    pendingAction = action
    hide()
  }

  /** A reaction not in the strip takes over the last slot in place of the picker. */
  private fun buildStrip(record: MessageRecord): Strip {
    val emojiStrings = SignalStore.emoji.reactions
    val applied = ReactionMenu.appliedEmoji(record)
    val customIndex = REACTION_EMOJI_COUNT - 1

    var appliedIndex = ReactionScrubber.NO_SELECTION
    var customSlotHasEmoji = false
    val drawables = mutableListOf<Drawable?>()

    for (index in 0 until REACTION_EMOJI_COUNT) {
      val isCustomSlot = index == customIndex
      val stripEmoji = emojiStrings.getOrNull(index)

      val matchesApplied = !isCustomSlot &&
        applied != null &&
        stripEmoji != null &&
        EmojiUtil.isCanonicallyEqual(stripEmoji, applied)

      val isUnlistedApplied = isCustomSlot && applied != null

      if (appliedIndex == ReactionScrubber.NO_SELECTION && (matchesApplied || isUnlistedApplied)) {
        appliedIndex = index

        if (isCustomSlot) {
          customSlotHasEmoji = true
          drawables += EmojiProvider.getEmojiDrawable(context, applied, true)
        } else {
          drawables += EmojiProvider.getEmojiDrawable(context, SignalStore.emoji.getPreferredVariation(stripEmoji!!), true)
        }
      } else if (isCustomSlot) {
        drawables += ContextCompat.getDrawable(context, R.drawable.ic_any_emoji_32)
      } else {
        drawables += stripEmoji?.let { EmojiProvider.getEmojiDrawable(context, SignalStore.emoji.getPreferredVariation(it), true) }
      }
    }

    return Strip(
      emojiStrings = emojiStrings,
      drawables = drawables,
      appliedIndex = appliedIndex,
      customSlotHasEmoji = customSlotHasEmoji
    )
  }
}

/** What the overlay reports back as it goes away, matching the pair the view code had. */
interface ReactionOverlayHideListener {
  fun startHide(focusedView: View?)

  fun onHide()
}

/**
 * The picture of the pressed row and where it sits, handed over by the fragment that took it.
 *
 * @param returnPosition Where the row is now, for the hide to fly back to. Null once it is gone.
 */
data class ReactionOverlaySnapshot(
  val bitmap: androidx.compose.ui.graphics.ImageBitmap,
  val bubbleX: Float,
  val bubbleY: Float,
  val bubbleWidth: Int,
  val contextMenuX: Float,
  val isMessageOnLeft: Boolean,
  val returnPosition: () -> Offset?
)
