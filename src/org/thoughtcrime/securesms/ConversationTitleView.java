package org.thoughtcrime.securesms;

import android.content.Context;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

public class ConversationTitleView extends LinearLayout {

  private static final String TAG = ConversationTitleView.class.getSimpleName();

  private TextView  title;
  private TextView  subtitle;
  private ImageView muteIcon;
  private ImageView blockedIcon;

  public ConversationTitleView(Context context) {
    this(context, null);
  }

  public ConversationTitleView(Context context, AttributeSet attrs) {
    super(context, attrs);

  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();

    this.title       = (TextView) findViewById(R.id.title);
    this.subtitle    = (TextView) findViewById(R.id.subtitle);
    this.muteIcon    = (ImageView) findViewById(R.id.muted);
    this.blockedIcon = (ImageView) findViewById(R.id.blocked);
  }


  public void setTitle(@Nullable Recipients recipients) {
    if      (recipients == null)             setComposeTitle();
    else if (recipients.isSingleRecipient()) setRecipientTitle(recipients.getPrimaryRecipient());
    else                                     setRecipientsTitle(recipients);

    if (recipients != null && recipients.isBlocked()) {
      this.blockedIcon.setVisibility(View.VISIBLE);
      this.muteIcon.setVisibility(View.GONE);
    } else if (recipients != null && recipients.isMuted()) {
      this.muteIcon.setVisibility(View.VISIBLE);
      this.blockedIcon.setVisibility(View.GONE);
    } else {
      this.muteIcon.setVisibility(View.GONE);
      this.blockedIcon.setVisibility(View.GONE);
    }
  }

  private void setComposeTitle() {
    this.title.setText(R.string.ConversationActivity_compose_message);
    this.subtitle.setText(null);
  }

  private void setRecipientTitle(Recipient recipient) {
    if (!recipient.isGroupRecipient()) {
      if (TextUtils.isEmpty(recipient.getName())) {
        this.title.setText(recipient.getNumber());
        this.subtitle.setText(null);
      } else {
        this.title.setText(recipient.getName());
        this.subtitle.setText(recipient.getNumber());
      }
    } else {
      String groupName = (!TextUtils.isEmpty(recipient.getName())) ?
                         recipient.getName() :
                         getContext().getString(R.string.ConversationActivity_unnamed_group);

      this.title.setText(groupName);
      this.subtitle.setText(null);
    }
  }

  private void setRecipientsTitle(Recipients recipients) {
    int size = recipients.getRecipientsList().size();

    title.setText(getContext().getString(R.string.ConversationActivity_group_conversation));
    subtitle.setText((size == 1) ? getContext().getString(R.string.ConversationActivity_d_recipients_in_group_singular) :
                         String.format(getContext().getString(R.string.ConversationActivity_d_recipients_in_group), size));
  }





}
