package org.thoughtcrime.securesms.sharing;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.MappingAdapter;
import org.thoughtcrime.securesms.util.MappingViewHolder;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.viewholders.RecipientMappingModel;

public class ShareSelectionViewHolder extends MappingViewHolder<ShareSelectionMappingModel> {

  protected final @NonNull TextView name;

  public ShareSelectionViewHolder(@NonNull View itemView) {
    super(itemView);

    name = findViewById(R.id.recipient_view_name);
  }

  @Override
  public void bind(@NonNull ShareSelectionMappingModel model) {
    name.setText(model.getName(context));
  }

  public static @NonNull MappingAdapter.Factory<ShareSelectionMappingModel> createFactory(@LayoutRes int layout) {
    return new MappingAdapter.LayoutFactory<>(ShareSelectionViewHolder::new, layout);
  }
}
