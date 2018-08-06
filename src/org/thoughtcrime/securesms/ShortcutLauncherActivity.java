package org.thoughtcrime.securesms;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.AppCompatActivity;

import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.CommunicationActions;

public class ShortcutLauncherActivity extends AppCompatActivity {

  private static final String KEY_SERIALIZED_ADDRESS = "serialized_address";

  public static Intent createIntent(@NonNull Context context, @NonNull Address address) {
    Intent intent = new Intent(context, ShortcutLauncherActivity.class);
    intent.setAction(Intent.ACTION_MAIN);
    intent.putExtra(KEY_SERIALIZED_ADDRESS, address.serialize());

    return intent;
  }

  @SuppressLint("StaticFieldLeak")
  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    String           serializedAddress = getIntent().getStringExtra(KEY_SERIALIZED_ADDRESS);
    Address          address           = Address.fromSerialized(serializedAddress);
    Recipient        recipient         = Recipient.from(this, address, true);
    TaskStackBuilder backStack         = TaskStackBuilder.create(this)
                                                         .addNextIntent(new Intent(this, ConversationListActivity.class));

    CommunicationActions.startConversation(this, recipient, null, backStack);
    finish();
  }
}
