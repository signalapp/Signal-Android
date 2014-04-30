package org.whispersystems.textsecure.storage;

import android.content.Context;

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
