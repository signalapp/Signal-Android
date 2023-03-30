package org.thoughtcrime.securesms.keyboard.sticker

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.StickerRecord
import org.thoughtcrime.securesms.glide.cache.ApngOptions
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

class KeyboardStickerListAdapter(
  private val glideRequests: GlideRequests,
  private val eventListener: EventListener?,
  private val allowApngAnimation: Boolean
) : MappingAdapter() {

  init {
    registerFactory(Sticker::class.java, LayoutFactory(::StickerViewHolder, R.layout.sticker_keyboard_page_list_item))
    registerFactory(StickerHeader::class.java, LayoutFactory(::StickerHeaderViewHolder, R.layout.sticker_grid_header))
  }

  data class Sticker(override val packId: String, val stickerRecord: StickerRecord) : MappingModel<Sticker>, HasPackId {
    val uri: DecryptableUri
      get() = DecryptableUri(stickerRecord.uri)

    override fun areItemsTheSame(newItem: Sticker): Boolean {
      return packId == newItem.packId && stickerRecord.rowId == newItem.stickerRecord.rowId
    }

    override fun areContentsTheSame(newItem: Sticker): Boolean {
      return areItemsTheSame(newItem)
    }
  }

  private inner class StickerViewHolder(itemView: View) : MappingViewHolder<Sticker>(itemView) {

    private val image: ImageView = findViewById(R.id.sticker_keyboard_page_image)

    override fun bind(model: Sticker) {
      glideRequests.load(model.uri)
        .set(ApngOptions.ANIMATE, allowApngAnimation)
        .transition(DrawableTransitionOptions.withCrossFade())
        .into(image)

      if (eventListener != null) {
        itemView.setOnClickListener { eventListener.onStickerClicked(model) }
        itemView.setOnLongClickListener {
          eventListener.onStickerLongClicked(model)
          true
        }
      } else {
        itemView.setOnClickListener(null)
        itemView.setOnLongClickListener(null)
      }
    }
  }

  data class StickerHeader(override val packId: String, private val title: String?, private val titleResource: Int?) : MappingModel<StickerHeader>, HasPackId, Header {
    fun getTitle(context: Context): String {
      return title ?: context.resources.getString(titleResource ?: R.string.StickerManagementAdapter_untitled)
    }

    override fun areItemsTheSame(newItem: StickerHeader): Boolean {
      return title == newItem.title
    }

    override fun areContentsTheSame(newItem: StickerHeader): Boolean {
      return areItemsTheSame(newItem)
    }
  }

  private class StickerHeaderViewHolder(itemView: View) : MappingViewHolder<StickerHeader>(itemView) {

    private val title: TextView = findViewById(R.id.sticker_grid_header_title)

    override fun bind(model: StickerHeader) {
      title.text = model.getTitle(context)
    }
  }

  interface Header
  interface HasPackId {
    val packId: String
  }

  interface EventListener {
    fun onStickerClicked(sticker: Sticker)
    fun onStickerLongClicked(sticker: Sticker)
  }
}
