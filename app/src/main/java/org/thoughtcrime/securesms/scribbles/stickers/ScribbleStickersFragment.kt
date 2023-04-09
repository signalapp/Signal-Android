package org.thoughtcrime.securesms.scribbles.stickers

import android.content.Context
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyboard.sticker.KeyboardStickerListAdapter
import org.thoughtcrime.securesms.keyboard.sticker.StickerKeyboardPageFragment
import org.thoughtcrime.securesms.util.Throttler
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.fragments.findListener

/**
 * Sticker chooser fragment for the image editor. Implement the Callback for
 * both feature stickers, and regular stickers from StickerKeyboardPageFragment
 */
class ScribbleStickersFragment : StickerKeyboardPageFragment() {

  interface Callback {
    fun onFeatureSticker(sticker: FeatureSticker)
  }

  private val stickerThrottler: Throttler = Throttler(100)

  private val featureStickerList: MappingModelList = MappingModelList(
    listOf(
      FeatureHeader(R.string.ScribbleStickersFragment__featured_stickers),
      FeatureStickerModel(FeatureSticker.ANALOG_CLOCK),
      FeatureStickerModel(FeatureSticker.DIGITAL_CLOCK)
    )
  )

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    stickerListAdapter.registerFactory(FeatureStickerModel::class.java, LayoutFactory(::FeatureStickerViewHolder, R.layout.sticker_keyboard_page_list_item))
    stickerListAdapter.registerFactory(FeatureHeader::class.java, LayoutFactory(::HeaderViewHolder, R.layout.sticker_grid_header))
  }

  override fun updateStickerList(stickers: MappingModelList) {
    stickers.addAll(0, featureStickerList)
    super.updateStickerList(stickers)
  }

  override fun scrollOnLoad() {
  }

  private fun onStickerClick(featureSticker: FeatureSticker) {
    stickerThrottler.publish { findListener<Callback>()?.onFeatureSticker(featureSticker) }
  }

  data class FeatureStickerModel(val featureSticker: FeatureSticker) : MappingModel<FeatureStickerModel> {

    override fun areItemsTheSame(newItem: FeatureStickerModel): Boolean {
      return featureSticker == newItem.featureSticker
    }

    override fun areContentsTheSame(newItem: FeatureStickerModel): Boolean {
      return areItemsTheSame(newItem)
    }
  }

  private inner class FeatureStickerViewHolder(itemView: View) : MappingViewHolder<FeatureStickerModel>(itemView) {

    private val image: ImageView = findViewById(R.id.sticker_keyboard_page_image)

    override fun bind(model: FeatureStickerModel) {
      when (model.featureSticker) {
        FeatureSticker.ANALOG_CLOCK -> bindAnalogClock()
        FeatureSticker.DIGITAL_CLOCK -> bindDigitalClock()
      }
      image.setOnClickListener {
        onStickerClick(model.featureSticker)
      }
    }

    private fun bindAnalogClock() {
      val clockDrawable = AnalogClockStickerDrawable(image.context)
      clockDrawable.start()
      image.setImageDrawable(clockDrawable)
    }

    private fun bindDigitalClock() {
      val clockDrawable = DigitalClockStickerDrawable(image.context)
      clockDrawable.start()
      image.setImageDrawable(clockDrawable)
    }

    override fun onAttachedToWindow() {
      (image.drawable as? Animatable)?.start()
    }

    override fun onDetachedFromWindow() {
      (image.drawable as? Animatable)?.stop()
    }
  }

  data class FeatureHeader(private val titleResource: Int?) : MappingModel<FeatureHeader>, KeyboardStickerListAdapter.Header {
    fun getTitle(context: Context): String {
      return context.resources.getString(titleResource ?: R.string.StickerManagementAdapter_untitled)
    }

    override fun areItemsTheSame(newItem: FeatureHeader): Boolean {
      return titleResource == newItem.titleResource
    }

    override fun areContentsTheSame(newItem: FeatureHeader): Boolean {
      return areItemsTheSame(newItem)
    }
  }

  private inner class HeaderViewHolder(itemView: View) : MappingViewHolder<FeatureHeader>(itemView) {

    private val title: TextView = findViewById(R.id.sticker_grid_header_title)

    override fun bind(model: FeatureHeader) {
      title.text = model.getTitle(context)
    }
  }
}
