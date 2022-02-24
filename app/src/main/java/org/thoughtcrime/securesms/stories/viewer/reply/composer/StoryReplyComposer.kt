package org.thoughtcrime.securesms.stories.viewer.reply.composer

import android.app.Dialog
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ViewSwitcher
import androidx.core.widget.doAfterTextChanged
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ComposeText
import org.thoughtcrime.securesms.components.InputAwareLayout
import org.thoughtcrime.securesms.components.QuoteView
import org.thoughtcrime.securesms.components.emoji.EmojiToggle
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.visible

class StoryReplyComposer @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  private val inputAwareLayout: InputAwareLayout
  private val quoteView: QuoteView
  private val reactionButton: View
  private val privacyChrome: TextView
  private val emojiDrawerToggle: EmojiToggle
  private val emojiDrawer: MediaKeyboard

  val input: ComposeText

  var isRequestingEmojiDrawer: Boolean = false
    private set

  var callback: Callback? = null

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
  }

  fun setQuote(messageRecord: MediaMmsMessageRecord) {
    quoteView.setQuote(
      GlideApp.with(this),
      messageRecord.dateSent,
      messageRecord.recipient,
      null,
      false,
      messageRecord.slideDeck,
      null
    )

    quoteView.visible = true
  }

  fun displayPrivacyChrome(recipient: Recipient) {
    privacyChrome.text = context.getString(R.string.StoryReplyComposer__replying_privately_to_s, recipient.getDisplayName(context))
    privacyChrome.visible = true
  }

  fun consumeInput(): Pair<CharSequence, List<Mention>> {
    val trimmedText = input.textTrimmed.toString()
    val mentions = input.mentions

    input.setText("")

    return trimmedText to mentions
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    callback?.onHeightChanged(h)
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

  private fun onEmojiToggleClicked() {
    if (!emojiDrawer.isInitialised) {
      callback?.onInitializeEmojiDrawer(emojiDrawer)
      emojiDrawerToggle.attach(emojiDrawer)
    }

    if (inputAwareLayout.currentInput == emojiDrawer) {
      isRequestingEmojiDrawer = false
      inputAwareLayout.showSoftkey(input)
    } else {
      isRequestingEmojiDrawer = true
      inputAwareLayout.hideSoftkey(input) {
        inputAwareLayout.post {
          inputAwareLayout.show(input, emojiDrawer)
        }
      }
    }
  }

  interface Callback {
    fun onSendActionClicked()
    fun onPickReactionClicked()
    fun onInitializeEmojiDrawer(mediaKeyboard: MediaKeyboard)
    fun onHeightChanged(height: Int)
  }

  companion object {
    fun installIntoBottomSheet(context: Context, dialog: Dialog): StoryReplyComposer {
      val container: ViewGroup = dialog.findViewById(R.id.container)

      val oldComposer: StoryReplyComposer? = container.findViewById(R.id.input)
      if (oldComposer != null) {
        return oldComposer
      }

      val composer = StoryReplyComposer(context)

      composer.id = R.id.input

      container.addView(composer)
      return composer
    }
  }
}
