package org.thoughtcrime.securesms.contacts;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.FromTextView;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.libsignal.util.guava.Optional;

public class ContactSelectionListItem extends LinearLayout implements RecipientForeverObserver {

  @SuppressWarnings("unused")
  private static final String TAG = ContactSelectionListItem.class.getSimpleName();

  private AvatarImageView contactPhotoImage;
  private TextView        numberView;
  private FromTextView    nameView;
  private TextView        labelView;
  private CheckBox        checkBox;

  private String        number;
  private String        chipName;
  private int           contactType;
  private LiveRecipient recipient;
  private GlideRequests glideRequests;

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

    ViewUtil.setTextViewGravityStart(this.nameView, getContext());
  }

  public void set(@NonNull GlideRequests glideRequests,
                  @Nullable RecipientId recipientId,
                  int type,
                  String name,
                  String number,
                  String label,
                  String about,
                  int color,
                  boolean checkboxVisible)
  {
    this.glideRequests = glideRequests;
    this.number        = number;
    this.contactType   = type;

    if (type == ContactRepository.NEW_PHONE_TYPE || type == ContactRepository.NEW_USERNAME_TYPE) {
      this.recipient = null;
      this.contactPhotoImage.setAvatar(glideRequests, null, false);
    } else if (recipientId != null) {
      this.recipient = Recipient.live(recipientId);
      this.recipient.observeForever(this);
      name = this.recipient.get().getDisplayName(getContext());
    }

    Recipient recipientSnapshot = recipient != null ? recipient.get() : null;

    this.nameView.setTextColor(color);
    this.numberView.setTextColor(color);
    this.contactPhotoImage.setAvatar(glideRequests, recipientSnapshot, false);

    setText(recipientSnapshot, type, name, number, label, about);

    this.checkBox.setVisibility(checkboxVisible ? View.VISIBLE : View.GONE);
  }

  public void setChecked(boolean selected) {
    this.checkBox.setChecked(selected);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    this.checkBox.setEnabled(enabled);
  }

  public void unbind(GlideRequests glideRequests) {
    if (recipient != null) {
      recipient.removeForeverObserver(this);
      recipient = null;
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
    } else {
      this.numberView.setText(!Util.isEmpty(about) ? about : number);
      this.nameView.setEnabled(true);
      this.labelView.setText(label != null && !label.equals("null") ? label : "");
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
    contactPhotoImage.setAvatar(glideRequests, recipient, false);
    nameView.setText(recipient);
    if (recipient.isGroup()) {
      numberView.setText(getGroupMemberCount(recipient));
    }
  }
}
