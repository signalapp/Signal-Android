package org.thoughtcrime.securesms.avatar.picker

import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.setPadding
import com.airbnb.lottie.SimpleColorFilter
import com.bumptech.glide.Glide
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.AvatarRenderer
import org.thoughtcrime.securesms.avatar.Avatars
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible

typealias OnAvatarClickListener = (Avatar, Boolean) -> Unit
typealias OnAvatarLongClickListener = (View, Avatar) -> Boolean

object AvatarPickerItem {

  private val SELECTION_CHANGED = Any()

  fun register(adapter: MappingAdapter, onAvatarClickListener: OnAvatarClickListener, onAvatarLongClickListener: OnAvatarLongClickListener) {
    adapter.registerFactory(Model::class.java, LayoutFactory({ ViewHolder(it, onAvatarClickListener, onAvatarLongClickListener) }, R.layout.avatar_picker_item))
  }

  class Model(val avatar: Avatar, val isSelected: Boolean) : MappingModel<Model> {
    override fun areItemsTheSame(newItem: Model): Boolean = avatar.isSameAs(newItem.avatar)

    override fun areContentsTheSame(newItem: Model): Boolean = avatar == newItem.avatar && isSelected == newItem.isSelected

    override fun getChangePayload(newItem: Model): Any? {
      return if (newItem.avatar == avatar && isSelected != newItem.isSelected) {
        SELECTION_CHANGED
      } else {
        null
      }
    }
  }

  class ViewHolder(
    itemView: View,
    private val onAvatarClickListener: OnAvatarClickListener? = null,
    private val onAvatarLongClickListener: OnAvatarLongClickListener? = null
  ) : MappingViewHolder<Model>(itemView) {

    private val imageView: ImageView = itemView.findViewById(R.id.avatar_picker_item_image)
    private val textView: TextView = itemView.findViewById(R.id.avatar_picker_item_text)
    private val selectedFader: View? = itemView.findViewById(R.id.avatar_picker_item_fader)
    private val selectedOverlay: View? = itemView.findViewById(R.id.avatar_picker_item_selection_overlay)

    init {
      textView.typeface = AvatarRenderer.getTypeface(context)
      textView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        updateFontSize(textView.text.toString())
      }
    }

    private fun updateFontSize(text: String) {
      val textSize = Avatars.getTextSizeForLength(context, text, textView.measuredWidth * 0.8f, textView.measuredHeight * 0.45f)
      textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)

      if (textView !is EditText) {
        textView.text = text
      }
    }

    override fun bind(model: Model) {
      val alpha = if (model.isSelected) 1f else 0f
      val scale = if (model.isSelected) 0.9f else 1f

      imageView.animate().cancel()
      textView.animate().cancel()
      selectedOverlay?.animate()?.cancel()
      selectedFader?.animate()?.cancel()

      itemView.setOnLongClickListener {
        onAvatarLongClickListener?.invoke(itemView, model.avatar) ?: false
      }

      itemView.setOnClickListener { onAvatarClickListener?.invoke(model.avatar, model.isSelected) }

      if (payload.isNotEmpty() && payload.contains(SELECTION_CHANGED)) {
        imageView.animate().scaleX(scale).scaleY(scale)
        textView.animate().scaleX(scale).scaleY(scale)
        selectedOverlay?.animate()?.alpha(alpha)
        selectedFader?.animate()?.alpha(alpha)
        return
      }

      imageView.scaleX = scale
      imageView.scaleY = scale
      textView.scaleX = scale
      textView.scaleY = scale
      selectedFader?.alpha = alpha
      selectedOverlay?.alpha = alpha

      imageView.clearColorFilter()
      imageView.setPadding(0)

      when (model.avatar) {
        is Avatar.Text -> {
          textView.visible = true

          updateFontSize(model.avatar.text)
          if (textView.text.toString() != model.avatar.text) {
            textView.text = model.avatar.text
          }

          imageView.setImageDrawable(null)
          imageView.background.colorFilter = SimpleColorFilter(model.avatar.color.backgroundColor)
          textView.setTextColor(model.avatar.color.foregroundColor)
        }
        is Avatar.Vector -> {
          textView.visible = false

          val drawableId = Avatars.getDrawableResource(model.avatar.key)
          if (drawableId == null) {
            imageView.setImageDrawable(null)
          } else {
            imageView.setImageDrawable(AppCompatResources.getDrawable(context, drawableId))
          }

          imageView.background.colorFilter = SimpleColorFilter(model.avatar.color.backgroundColor)
        }
        is Avatar.Photo -> {
          textView.visible = false
          Glide.with(imageView).load(DecryptableStreamUriLoader.DecryptableUri(model.avatar.uri)).into(imageView)
        }
        is Avatar.Resource -> {
          imageView.setPadding((imageView.width * 0.2).toInt())
          textView.visible = false
          Glide.with(imageView).clear(imageView)
          imageView.setImageResource(model.avatar.resourceId)
          imageView.colorFilter = SimpleColorFilter(model.avatar.color.foregroundColor)
          imageView.background.colorFilter = SimpleColorFilter(model.avatar.color.backgroundColor)
        }
      }
    }
  }
}
