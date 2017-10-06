package org.thoughtcrime.securesms;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.lang.ref.WeakReference;

public class ConversationTitleView extends RelativeLayout {

  private static final String TAG = ConversationTitleView.class.getSimpleName();

  private View            content;
  private ImageView       back;
  private AvatarImageView avatar;
  private TextView        title;
  private TextView        subtitle;
  private ImageView       verified;

  public ConversationTitleView(Context context) {
    this(context, null);
  }

  public ConversationTitleView(Context context, AttributeSet attrs) {
    super(context, attrs);

  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();

    this.back     = ViewUtil.findById(this, R.id.up_button);
    this.content  = ViewUtil.findById(this, R.id.content);
    this.title    = ViewUtil.findById(this, R.id.title);
    this.subtitle = ViewUtil.findById(this, R.id.subtitle);
    this.verified = ViewUtil.findById(this, R.id.verified_indicator);
    this.avatar   = ViewUtil.findById(this, R.id.contact_photo_image);

    ViewUtil.setTextViewGravityStart(this.title, getContext());
    ViewUtil.setTextViewGravityStart(this.subtitle, getContext());
  }

  public void setTitle(@Nullable Recipient recipient) {
    if      (recipient == null) setComposeTitle();
    else                        setRecipientTitle(recipient);

    if (recipient != null && recipient.isBlocked()) {
      title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_block_white_18dp, 0, 0, 0);
    } else if (recipient != null && recipient.isMuted()) {
      title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_volume_off_white_18dp, 0, 0, 0);
    } else {
      title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
    }

    if (recipient != null) {
      this.avatar.setAvatar(recipient, false);
    }
  }

  public void setVerified(boolean verified) {
    this.verified.setVisibility(verified ? View.VISIBLE : View.GONE);
  }

  @Override
  public void setOnClickListener(@Nullable OnClickListener listener) {
    this.content.setOnClickListener(listener);
    this.avatar.setOnClickListener(listener);
  }

  public void setOnBackClickedListener(@Nullable OnClickListener listener) {
    this.back.setOnClickListener(listener);
  }

  private void setComposeTitle() {
    this.title.setText(R.string.ConversationActivity_compose_message);
    this.subtitle.setText(null);
    this.subtitle.setVisibility(View.GONE);
  }

  private void setRecipientTitle(Recipient recipient) {
    if      (recipient.isGroupRecipient())           setGroupRecipientTitle(recipient);
    else if (TextUtils.isEmpty(recipient.getName())) setNonContactRecipientTitle(recipient);
    else                                             setContactRecipientTitle(recipient);
  }

  private void setGroupRecipientTitle(Recipient recipient) {
    String localNumber = TextSecurePreferences.getLocalNumber(getContext());

    this.title.setText(recipient.getName());
    this.subtitle.setText(Stream.of(recipient.getParticipants())
                                .filter(r -> !r.getAddress().serialize().equals(localNumber))
                                .map(Recipient::toShortString)
                                .collect(Collectors.joining(", ")));

    this.subtitle.setVisibility(View.VISIBLE);
  }

  private void setNonContactRecipientTitle(Recipient recipient) {
    this.title.setText(recipient.getAddress().serialize());

    if (TextUtils.isEmpty(recipient.getProfileName())) {
      this.subtitle.setText(null);
      this.subtitle.setVisibility(View.GONE);
    } else {
      this.subtitle.setText("~" + recipient.getProfileName());
      this.subtitle.setVisibility(View.VISIBLE);
    }
  }

  private void setContactRecipientTitle(Recipient recipient) {
    this.title.setText(recipient.getName());

    if (recipient.getCustomLabel() != null) this.subtitle.setText(recipient.getCustomLabel());
    else                                    this.subtitle.setText(recipient.getAddress().serialize());

    this.subtitle.setVisibility(View.VISIBLE);
  }
}
