package org.thoughtcrime.securesms.conversation.colors.ui

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.TooltipPopup
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.withFixedSize

class ChatColorSelectionAdapter(
  context: Context,
  private val callbacks: Callbacks
) : MappingAdapter() {
  init {
    val popupWindow = ChatSelectionContextMenu(context)

    registerFactory(
      ChatColorMappingModel::class.java,
      LayoutFactory(
        { v -> ChatColorMappingViewHolder(v, popupWindow, callbacks) },
        R.layout.chat_color_selection_adapter_item
      )
    )
    registerFactory(
      CustomColorMappingModel::class.java,
      LayoutFactory(
        { v -> CustomColorMappingViewHolder(v, callbacks::onAdd) },
        R.layout.chat_color_custom_adapter_item
      )
    )
  }

  class CustomColorMappingViewHolder(
    itemView: View,
    onClicked: () -> Unit
  ) : MappingViewHolder<CustomColorMappingModel>(itemView) {

    init {
      itemView.setOnClickListener { onClicked() }
    }

    override fun bind(model: CustomColorMappingModel) = Unit
  }

  class ChatColorMappingViewHolder(
    itemView: View,
    private val popupWindow: ChatSelectionContextMenu,
    private val callbacks: Callbacks
  ) : MappingViewHolder<ChatColorMappingModel>(itemView) {

    private val preview: ImageView = itemView.findViewById(R.id.chat_color)
    private val auto: TextView = itemView.findViewById(R.id.auto)
    private val edit: View = itemView.findViewById(R.id.edit)

    override fun bind(model: ChatColorMappingModel) {
      itemView.isSelected = model.isSelected

      auto.visibility = if (model.isAuto) View.VISIBLE else View.GONE
      edit.visibility = if (model.isCustom && model.isSelected) View.VISIBLE else View.GONE

      preview.setOnClickListener {
        if (model.isCustom && model.isSelected) {
          callbacks.onEdit(model.chatColors)
        } else {
          callbacks.onSelect(model.chatColors)
        }
      }

      if (model.isCustom) {
        preview.setOnLongClickListener {
          popupWindow.callback = CallbackBinder(callbacks, model.chatColors)
          popupWindow.show(itemView)
          true
        }
      } else {
        preview.setOnLongClickListener(null)
        preview.isLongClickable = false
      }

      val mask = model.chatColors.asCircle()
      preview.setImageDrawable(mask.withFixedSize(ViewUtil.dpToPx(56)))

      if (model.isAuto && SignalStore.chatColors.shouldShowAutoTooltip) {
        SignalStore.chatColors.shouldShowAutoTooltip = false
        TooltipPopup.forTarget(itemView)
          .setText(R.string.ChatColorSelectionFragment__auto_matches_the_color_to_the_wallpaper)
          .setBackgroundTint(ContextCompat.getColor(context, R.color.signal_accent_primary))
          .setTextColor(Color.WHITE)
          .show(TooltipPopup.POSITION_BELOW)
      }
    }
  }

  class CallbackBinder(private val callbacks: Callbacks, private val chatColors: ChatColors) : ChatSelectionContextMenu.Callback {
    override fun onEditPressed() {
      callbacks.onEdit(chatColors)
    }

    override fun onDuplicatePressed() {
      callbacks.onDuplicate(chatColors)
    }

    override fun onDeletePressed() {
      callbacks.onDelete(chatColors)
    }
  }

  interface Callbacks {
    fun onSelect(chatColors: ChatColors)
    fun onEdit(chatColors: ChatColors)
    fun onDuplicate(chatColors: ChatColors)
    fun onDelete(chatColors: ChatColors)
    fun onAdd()
  }
}
