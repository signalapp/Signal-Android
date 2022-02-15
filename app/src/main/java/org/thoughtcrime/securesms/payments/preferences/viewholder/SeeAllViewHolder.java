package org.thoughtcrime.securesms.payments.preferences.viewholder;

import android.view.View;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.payments.preferences.PaymentsHomeAdapter;
import org.thoughtcrime.securesms.payments.preferences.model.SeeAll;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder;

public class SeeAllViewHolder extends MappingViewHolder<SeeAll> {

  private final PaymentsHomeAdapter.Callbacks callbacks;
  private final View                          seeAllButton;

  public SeeAllViewHolder(@NonNull View itemView, PaymentsHomeAdapter.Callbacks callbacks) {
    super(itemView);
    this.callbacks = callbacks;
    this.seeAllButton = itemView.findViewById(R.id.payments_home_see_all_item_button);
  }

  @Override
  public void bind(@NonNull SeeAll model) {
    seeAllButton.setOnClickListener(v -> callbacks.onSeeAll(model.getPaymentType()));
  }
}
