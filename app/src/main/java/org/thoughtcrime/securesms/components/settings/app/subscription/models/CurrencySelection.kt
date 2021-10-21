package org.thoughtcrime.securesms.components.settings.app.subscription.models

import android.view.View
import android.widget.TextView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.MappingViewHolder

data class CurrencySelection(
  val selectedCurrencyCode: String,
) {

  companion object {
    fun register(adapter: MappingAdapter) {
      adapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it) }, R.layout.subscription_currency_selection))
    }
  }

  class Model(
    val currencySelection: CurrencySelection,
    override val isEnabled: Boolean,
    val onClick: () -> Unit
  ) : PreferenceModel<Model>(isEnabled = isEnabled) {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return true
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) &&
        newItem.currencySelection.selectedCurrencyCode == currencySelection.selectedCurrencyCode
    }
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val spinner: TextView = itemView.findViewById(R.id.subscription_currency_selection_spinner)

    override fun bind(model: Model) {
      spinner.text = model.currencySelection.selectedCurrencyCode

      if (model.isEnabled) {
        itemView.setOnClickListener { model.onClick() }
      }
    }
  }
}
