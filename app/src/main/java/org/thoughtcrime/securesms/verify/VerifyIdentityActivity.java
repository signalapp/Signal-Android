package org.thoughtcrime.securesms.verify;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;

import org.signal.libsignal.protocol.IdentityKey;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable;
import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;

/**
 * Activity for verifying identity keys.
 *
 * @author Moxie Marlinspike
 */
public class VerifyIdentityActivity extends PassphraseRequiredActivity {

  private static final String RECIPIENT_EXTRA = "recipient_id";
  private static final String IDENTITY_EXTRA  = "recipient_identity";
  private static final String VERIFIED_EXTRA  = "verified_state";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  public static Intent newIntent(@NonNull Context context,
                                 @NonNull IdentityRecord identityRecord)
  {
    return newIntent(context,
                     identityRecord.getRecipientId(),
                     identityRecord.getIdentityKey(),
                     identityRecord.getVerifiedStatus() == IdentityTable.VerifiedStatus.VERIFIED);
  }

  public static Intent newIntent(@NonNull Context context,
                                 @NonNull IdentityRecord identityRecord,
                                 boolean verified)
  {
    return newIntent(context,
                     identityRecord.getRecipientId(),
                     identityRecord.getIdentityKey(),
                     verified);
  }

  public static Intent newIntent(@NonNull Context context,
                                 @NonNull RecipientId recipientId,
                                 @NonNull IdentityKey identityKey,
                                 boolean verified)
  {
    Intent intent = new Intent(context, VerifyIdentityActivity.class);

    intent.putExtra(RECIPIENT_EXTRA, recipientId);
    intent.putExtra(IDENTITY_EXTRA, new IdentityKeyParcelable(identityKey));
    intent.putExtra(VERIFIED_EXTRA, verified);

    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);
    dynamicTheme.onCreate(this);

    VerifyIdentityFragment fragment = VerifyIdentityFragment.create(
        getIntent().getParcelableExtra(RECIPIENT_EXTRA),
        getIntent().getParcelableExtra(IDENTITY_EXTRA),
        getIntent().getBooleanExtra(VERIFIED_EXTRA, false)
    );

    getSupportFragmentManager().beginTransaction()
                               .replace(android.R.id.content, fragment)
                               .commitAllowingStateLoss();
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }
}
