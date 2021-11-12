package org.thoughtcrime.securesms.util.viewholders;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.badges.BadgeImageView;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.MappingAdapter;
import org.thoughtcrime.securesms.util.MappingViewHolder;

public class RecipientViewHolder<T extends RecipientMappingModel<T>> extends MappingViewHolder<T> {

  protected final @Nullable AvatarImageView  avatar;
  protected final @Nullable BadgeImageView   badge;
  protected final @Nullable TextView         name;
  protected final @Nullable EventListener<T> eventListener;
  private   final           boolean          quickContactEnabled;

  public RecipientViewHolder(@NonNull View itemView, @Nullable EventListener<T> eventListener) {
    this(itemView, eventListener, false);
  }

  public RecipientViewHolder(@NonNull View itemView, @Nullable EventListener<T> eventListener, boolean quickContactEnabled) {
    super(itemView);
    this.eventListener       = eventListener;
    this.quickContactEnabled = quickContactEnabled;

    avatar = findViewById(R.id.recipient_view_avatar);
    badge  = findViewById(R.id.recipient_view_badge);
    name   = findViewById(R.id.recipient_view_name);
  }

  @Override
  public void bind(@NonNull T model) {
    if (avatar != null) {
      avatar.setRecipient(model.getRecipient(), quickContactEnabled);
    }

    if (badge != null) {
      badge.setBadgeFromRecipient(model.getRecipient());
    }

    if (name != null) {
      name.setText(model.getName(context));
    }

    if (eventListener != null) {
      itemView.setOnClickListener(v -> eventListener.onModelClick(model));
    } else {
      itemView.setOnClickListener(null);
    }
  }

  public static @NonNull <T extends RecipientMappingModel<T>> MappingAdapter.Factory<T> createFactory(@LayoutRes int layout, @Nullable EventListener<T> listener) {
    return new MappingAdapter.LayoutFactory<>(view -> new RecipientViewHolder<>(view, listener), layout);
  }

  public interface EventListener<T extends RecipientMappingModel<T>> {
    default void onModelClick(@NonNull T model) {
      onClick(model.getRecipient());
    }

    void onClick(@NonNull Recipient recipient);
  }
}
