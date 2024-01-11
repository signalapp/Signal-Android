package org.thoughtcrime.securesms.profiles.spoofing;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.ListAdapter;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.adapter.AlwaysChangedDiffUtil;

import java.util.Objects;

class ReviewCardAdapter extends ListAdapter<ReviewCard, ReviewCardViewHolder> {

  private final @StringRes  int              noGroupsInCommonResId;
  private final @PluralsRes int              groupsInCommonResId;
  private final             CallbacksAdapter callbackAdapter;

  protected ReviewCardAdapter(@StringRes int noGroupsInCommonResId, @PluralsRes int groupsInCommonResId, @NonNull Callbacks callback) {
    super(new AlwaysChangedDiffUtil<>());

    this.noGroupsInCommonResId = noGroupsInCommonResId;
    this.groupsInCommonResId   = groupsInCommonResId;
    this.callbackAdapter       = new CallbacksAdapter(callback);
  }

  @Override
  public @NonNull ReviewCardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ReviewCardViewHolder(LayoutInflater.from(parent.getContext())
                                                  .inflate(R.layout.review_card, parent, false),
                                    noGroupsInCommonResId,
                                    groupsInCommonResId,
                                    callbackAdapter);
  }

  @Override
  public void onBindViewHolder(@NonNull ReviewCardViewHolder holder, int position) {
    holder.bind(getItem(position));
  }

  interface Callbacks {
    void onCardClicked(@NonNull ReviewCard card);
    void onActionClicked(@NonNull ReviewCard card, @NonNull ReviewCard.Action action);
    void onSignalConnectionClicked();
  }

  private final class CallbacksAdapter implements ReviewCardViewHolder.Callbacks {

    private final Callbacks callback;

    private CallbacksAdapter(@NonNull Callbacks callback) {
      this.callback = callback;
    }

    @Override
    public void onCardClicked(int position) {
      callback.onCardClicked(getItem(position));
    }

    @Override
    public void onPrimaryActionItemClicked(int position) {
      ReviewCard card = getItem(position);
      callback.onActionClicked(card, Objects.requireNonNull(card.getPrimaryAction()));
    }

    @Override
    public void onSecondaryActionItemClicked(int position) {
      ReviewCard card = getItem(position);
      callback.onActionClicked(card, Objects.requireNonNull(card.getSecondaryAction()));
    }

    @Override
    public void onSignalConnectionClicked() {
      callback.onSignalConnectionClicked();
    }
  }
}
