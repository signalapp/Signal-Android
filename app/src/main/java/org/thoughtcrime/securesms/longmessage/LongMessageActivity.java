package org.thoughtcrime.securesms.longmessage;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import org.session.libsession.utilities.Address;
import org.session.libsession.utilities.Util;
import org.session.libsession.utilities.recipients.Recipient;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.conversation.v2.messages.VisibleMessageContentView;

import network.loki.messenger.R;

public class LongMessageActivity extends PassphraseRequiredActionBarActivity {

  private static final String KEY_ADDRESS    = "address";
  private static final String KEY_MESSAGE_ID = "message_id";
  private static final String KEY_IS_MMS     = "is_mms";

  private static final int MAX_DISPLAY_LENGTH = 64 * 1024;

  private TextView textBody;

  private LongMessageViewModel viewModel;

  public static Intent getIntent(@NonNull Context context, @NonNull Address conversationAddress, long messageId, boolean isMms) {
    Intent intent = new Intent(context, LongMessageActivity.class);
    intent.putExtra(KEY_ADDRESS, conversationAddress.serialize());
    intent.putExtra(KEY_MESSAGE_ID, messageId);
    intent.putExtra(KEY_IS_MMS, isMms);
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);
    setContentView(R.layout.longmessage_activity);
    textBody = findViewById(R.id.longmessage_text);

    initViewModel(getIntent().getLongExtra(KEY_MESSAGE_ID, -1), getIntent().getBooleanExtra(KEY_IS_MMS, false));
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;
    }

    return false;
  }

  private void initViewModel(long messageId, boolean isMms) {
    viewModel = new ViewModelProvider(this, new LongMessageViewModel.Factory(getApplication(), new LongMessageRepository(this), messageId, isMms))
                                  .get(LongMessageViewModel.class);

    viewModel.getMessage().observe(this, message -> {
      if (message == null) return;

      if (!message.isPresent()) {
        Toast.makeText(this, R.string.LongMessageActivity_unable_to_find_message, Toast.LENGTH_SHORT).show();
        finish();
        return;
      }

      if (message.get().getMessageRecord().isOutgoing()) {
        getSupportActionBar().setTitle(getString(R.string.LongMessageActivity_your_message));
      } else {
        Recipient recipient = message.get().getMessageRecord().getRecipient();
        String    name      = Util.getFirstNonEmpty(recipient.getName(), recipient.getProfileName(), recipient.getAddress().serialize());
        getSupportActionBar().setTitle(getString(R.string.LongMessageActivity_message_from_s, name));
      }
      Spannable bodySpans = VisibleMessageContentView.Companion.getBodySpans(this, message.get().getMessageRecord(), null);
      textBody.setText(bodySpans);
      textBody.setMovementMethod(LinkMovementMethod.getInstance());
    });
  }

}
