package org.thoughtcrime.securesms.stories.viewer.reply.composer

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ViewSwitcher
import androidx.core.widget.doAfterTextChanged
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ComposeText
import org.thoughtcrime.securesms.components.InputAwareLayout
import org.thoughtcrime.securesms.components.QuoteView
import org.thoughtcrime.securesms.components.emoji.EmojiPageView
import org.thoughtcrime.securesms.components.emoji.EmojiToggle
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.visible

class StoryReplyComposer @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  private val inputAwareLayout: InputAwareLayout
  private val quoteView: QuoteView
  private val privacyChrome: TextView
  private val emojiDrawerToggle: EmojiToggle
  private val emojiDrawer: MediaKeyboard

  val reactionButton: View
  val input: ComposeText

  var isRequestingEmojiDrawer: Boolean = false
    private set

  var callback: Callback? = null

  val emojiPageView: EmojiPageView?
    get() = findViewById(R.id.emoji_page_view)

  init {
    inflate(context, R.layout.stories_reply_to_story_composer, this)

    inputAwareLayout = findViewById(R.id.input_aware_layout)
    emojiDrawerToggle = findViewById(R.id.emoji_toggle)
    quoteView = findViewById(R.id.quote_view)
    input = findViewById(R.id.compose_text)
    reactionButton = findViewById(R.id.reaction)
    privacyChrome = findViewById(R.id.private_reply_recipient)
    emojiDrawer = findViewById(R.id.emoji_drawer)

    val viewSwitcher: ViewSwitcher = findViewById(R.id.reply_reaction_switch)
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

    input.doAfterTextChanged {
      if (it.isNullOrEmpty()) {
        viewSwitcher.displayedChild = 0
      } else {
        viewSwitcher.displayedChild = 1
      }
    }

    reactionButton.setOnClickListener {
      callback?.onPickReactionClicked()
    }

    emojiDrawerToggle.setOnClickListener {
      onEmojiToggleClicked()
    }

    inputAwareLayout.addOnKeyboardShownListener {
      if (inputAwareLayout.currentInput == emojiDrawer && !emojiDrawer.isEmojiSearchMode) {
        onEmojiToggleClicked()
      }
    }
  }

  var hint: CharSequence
    get() {
      return input.hint
    }
    set(value) {
      input.hint = value
    }

  fun setQuote(messageRecord: MediaMmsMessageRecord) {
    quoteView.setQuote(
      GlideApp.with(this),
      messageRecord.dateSent,
      messageRecord.recipient,
      messageRecord.body,
      false,
      messageRecord.slideDeck,
      null,
      QuoteModel.Type.NORMAL
    )

    quoteView.visible = true
  }

  fun displayPrivacyChrome(recipient: Recipient) {
    privacyChrome.text = context.getString(R.string.StoryReplyComposer__replying_privately_to_s, recipient.getDisplayName(context))
    privacyChrome.visible = true
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

  interface Callback {
    fun onSendActionClicked()
    fun onPickReactionClicked()
    fun onInitializeEmojiDrawer(mediaKeyboard: MediaKeyboard)
    fun onShowEmojiKeyboard() = Unit
    fun onHideEmojiKeyboard() = Unit
  }

  data class Input(val body: String, val mentions: List<Mention>, val bodyRanges: BodyRangeList?)
}
