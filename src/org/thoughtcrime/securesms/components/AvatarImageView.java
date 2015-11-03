package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.Pair;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.contacts.avatars.ContactColors;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhotoFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.thoughtcrime.securesms.util.DirectoryHelper.UserCapabilities.Capability;

public class AvatarImageView extends ImageView {

  private boolean inverted;
  private boolean showBadge;

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
      showBadge = typedArray.getBoolean(1, false);
      typedArray.recycle();
    }
  }

  public void setAvatar(final @Nullable Recipients recipients, boolean quickContactEnabled) {
    if (recipients != null) {
      MaterialColor backgroundColor = recipients.getColor();
      setImageDrawable(recipients.getContactPhoto().asDrawable(getContext(), backgroundColor.toConversationColor(getContext()), inverted));
      setAvatarClickHandler(recipients, quickContactEnabled);
      setTag(recipients);
      if (showBadge) new BadgeResolutionTask(getContext()).execute(recipients);
    } else {
      setImageDrawable(ContactPhotoFactory.getDefaultContactPhoto(null).asDrawable(getContext(), ContactColors.UNKNOWN_COLOR.toConversationColor(getContext()), inverted));
      setOnClickListener(null);
      setTag(null);
    }
  }

  public void setAvatar(@Nullable Recipient recipient, boolean quickContactEnabled) {
    setAvatar(RecipientFactory.getRecipientsFor(getContext(), recipient, true), quickContactEnabled);
  }

  private void setAvatarClickHandler(final Recipients recipients, boolean quickContactEnabled) {
    if (!recipients.isGroupRecipient() && quickContactEnabled) {
      setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          Recipient recipient = recipients.getPrimaryRecipient();

          if (recipient != null && recipient.getContactUri() != null) {
            ContactsContract.QuickContact.showQuickContact(getContext(), AvatarImageView.this, recipient.getContactUri(), ContactsContract.QuickContact.MODE_LARGE, null);
          } else if (recipient != null) {
            final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipient.getNumber());
            intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
            getContext().startActivity(intent);
          }
        }
      });
    } else {
      setOnClickListener(null);
    }
  }

  private class BadgeResolutionTask extends AsyncTask<Recipients,Void,Pair<Recipients, Boolean>> {
    private final Context context;

    public BadgeResolutionTask(Context context) {
      this.context = context;
    }

    @Override
    protected Pair<Recipients, Boolean> doInBackground(Recipients... recipients) {
      Capability textCapability = DirectoryHelper.getUserCapabilities(context, recipients[0]).getTextCapability();
      return new Pair<>(recipients[0], textCapability == Capability.SUPPORTED);
    }

    @Override
    protected void onPostExecute(Pair<Recipients, Boolean> result) {
      if (getTag() == result.first && result.second) {
        final Drawable badged = new LayerDrawable(new Drawable[] {
            getDrawable(),
            ContextCompat.getDrawable(context, R.drawable.badge_drawable)
        });

        setImageDrawable(badged);
      }
    }
  }
}
