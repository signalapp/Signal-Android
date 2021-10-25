package org.thoughtcrime.securesms.components.settings.app.subscription.currency

import androidx.fragment.app.viewModels
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsBottomSheetFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import java.util.Locale

/**
 * Simple fragment for selecting a currency for Donations
 */
class SetCurrencyFragment : DSLSettingsBottomSheetFragment() {

  private val viewModel: SetCurrencyViewModel by viewModels(
    factoryProducer = {
      SetCurrencyViewModel.Factory(SetCurrencyFragmentArgs.fromBundle(requireArguments()).isBoost)
    }
  )

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: SetCurrencyState): DSLConfiguration {
    return configure {
      state.currencies.forEach { currency ->
        radioPref(
          title = DSLSettingsText.from(currency.getDisplayName(Locale.getDefault())),
          summary = DSLSettingsText.from(currency.currencyCode),
          isChecked = currency.currencyCode == state.selectedCurrencyCode,
          onClick = {
            viewModel.setSelectedCurrency(currency.currencyCode)
          }
        )
      }
    }
  }
}
