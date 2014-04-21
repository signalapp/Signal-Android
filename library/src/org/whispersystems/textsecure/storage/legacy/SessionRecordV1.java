package org.whispersystems.textsecure.storage.legacy;

import android.content.Context;

import org.whispersystems.textsecure.storage.CanonicalRecipient;
import org.whispersystems.textsecure.storage.Record;

/**
 * A disk record representing a current session.
 *
 * @author Moxie Marlinspike
 */

public class SessionRecordV1 {
  public static void delete(Context context, CanonicalRecipient recipient) {
    Record.delete(context, Record.SESSIONS_DIRECTORY, recipient.getRecipientId() + "");
  }
}
