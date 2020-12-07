package org.thoughtcrime.securesms.delete;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;

import java.util.Objects;

class DeleteAccountCountryPickerAdapter extends ListAdapter<Country, DeleteAccountCountryPickerAdapter.ViewHolder> {

  private final Callback callback;

  protected DeleteAccountCountryPickerAdapter(@NonNull Callback callback) {
    super(new CountryDiffCallback());
    this.callback = callback;
  }

  @Override
  public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(parent.getContext())
                              .inflate(R.layout.delete_account_country_adapter_item, parent, false);

    return new ViewHolder(view, position -> callback.onItemSelected(getItem(position)));
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    holder.textView.setText(getItem(position).getDisplayName());
  }

  static class ViewHolder extends RecyclerView.ViewHolder {

    private final TextView textView;

    public ViewHolder(@NonNull View itemView, @NonNull Consumer<Integer> onItemClickedConsumer) {
      super(itemView);
      textView = itemView.findViewById(android.R.id.text1);

      itemView.setOnClickListener(unused -> {
        if (getAdapterPosition() != RecyclerView.NO_POSITION) {
          onItemClickedConsumer.accept(getAdapterPosition());
        }
      });
    }
  }

  private static class CountryDiffCallback extends DiffUtil.ItemCallback<Country> {

    @Override
    public boolean areItemsTheSame(@NonNull Country oldItem, @NonNull Country newItem) {
      return Objects.equals(oldItem.getCode(), newItem.getCode());
    }

    @Override
    public boolean areContentsTheSame(@NonNull Country oldItem, @NonNull Country newItem) {
      return Objects.equals(oldItem, newItem);
    }
  }

  interface Callback {
    void onItemSelected(@NonNull Country country);
  }
}
