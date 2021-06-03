package org.thoughtcrime.securesms.keyboard.emoji

import android.view.ViewGroup
import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider
import org.thoughtcrime.securesms.components.emoji.EmojiPageView
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.MappingViewHolder

class EmojiKeyboardPageAdapter(
  private val emojiSelectionListener: EmojiKeyboardProvider.EmojiEventListener,
  private val variationSelectorListener: EmojiPageViewGridAdapter.VariationSelectorListener
) : MappingAdapter() {

  init {
    registerFactory(EmojiPageMappingModel::class.java) { parent ->
      val pageView = EmojiPageView(parent.context, emojiSelectionListener, variationSelectorListener, true)

      val layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
      pageView.layoutParams = layoutParams
      pageView.presentForEmojiKeyboard()

      ViewHolder(pageView)
    }
  }

  private class ViewHolder(
    private val emojiPageView: EmojiPageView,
  ) : MappingViewHolder<EmojiPageMappingModel>(emojiPageView) {

    override fun bind(model: EmojiPageMappingModel) {
      emojiPageView.bindSearchableAdapter(model.emojiPageModel)
    }
  }
}
