package org.thoughtcrime.securesms.components.settings.models

import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import com.google.android.material.textfield.TextInputLayout
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import org.signal.core.util.EditTextUtil
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.text.AfterTextChanged

object TextInput {

  sealed class TextInputEvent {
    data class OnKeyEvent(val keyEvent: KeyEvent) : TextInputEvent()
    data class OnEmojiEvent(val emoji: CharSequence) : TextInputEvent()
  }

  fun register(adapter: MappingAdapter, events: Observable<TextInputEvent>) {
    adapter.registerFactory(MultilineModel::class.java, LayoutFactory({ MultilineViewHolder(it, events) }, R.layout.dsl_multiline_text_input))
  }

  class MultilineModel(
    val text: CharSequence?,
    val hint: DSLSettingsText? = null,
    val onEmojiToggleClicked: (EditText) -> Unit,
    val onTextChanged: (CharSequence) -> Unit
  ) : MappingModel<MultilineModel> {
    override fun areItemsTheSame(newItem: MultilineModel): Boolean = true

    override fun areContentsTheSame(newItem: MultilineModel): Boolean = text == newItem.text
  }

  class MultilineViewHolder(itemView: View, private val events: Observable<TextInputEvent>) : MappingViewHolder<MultilineModel>(itemView) {

    private val inputLayout: TextInputLayout = itemView.findViewById(R.id.input_layout)
    private val input: EditText = itemView.findViewById<EditText>(R.id.input).apply {
      EditTextUtil.addGraphemeClusterLimitFilter(this, 700)
    }

    private val emojiToggle: ImageView = itemView.findViewById(R.id.emoji_toggle)

    private var textChangedListener: AfterTextChanged? = null
    private var eventDisposable: Disposable? = null

    override fun onAttachedToWindow() {
      eventDisposable = events.subscribe {
        when (it) {
          is TextInputEvent.OnEmojiEvent -> input.append(it.emoji)
          is TextInputEvent.OnKeyEvent -> input.dispatchKeyEvent(it.keyEvent)
        }
      }
    }

    override fun onDetachedFromWindow() {
      eventDisposable?.dispose()
    }

    override fun bind(model: MultilineModel) {
      inputLayout.hint = model.hint?.resolve(context)

      if (textChangedListener != null) {
        input.removeTextChangedListener(textChangedListener)
      }

      if (model.text.toString() != input.text.toString()) {
        input.setText(model.text)
      }

      textChangedListener = AfterTextChanged { model.onTextChanged(it.toString()) }
      input.addTextChangedListener(textChangedListener)

      // Set Emoji Toggle according to state.
      emojiToggle.setOnClickListener {
        model.onEmojiToggleClicked(input)
      }
    }
  }
}
