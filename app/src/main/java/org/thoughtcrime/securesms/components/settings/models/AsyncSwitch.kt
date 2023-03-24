package org.thoughtcrime.securesms.components.settings.models

import android.view.View
import android.widget.ViewSwitcher
import com.google.android.material.materialswitch.MaterialSwitch
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.components.settings.PreferenceViewHolder
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

/**
 * Switch that will perform a long-running async operation (normally network) that requires a
 * progress spinner to replace the switch after a press.
 */
object AsyncSwitch {

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, LayoutFactory(AsyncSwitch::ViewHolder, R.layout.dsl_async_switch_preference_item))
  }

  class Model(
    override val title: DSLSettingsText,
    override val isEnabled: Boolean,
    val isChecked: Boolean,
    val isProcessing: Boolean,
    val onClick: () -> Unit
  ) : PreferenceModel<Model>() {
    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) && isChecked == newItem.isChecked && isProcessing == newItem.isProcessing
    }
  }

  class ViewHolder(itemView: View) : PreferenceViewHolder<Model>(itemView) {
    private val switchWidget: MaterialSwitch = itemView.findViewById(R.id.switch_widget)
    private val switcher: ViewSwitcher = itemView.findViewById(R.id.switcher)

    override fun bind(model: Model) {
      super.bind(model)
      switchWidget.setOnCheckedChangeListener(null)
      switchWidget.isEnabled = model.isEnabled
      switchWidget.isChecked = model.isChecked
      itemView.isEnabled = !model.isProcessing && model.isEnabled
      switcher.displayedChild = if (model.isProcessing) 1 else 0

      fun onClick() {
        if (!model.isProcessing) {
          itemView.isEnabled = false
          switcher.displayedChild = 1
          model.onClick()
        }
      }

      itemView.setOnClickListener {
        onClick()
      }

      switchWidget.setOnCheckedChangeListener { _, _ ->
        onClick()
      }
    }
  }
}
