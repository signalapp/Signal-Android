package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.view.View;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.avatars.ContactColors;
import org.thoughtcrime.securesms.contacts.avatars.GeneratedContactPhoto;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;

public class AvatarImageView extends AppCompatImageView {

  private static final String TAG = AvatarImageView.class.getSimpleName();

  private boolean inverted;
  private OnClickListener listener = null;

  public AvatarImageView(Context context) {
    super(context);
    setScaleType(ScaleType.CENTER_CROP);
  }

  public AvatarImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setScaleType(ScaleType.CENTER_CROP);

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AvatarImageView, 0, 0);
      inverted = typedArray.getBoolean(0, false);
      typedArray.recycle();
    }
  }

  @Override
  public void setOnClickListener(OnClickListener listener) {
    this.listener = listener;
    super.setOnClickListener(listener);
  }

  public void setAvatar(@NonNull GlideRequests requestManager, @Nullable Recipient recipient, boolean quickContactEnabled) {
    if (recipient != null) {
      requestManager.load(recipient.getContactPhoto())
                    .fallback(recipient.getFallbackContactPhotoDrawable(getContext(), inverted))
                    .error(recipient.getFallbackContactPhotoDrawable(getContext(), inverted))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .circleCrop()
                    .into(this);
      setAvatarClickHandler(recipient, quickContactEnabled);
    } else {
      setImageDrawable(new GeneratedContactPhoto("#").asDrawable(getContext(), ContactColors.UNKNOWN_COLOR.toConversationColor(getContext()), inverted));
      super.setOnClickListener(listener);
    }
  }

  public void clear(@NonNull GlideRequests glideRequests) {
    glideRequests.clear(this);
  }

  private void setAvatarClickHandler(final Recipient recipient, boolean quickContactEnabled) {
    if (!recipient.isGroupRecipient() && quickContactEnabled) {
      super.setOnClickListener(v -> {
        if (recipient.getContactUri() != null) {
          ContactsContract.QuickContact.showQuickContact(getContext(), AvatarImageView.this, recipient.getContactUri(), ContactsContract.QuickContact.MODE_LARGE, null);
        } else {
          final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
          if (recipient.getAddress().isEmail()) {
            intent.putExtra(ContactsContract.Intents.Insert.EMAIL, recipient.getAddress().toEmailString());
          } else {
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipient.getAddress().toPhoneString());
          }
          intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
          getContext().startActivity(intent);
        }
      });
    } else {
      super.setOnClickListener(listener);
    }
  }

}
