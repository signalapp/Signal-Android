package org.thoughtcrime.securesms.blocked;

import android.animation.ValueAnimator;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.conversationlist.model.Conversation;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.Objects;

final class BlockedUsersAdapter extends ListAdapter<Recipient, RecyclerView.ViewHolder> {

  private int mNormalPadding = 30;
  private int mFocusedPadding = 5;
  private int mNormalTextSize = 24;
  private int mFocusedTextSize = 40;
  private int mNormalHeight = 32;
  private int mFocusedHeight = 56;
  private static BlockedUsersFragment.Listener mListener;

  private static final int TYPE_ADD      = 0;
  private static final int TYPE_NORMAL      = 1;

  private final RecipientClickedListener recipientClickedListener;

  BlockedUsersAdapter(@NonNull RecipientClickedListener recipientClickedListener) {
    super(new RecipientDiffCallback());

    this.recipientClickedListener = recipientClickedListener;
  }

  @Override
  public int getItemCount() {
    int count = super.getItemCount();
    return count + 1;
  }

  @Override
  protected Recipient getItem(int position) {
    if (position < 1) {
      return null;
    }

    return super.getItem(position  - 1);
  }

  @Override
  public @NonNull RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    if (viewType == TYPE_ADD) {
      return new ViewHolder0(LayoutInflater.from(parent.getContext()).inflate(R.layout.blocked_users_adapter_item, parent, false));
    } else {
      return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.blocked_users_adapter_item, parent, false),
              position -> recipientClickedListener.onRecipientClicked(Objects.requireNonNull(getItem(position))));
    }
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    if (holder instanceof BlockedUsersAdapter.ViewHolder0) {
      ((ViewHolder0) holder).bind();
      holder.itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
          startFocusAnimation(v.findViewById(R.id.number_or_username), hasFocus);
        }
      });
    } else {
      ((ViewHolder) holder).bind(Objects.requireNonNull(getItem(position)));
      holder.itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
          startFocusAnimation(v.findViewById(R.id.number_or_username), hasFocus);
        }
      });
    }
  }

  @Override
  public int getItemViewType(int position) {
    if (position == 0) {
      return TYPE_ADD;
    } else {
      return TYPE_NORMAL;
    }
  }

  public void setAddListener(BlockedUsersFragment.Listener listener) {
    mListener = listener;
  }

  static class ViewHolder0 extends RecyclerView.ViewHolder {
    private final TextView        numberOrUsername;

    public ViewHolder0(@NonNull View itemView) {
      super(itemView);
      this.numberOrUsername = itemView.findViewById(R.id.number_or_username);

      itemView.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          mListener.handleAddUserToBlockedList();
        }
      });
    }

    public void bind() {
      numberOrUsername.setText(R.string.BlockedUsersActivity__add_blocked_user);
    }
  }

  static class ViewHolder extends RecyclerView.ViewHolder {

    //private final AvatarImageView avatar;
    //private final TextView        displayName;
    private final TextView        numberOrUsername;

    public ViewHolder(@NonNull View itemView, Consumer<Integer> clickConsumer) {
      super(itemView);

      //this.avatar           = itemView.findViewById(R.id.avatar);
      //this.displayName      = itemView.findViewById(R.id.display_name);
      this.numberOrUsername = itemView.findViewById(R.id.number_or_username);

      itemView.setOnClickListener(unused -> {
        if (getAdapterPosition() != RecyclerView.NO_POSITION) {
          clickConsumer.accept(getAdapterPosition());
        }
      });
    }

    public void bind(@NonNull Recipient recipient) {
      //avatar.setAvatar(recipient);
      //displayName.setText(recipient.getDisplayName(itemView.getContext()));

      if (recipient.hasAUserSetDisplayName(itemView.getContext())) {
        String identifier = recipient.getE164().transform(PhoneNumberFormatter::prettyPrint).or(recipient.getUsername()).orNull();

        if (identifier != null) {
          numberOrUsername.setText(identifier);
          numberOrUsername.setVisibility(View.VISIBLE);
        } else {
          numberOrUsername.setVisibility(View.GONE);
        }
      } else {
        numberOrUsername.setVisibility(View.GONE);
      }
    }
  }

  private void startFocusAnimation(TextView tv, boolean focused) {

    ValueAnimator va;
    if (focused) {
      va = ValueAnimator.ofFloat(0, 1);
    } else {
      va = ValueAnimator.ofFloat(1, 0);
    }

    va.addUpdateListener(valueAnimator -> {
      float scale = (float) valueAnimator.getAnimatedValue();
      float height = ((float) (mFocusedHeight - mNormalHeight)) * (scale) + (float) mNormalHeight;
      float textsize = ((float) (mFocusedTextSize - mNormalTextSize)) * (scale) + (float) mNormalTextSize;
      float padding = (float) mNormalPadding - ((float) (mNormalPadding - mFocusedPadding)) * (scale);
      int alpha = (int) ((float) 0x81 + (float) ((0xff - 0x81)) * (scale));
      int color = alpha * 0x1000000 + 0xffffff;

      tv.setTextColor(color);
      tv.setPadding((int) padding, tv.getPaddingTop(), tv.getPaddingRight(), tv.getPaddingBottom());
      tv.setTextSize((int) textsize);
      tv.getLayoutParams().height = (int) height;
    });

    FastOutLinearInInterpolator mInterpolator = new FastOutLinearInInterpolator();
    va.setInterpolator(mInterpolator);
    if (focused) {
      va.setDuration(300);
      va.start();
    } else {
      va.setDuration(300);
      va.start();
    }
    tv.setEllipsize(TextUtils.TruncateAt.MARQUEE);
  }

  private static final class RecipientDiffCallback extends DiffUtil.ItemCallback<Recipient> {

    @Override
    public boolean areItemsTheSame(@NonNull Recipient oldItem, @NonNull Recipient newItem) {
      return oldItem.equals(newItem);
    }

    @Override
    public boolean areContentsTheSame(@NonNull Recipient oldItem, @NonNull Recipient newItem) {
      return oldItem.equals(newItem);
    }
  }

  interface RecipientClickedListener {
    void onRecipientClicked(@NonNull Recipient recipient);
  }
}
