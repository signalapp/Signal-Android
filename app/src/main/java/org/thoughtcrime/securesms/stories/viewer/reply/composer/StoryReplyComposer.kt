package org.thoughtcrime.securesms.stories.viewer.reply.composer

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.view.marginEnd
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import org.signal.core.util.dp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ComposeText
import org.thoughtcrime.securesms.components.InputAwareLayout
import org.thoughtcrime.securesms.components.emoji.Emoji
import org.thoughtcrime.securesms.components.emoji.EmojiEventListener
import org.thoughtcrime.securesms.components.emoji.EmojiPageModel
import org.thoughtcrime.securesms.components.emoji.EmojiPageView
import org.thoughtcrime.securesms.components.emoji.EmojiToggle
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.emoji.EmojiSource
import org.thoughtcrime.securesms.keyboard.emoji.toMappingModels
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel

class StoryReplyComposer @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  private val inputAwareLayout: InputAwareLayout
  private val emojiDrawerToggle: EmojiToggle
  private val emojiDrawer: MediaKeyboard
  private val reactionEmojiView: EmojiPageView
  private val anyReactionView: View
  private val emojiBar: View
  private val bubbleView: ViewGroup

  val input: ComposeText
  val decoration: SpacingDecoration

  var isRequestingEmojiDrawer: Boolean = false
    private set

  var callback: Callback? = null

  val emojiPageView: EmojiPageView?
    get() = findViewById(R.id.emoji_page_view)

  init {
    inflate(context, R.layout.stories_reply_to_story_composer, this)

    inputAwareLayout = findViewById(R.id.input_aware_layout)
    emojiDrawerToggle = findViewById(R.id.emoji_toggle)
    input = findViewById(R.id.compose_text)
    emojiDrawer = findViewById(R.id.emoji_drawer)
    anyReactionView = findViewById(R.id.any_reaction)
    reactionEmojiView = findViewById(R.id.reaction_emoji_view)
    emojiBar = findViewById(R.id.emoji_bar)
    bubbleView = findViewById(R.id.bubble)

    val reply: View = findViewById(R.id.reply)

    reply.setOnClickListener {
      callback?.onSendActionClicked()
    }
    input.setOnEditorActionListener { _, actionId, _ ->
      when (actionId) {
        EditorInfo.IME_ACTION_SEND -> {
          callback?.onSendActionClicked()
          true
        }
        else -> false
      }
    }

    anyReactionView.setOnClickListener {
      callback?.onPickAnyReactionClicked()
    }

    input.doAfterTextChanged {
      val notEmpty = !it.isNullOrEmpty()
      reply.isEnabled = notEmpty
      if (notEmpty && reply.visibility != View.VISIBLE) {
        val transition = AutoTransition().setDuration(200L).setInterpolator(OvershootInterpolator(1f))
        TransitionManager.beginDelayedTransition(bubbleView, transition)
        reply.visibility = View.VISIBLE
        reply.scaleX = 0f
        reply.scaleY = 0f
        reply.animate().setDuration(150).scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator(1f)).start()
      }
    }

    emojiDrawerToggle.setOnClickListener {
      onEmojiToggleClicked()
    }

    inputAwareLayout.addOnKeyboardShownListener {
      if (inputAwareLayout.currentInput == emojiDrawer && !emojiDrawer.isEmojiSearchMode) {
        onEmojiToggleClicked()
      }
    }

    val emojiEventListener: EmojiEventListener = object : EmojiEventListener {
      override fun onEmojiSelected(emoji: String?) {
        if (emoji != null) {
          callback?.onReactionClicked(emoji)
        }
      }
      override fun onKeyEvent(keyEvent: KeyEvent?) = Unit
    }

    reactionEmojiView.initialize(
      emojiEventListener,
      { },
      false,
      LinearLayoutManager(context, RecyclerView.HORIZONTAL, false),
      R.layout.emoji_display_item_list,
      R.layout.emoji_text_display_item_list
    )
    decoration = SpacingDecoration()
    reactionEmojiView.addItemDecoration(decoration)
    reactionEmojiView.setList(getReactionEmojis()) {
      updateEmojiSpacing()
    }
  }

  var hint: CharSequence
    get() {
      return input.hint
    }
    set(value) {
      input.hint = value
    }

  fun displayReplyHint(recipient: Recipient) {
    input.hint = (context.getString(R.string.StoryReplyComposer__reply_to_s, recipient.getDisplayName(context)))
  }

  fun consumeInput(): Input {
    val trimmedText = input.textTrimmed.toString()
    val mentions = input.mentions
    val bodyRanges = input.styling

    input.setText("")

    return Input(trimmedText, mentions, bodyRanges)
  }

  fun openEmojiSearch() {
    emojiDrawer.onOpenEmojiSearch()
  }

  fun onEmojiSelected(emoji: String?) {
    input.insertEmoji(emoji)
  }

  fun closeEmojiSearch() {
    emojiDrawer.onCloseEmojiSearch()
  }

  fun close() {
    inputAwareLayout.hideCurrentInput(input)
  }

  private fun getReactionEmojis(): List<MappingModel<*>> {
    val reactionDisplayEmoji: List<Emoji> = SignalStore.emojiValues().reactions.map { Emoji(it) }
    val canonicalReactionEmoji: List<String> = reactionDisplayEmoji.map { EmojiSource.latest.variationsToCanonical[it.value] ?: it.value }
    val canonicalRecentReactionEmoji: Set<String> = LinkedHashSet(RecentEmojiPageModel(context, ReactWithAnyEmojiBottomSheetDialogFragment.REACTION_STORAGE_KEY).emoji) - canonicalReactionEmoji.toSet()

    val recentDisplayEmoji: List<Emoji> = canonicalRecentReactionEmoji
      .mapNotNull { canonical -> EmojiSource.latest.canonicalToVariations[canonical] }
      .map { Emoji(it) }

    return EmojiReactionsPageModel(canonicalReactionEmoji + canonicalRecentReactionEmoji, reactionDisplayEmoji + recentDisplayEmoji).toMappingModels()
  }

  private fun onEmojiToggleClicked() {
    if (!emojiDrawer.isInitialised) {
      callback?.onInitializeEmojiDrawer(emojiDrawer)
      emojiDrawerToggle.attach(emojiDrawer)
    }

    if (inputAwareLayout.currentInput == emojiDrawer) {
      isRequestingEmojiDrawer = false
      inputAwareLayout.showSoftkey(input)
      callback?.onHideEmojiKeyboard()
    } else {
      isRequestingEmojiDrawer = true
      inputAwareLayout.show(input, emojiDrawer)
      emojiDrawer.post { callback?.onShowEmojiKeyboard() }
    }
  }

  private fun updateEmojiSpacing() {
    val emojiItemWidth = 44.dp
    val availableWidth = reactionEmojiView.width - anyReactionView.marginEnd
    val maxNumItems = availableWidth / emojiItemWidth
    val numItems = reactionEmojiView.adapter?.itemCount ?: 0

    decoration.firstItemOffset = anyReactionView.marginEnd

    if (numItems > maxNumItems) {
      decoration.horizontalSpacing = 0
      reactionEmojiView.invalidateItemDecorations()
    } else {
      decoration.horizontalSpacing = (availableWidth - (numItems * emojiItemWidth)) / numItems
      reactionEmojiView.invalidateItemDecorations()
    }
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    updateEmojiSpacing()
  }

  interface Callback {
    fun onSendActionClicked()
    fun onPickAnyReactionClicked()
    fun onReactionClicked(emoji: String)
    fun onInitializeEmojiDrawer(mediaKeyboard: MediaKeyboard)
    fun onShowEmojiKeyboard() = Unit
    fun onHideEmojiKeyboard() = Unit
  }

  class SpacingDecoration : RecyclerView.ItemDecoration() {
    var horizontalSpacing: Int = 0
    var firstItemOffset: Int = 0

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
      super.getItemOffsets(outRect, view, parent, state)
      outRect.right = horizontalSpacing
      if (parent.getChildAdapterPosition(view) == 0) {
        outRect.left = firstItemOffset
      } else {
        outRect.left = 0
      }
    }
  }

  private class EmojiReactionsPageModel(private val emoji: List<String>, private val displayEmoji: List<Emoji>) : EmojiPageModel {
    override fun getKey(): String = ""
    override fun getIconAttr(): Int = -1
    override fun getEmoji(): List<String> = emoji
    override fun getDisplayEmoji(): List<Emoji> = displayEmoji
    override fun getSpriteUri(): Uri? = null
    override fun isDynamic(): Boolean = false
  }

  data class Input(val body: String, val mentions: List<Mention>, val bodyRanges: BodyRangeList?)
}
