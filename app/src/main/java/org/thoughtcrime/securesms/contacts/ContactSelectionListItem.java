package org.thoughtcrime.securesms.contacts;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.badges.BadgeImageView;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.FromTextView;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.libsignal.util.guava.Optional;

public class ContactSelectionListItem extends ConstraintLayout implements RecipientForeverObserver {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(ContactSelectionListItem.class);

  private AvatarImageView contactPhotoImage;
  private TextView        numberView;
  private FromTextView    nameView;
  private TextView        labelView;
  private CheckBox        checkBox;
  private View            smsTag;
  private BadgeImageView  badge;

  private String           number;
  private String           chipName;
  private int              contactType;
  private String           contactName;
  private String           contactNumber;
  private String           contactLabel;
  private String           contactAbout;
  private LiveRecipient    recipient;
  private GlideRequests    glideRequests;

  public ContactSelectionListItem(Context context) {
    super(context);
  }

  public ContactSelectionListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.contactPhotoImage = findViewById(R.id.contact_photo_image);
    this.numberView        = findViewById(R.id.number);
    this.labelView         = findViewById(R.id.label);
    this.nameView          = findViewById(R.id.name);
    this.checkBox          = findViewById(R.id.check_box);
    this.smsTag            = findViewById(R.id.sms_tag);
    this.badge             = findViewById(R.id.contact_badge);

    ViewUtil.setTextViewGravityStart(this.nameView, getContext());
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (recipient != null) {
      recipient.observeForever(this);
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    unbind();
  }

  public void set(@NonNull GlideRequests glideRequests,
                  @Nullable RecipientId recipientId,
                  int type,
                  String name,
                  String number,
                  String label,
                  String about,
                  boolean checkboxVisible)
  {
    this.glideRequests = glideRequests;
    this.number        = number;
    this.contactType   = type;
    this.contactName   = name;
    this.contactNumber = number;
    this.contactLabel  = label;
    this.contactAbout  = about;

    if (type == ContactRepository.NEW_PHONE_TYPE || type == ContactRepository.NEW_USERNAME_TYPE) {
      this.recipient = null;
      this.contactPhotoImage.setAvatar(glideRequests, null, false);
    } else if (recipientId != null) {
      if (this.recipient != null) {
        this.recipient.removeForeverObserver(this);
      }
      this.recipient = Recipient.live(recipientId);
      this.recipient.observeForever(this);
    }

    Recipient recipientSnapshot = recipient != null ? recipient.get() : null;

    if (recipientSnapshot != null && !recipientSnapshot.isResolving() && !recipientSnapshot.isMyStory()) {
      contactName = recipientSnapshot.getDisplayName(getContext());
      name        = contactName;
    } else if (recipient != null) {
      name = "";
    }

    if (recipientSnapshot == null || recipientSnapshot.isResolving() || recipientSnapshot.isRegistered() || recipientSnapshot.isDistributionList()) {
      smsTag.setVisibility(GONE);
    } else {
      smsTag.setVisibility(VISIBLE);
    }

    if (recipientSnapshot == null || recipientSnapshot.isResolving()) {
      this.contactPhotoImage.setAvatar(glideRequests, null, false);
      setText(null, type, name, number, label, about);
    } else if (recipientSnapshot.isMyStory()) {
      this.contactPhotoImage.setRecipient(Recipient.self(), false);
      setText(recipientSnapshot, type, name, number, label, about);
    } else {
      this.contactPhotoImage.setAvatar(glideRequests, recipientSnapshot, false);
      setText(recipientSnapshot, type, name, number, label, about);
    }

    this.checkBox.setVisibility(checkboxVisible ? View.VISIBLE : View.GONE);

    if (recipientSnapshot == null || recipientSnapshot.isSelf()) {
      badge.setBadge(null);
    } else {
      badge.setBadgeFromRecipient(recipientSnapshot);
    }
  }

  public void setChecked(boolean selected, boolean animate) {
    checkBox.setChecked(selected);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    this.checkBox.setEnabled(enabled);
  }

  public void unbind() {
    if (recipient != null) {
      recipient.removeForeverObserver(this);
    }
  }

  @SuppressLint("SetTextI18n")
  private void setText(@Nullable Recipient recipient, int type, String name, String number, String label, @Nullable String about) {
    if (number == null || number.isEmpty()) {
      this.nameView.setEnabled(false);
      this.numberView.setText("");
      this.labelView.setVisibility(View.GONE);
    } else if (recipient != null && recipient.isGroup()) {
      this.nameView.setEnabled(false);
      this.numberView.setText(getGroupMemberCount(recipient));
      this.labelView.setVisibility(View.GONE);
    } else if (type == ContactRepository.PUSH_TYPE) {
      this.numberView.setText(!Util.isEmpty(about) ? about : number);
      this.nameView.setEnabled(true);
      this.labelView.setVisibility(View.GONE);
    } else if (type == ContactRepository.NEW_USERNAME_TYPE) {
      this.numberView.setText("@" + number);
      this.nameView.setEnabled(true);
      this.labelView.setText(label);
      this.labelView.setVisibility(View.VISIBLE);
    } else if (recipient != null && recipient.isDistributionList()) {
      this.numberView.setText(getViewerCount(number));
      this.labelView.setVisibility(View.GONE);
    } else {
      this.numberView.setText(!Util.isEmpty(about) ? about : number);
      this.nameView.setEnabled(true);
      this.labelView.setText(label != null && !label.equals("null") ? getResources().getString(R.string.ContactSelectionListItem__dot_s, label) : "");
      this.labelView.setVisibility(View.VISIBLE);
    }

    if (recipient != null) {
      this.nameView.setText(recipient);
      chipName = recipient.getShortDisplayName(getContext());
    } else {
      this.nameView.setText(name);
      chipName = name;
    }
  }

  public String getNumber() {
    return number;
  }

  public String getChipName() {
    return chipName;
  }

  private String getGroupMemberCount(@NonNull Recipient recipient) {
    if (!recipient.isGroup()) {
      throw new AssertionError();
    }
    int memberCount = recipient.getParticipants().size();
    return getContext().getResources().getQuantityString(R.plurals.contact_selection_list_item__number_of_members, memberCount, memberCount);
  }

  private String getViewerCount(@NonNull String number) {
    int viewerCount = Integer.parseInt(number);
    return getContext().getResources().getQuantityString(R.plurals.contact_selection_list_item__number_of_viewers, viewerCount, viewerCount);
  }

  public @Nullable LiveRecipient getRecipient() {
    return recipient;
  }

  public boolean isUsernameType() {
    return contactType == ContactRepository.NEW_USERNAME_TYPE;
  }

  public Optional<RecipientId> getRecipientId() {
    return recipient != null ? Optional.of(recipient.getId()) : Optional.absent();
  }

  @Override
  public void onRecipientChanged(@NonNull Recipient recipient) {
    if (this.recipient != null && this.recipient.getId().equals(recipient.getId())) {
      contactName   = recipient.getDisplayName(getContext());
      contactAbout  = recipient.getCombinedAboutAndEmoji();

      if (recipient.isGroup() && recipient.getGroupId().isPresent()) {
        contactNumber = recipient.getGroupId().get().toString();
      } else if (recipient.hasE164()) {
        contactNumber = PhoneNumberFormatter.prettyPrint(recipient.getE164().or(""));
      } else if (!recipient.isDistributionList()) {
        contactNumber = recipient.getEmail().or("");
      }

      if (recipient.isMyStory()) {
        contactPhotoImage.setRecipient(Recipient.self(), false);
      } else {
        contactPhotoImage.setAvatar(glideRequests, recipient, false);
      }

      setText(recipient, contactType, contactName, contactNumber, contactLabel, contactAbout);
      smsTag.setVisibility(recipient.isRegistered() || recipient.isDistributionList() ? GONE : VISIBLE);
      badge.setBadgeFromRecipient(recipient);
    } else {
      Log.w(TAG, "Bad change! Local recipient doesn't match. Ignoring. Local: " + (this.recipient == null ? "null" : this.recipient.getId()) + ", Changed: " + recipient.getId());
    }
  }
}
