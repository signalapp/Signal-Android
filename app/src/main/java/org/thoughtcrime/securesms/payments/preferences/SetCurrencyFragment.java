package org.thoughtcrime.securesms.payments.preferences;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.settings.BaseSettingsAdapter;

import java.util.Currency;

public final class SetCurrencyFragment extends LoggingFragment {

  private boolean handledInitialScroll = false;

  public SetCurrencyFragment() {
    super(R.layout.set_currency_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Toolbar      toolbar = view.findViewById(R.id.set_currency_fragment_toolbar);
    RecyclerView list    = view.findViewById(R.id.set_currency_fragment_list);

    toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).popBackStack());

    SetCurrencyViewModel viewModel = new ViewModelProvider(this, new SetCurrencyViewModel.Factory()).get(SetCurrencyViewModel.class);

    BaseSettingsAdapter adapter = new BaseSettingsAdapter();
    adapter.configureSingleSelect(selection -> viewModel.select((Currency) selection));
    list.setAdapter(adapter);

    viewModel.getCurrencyListState().observe(getViewLifecycleOwner(), currencyListState -> {
      adapter.submitList(currencyListState.getItems(), () -> {
        if (currencyListState.isLoaded()               &&
            currencyListState.getSelectedIndex() != -1 &&
            savedInstanceState == null                 &&
            !handledInitialScroll)
        {
          handledInitialScroll = true;
          list.post(() -> list.scrollToPosition(currencyListState.getSelectedIndex()));
        }
      });
    });
  }
}
