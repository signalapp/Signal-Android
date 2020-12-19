package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.TaskStackBuilder;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.CommunicationActions;

public class ShortcutLauncherActivity extends AppCompatActivity {

  private static final String KEY_RECIPIENT = "recipient_id";

  public static Intent createIntent(@NonNull Context context, @NonNull RecipientId recipientId) {
    Intent intent = new Intent(context, ShortcutLauncherActivity.class);
    intent.setAction(Intent.ACTION_MAIN);
    intent.putExtra(KEY_RECIPIENT, recipientId.serialize());

    return intent;
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    String rawId = getIntent().getStringExtra(KEY_RECIPIENT);

    if (rawId == null) {
      Toast.makeText(this, R.string.ShortcutLauncherActivity_invalid_shortcut, Toast.LENGTH_SHORT).show();
      // TODO [greyson] Navigation
      startActivity(MainActivity.clearTop(this));
      finish();
      return;
    }

    Recipient        recipient = Recipient.live(RecipientId.from(rawId)).get();
    // TODO [greyson] Navigation
    TaskStackBuilder backStack = TaskStackBuilder.create(this)
                                                 .addNextIntent(MainActivity.clearTop(this));

    CommunicationActions.startConversation(this, recipient, null, backStack);
    finish();
  }
}
