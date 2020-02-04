package org.thoughtcrime.securesms.contacts;

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.loki.redesign.views.ProfilePictureView;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.signalservice.loki.api.LokiAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import network.loki.messenger.R;

public class ContactSelectionListItem extends LinearLayout implements RecipientModifiedListener {

  @SuppressWarnings("unused")
  private static final String TAG = ContactSelectionListItem.class.getSimpleName();

  private ProfilePictureView profilePictureView;
  private TextView        numberView;
  private TextView        nameView;
  private TextView        labelView;
  private CheckBox        checkBox;

  private String        number;
  private Recipient     recipient;
  private GlideRequests glideRequests;
  private long          threadID;

  public ContactSelectionListItem(Context context) {
    super(context);
  }

  public ContactSelectionListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.profilePictureView = findViewById(R.id.profilePictureView);
    this.numberView        = findViewById(R.id.number);
    this.labelView         = findViewById(R.id.label);
    this.nameView          = findViewById(R.id.name);
    this.checkBox          = findViewById(R.id.check_box);

    ViewUtil.setTextViewGravityStart(this.nameView, getContext());
  }

  public void set(@NonNull GlideRequests glideRequests, int type, String name, String number, String label, int color, boolean multiSelect) {
    this.glideRequests = glideRequests;
    this.number        = number;

    if (type == ContactsDatabase.NEW_TYPE) {
      this.recipient = null;
    } else if (!TextUtils.isEmpty(number)) {
      Address address = Address.fromExternal(getContext(), number);
      this.recipient = Recipient.from(getContext(), address, true);
      this.recipient.addListener(this);

      if (this.recipient.getName() != null) {
        name = this.recipient.getName();
      }
    }

    threadID = DatabaseFactory.getThreadDatabase(getContext()).getThreadIdFor(recipient);

    this.numberView.setTextColor(color);
    updateProfilePicture(glideRequests, name, threadID);

    if (!multiSelect && recipient != null && recipient.isLocalNumber()) {
      name = getContext().getString(R.string.note_to_self);
    }

    setText(type, name, number, label);

    if (multiSelect) this.checkBox.setVisibility(View.VISIBLE);
    else             this.checkBox.setVisibility(View.GONE);
  }

  public void setChecked(boolean selected) {
    this.checkBox.setChecked(selected);
  }

  public void unbind(GlideRequests glideRequests) {
    if (recipient != null) {
      recipient.removeListener(this);
      recipient = null;
    }
  }

  private void setText(int type, String name, String number, String label) {
    if (number == null || number.isEmpty() || GroupUtil.isEncodedGroup(number)) {
      this.nameView.setEnabled(false);
      this.numberView.setText("");
      this.labelView.setVisibility(View.GONE);
    } else if (type == ContactsDatabase.PUSH_TYPE) {
      this.numberView.setText(number);
      this.nameView.setEnabled(true);
      this.labelView.setVisibility(View.GONE);
    } else {
      this.numberView.setText(number);
      this.nameView.setEnabled(true);
      this.labelView.setText(label);
      this.labelView.setVisibility(View.VISIBLE);
    }

    this.nameView.setText(name);
  }

  public String getNumber() {
    return number;
  }

  @Override
  public void onModified(final Recipient recipient) {
    if (this.recipient == recipient) {
      Util.runOnMain(() -> {
        threadID = DatabaseFactory.getThreadDatabase(getContext()).getThreadIdFor(recipient);
        updateProfilePicture(glideRequests, recipient.getName(), threadID);
        nameView.setText(recipient.toShortString());
      });
    }
  }

  private void updateProfilePicture(GlideRequests glide, String name, long threadID) {
    if (this.recipient.isGroupRecipient()) {
      Set<String> usersAsSet = LokiAPI.Companion.getUserHexEncodedPublicKeyCache().get(threadID);
      if (usersAsSet == null) {
        usersAsSet = new HashSet<>();
      }
      ArrayList<String> users = new ArrayList<>(usersAsSet);
      Collections.sort(users); // Sort to provide a level of stability
      profilePictureView.setHexEncodedPublicKey(users.size() > 0 ? users.get(0) : "");
      profilePictureView.setAdditionalHexEncodedPublicKey(users.size() > 1 ? users.get(1) : "");
      profilePictureView.setRSSFeed(name.equals("Loki News") || name.equals("Session Updates"));
    } else {
      profilePictureView.setHexEncodedPublicKey(this.number);
      profilePictureView.setAdditionalHexEncodedPublicKey(null);
      profilePictureView.setRSSFeed(false);
    }
    profilePictureView.glide = glide;
    profilePictureView.update();
  }
}
