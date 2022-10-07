package org.thoughtcrime.securesms.stories.settings.my

import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.AllSignalConnectionsRowItemBinding
import org.thoughtcrime.securesms.util.adapter.mapping.BindingFactory
import org.thoughtcrime.securesms.util.adapter.mapping.BindingViewHolder
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.visible

/**
 * AllSignalConnections privacy setting row item with "View" support
 */
object AllSignalConnectionsRowItem {

  private const val IS_CHECKED = 0
  private const val IS_COUNT = 1

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, BindingFactory(::ViewHolder, AllSignalConnectionsRowItemBinding::inflate))
  }

  class Model(
    val isChecked: Boolean,
    val count: Int,
    val onRowClicked: () -> Unit,
    val onViewClicked: () -> Unit
  ) : MappingModel<Model> {

    override fun areItemsTheSame(newItem: Model): Boolean = true

    override fun areContentsTheSame(newItem: Model): Boolean = isChecked == newItem.isChecked && count == newItem.count

    override fun getChangePayload(newItem: Model): Any? {
      val isCheckedDifferent = isChecked != newItem.isChecked
      val isCountDifferent = count != newItem.count

      return when {
        isCheckedDifferent && !isCountDifferent -> IS_CHECKED
        !isCheckedDifferent && isCountDifferent -> IS_COUNT
        else -> null
      }
    }
  }

  private class ViewHolder(binding: AllSignalConnectionsRowItemBinding) : BindingViewHolder<Model, AllSignalConnectionsRowItemBinding>(binding) {
    override fun bind(model: Model) {
      binding.root.setOnClickListener { model.onRowClicked() }
      binding.view.setOnClickListener { model.onViewClicked() }

      when {
        payload.contains(IS_COUNT) -> presentCount(model.count)
        payload.contains(IS_CHECKED) -> presentSelected(model.isChecked)
        else -> {
          presentCount(model.count)
          presentSelected(model.isChecked)
        }
      }
    }

    private fun presentCount(count: Int) {
      binding.count.visible = count > 0
      binding.count.text = context.resources.getQuantityString(R.plurals.MyStorySettingsFragment__viewers, count, count)
    }

    private fun presentSelected(isChecked: Boolean) {
      binding.radio.isChecked = isChecked
    }
  }
}
