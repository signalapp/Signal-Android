package org.thoughtcrime.securesms.conversation.colors.ui

import android.content.Context
import android.graphics.Path
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.MappingViewHolder
import org.thoughtcrime.securesms.util.customizeOnDraw

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
      edit.visibility = if (model.isCustom) View.VISIBLE else View.GONE

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

      val mask = model.chatColors.chatBubbleMask
      preview.setImageDrawable(
        mask.customizeOnDraw { wrapped, canvas ->
          val circlePath = Path()
          val bounds = canvas.clipBounds
          circlePath.addCircle(
            bounds.width() / 2f,
            bounds.height() / 2f,
            bounds.width() / 2f,
            Path.Direction.CW
          )

          canvas.save()
          canvas.clipPath(circlePath)
          wrapped.draw(canvas)
          canvas.restore()
        }
      )
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
