package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Util;

public class ConversationUpdateItem extends LinearLayout
    implements Recipients.RecipientsModifiedListener, Recipient.RecipientModifiedListener, View.OnClickListener
{
  private static final String TAG = ConversationUpdateItem.class.getSimpleName();

  private ImageView     icon;
  private TextView      body;
  private Recipient     sender;
  private MessageRecord messageRecord;

  public ConversationUpdateItem(Context context) {
    super(context);
  }

  public ConversationUpdateItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();

    this.icon = (ImageView)findViewById(R.id.conversation_update_icon);
    this.body = (TextView)findViewById(R.id.conversation_update_body);

    setOnClickListener(this);
  }

  public void set(MessageRecord messageRecord) {
    this.messageRecord = messageRecord;
    this.sender        = messageRecord.getIndividualRecipient();

    this.sender.addListener(this);

    if (messageRecord.isGroupAction()) {
      icon.setImageDrawable(getContext().getResources().getDrawable(R.drawable.ic_group_grey600_24dp));

      if (messageRecord.isGroupQuit() && messageRecord.isOutgoing()) {
        body.setText(R.string.MessageRecord_left_group);
      } else if (messageRecord.isGroupQuit()) {
        body.setText(getContext().getString(R.string.ConversationItem_group_action_left, sender.toShortString()));
      } else {
        GroupUtil.GroupDescription description = GroupUtil.getDescription(getContext(), messageRecord.getBody().getBody());
        description.addListener(this);
        body.setText(description.toString());
      }
    }
  }

  @Override
  public void onModified(Recipients recipient) {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        set(messageRecord);
      }
    });
  }

  @Override
  public void onModified(Recipient recipient) {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        set(messageRecord);
      }
    });
  }

  @Override
  public void onClick(View v) {
    if (messageRecord.isIdentityUpdate()) {
      Intent intent = new Intent(getContext(), RecipientPreferenceActivity.class);
      intent.putExtra(RecipientPreferenceActivity.RECIPIENTS_EXTRA,
                      new long[] {messageRecord.getIndividualRecipient().getRecipientId()});

      getContext().startActivity(intent);
    }
  }
}
