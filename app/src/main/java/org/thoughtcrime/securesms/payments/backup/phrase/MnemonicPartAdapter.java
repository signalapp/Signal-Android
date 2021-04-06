package org.thoughtcrime.securesms.payments.backup.phrase;

import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.adapter.AlwaysChangedDiffUtil;

final class MnemonicPartAdapter extends ListAdapter<MnemonicPart, MnemonicPartAdapter.ViewHolder> {

  protected MnemonicPartAdapter() {
    super(new AlwaysChangedDiffUtil<>());
  }

  @Override
  public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ViewHolder((TextView) LayoutInflater.from(parent.getContext()).inflate(R.layout.mnemonic_part_adapter_item, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    holder.bind(getItem(position));
  }

  final static class ViewHolder extends RecyclerView.ViewHolder {

    private final TextView view;

    ViewHolder(@NonNull TextView itemView) {
      super(itemView);

      this.view = itemView;
    }

    void bind(@NonNull MnemonicPart mnemonicPart) {
      SpannableStringBuilder builder = new SpannableStringBuilder();

      builder.append(SpanUtil.color(ContextCompat.getColor(view.getContext(), R.color.payment_currency_code_foreground_color),
                                    String.valueOf(mnemonicPart.getIndex() + 1)))
             .append(" ")
             .append(SpanUtil.bold(mnemonicPart.getWord()));

      view.setText(builder);
    }
  }
}
