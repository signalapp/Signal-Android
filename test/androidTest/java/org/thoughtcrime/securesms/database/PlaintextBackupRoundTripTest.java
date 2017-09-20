package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.TextSecureTestCase;
import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test that messages correctly make it through an export/import cycle.
 * Should be run on a device with no existing backups and no messages to UNKNOWN,
 * but where Signal has been opened at least once to initialize the master secret.
 *
 * @author Aneesh Agrawal
 */
public class PlaintextBackupRoundTripTest extends TextSecureTestCase {
  private static final String TAG = PlaintextBackupRoundTripTest.class.getSimpleName();

  private Context context;
  private Recipient recipient;
  private SmsDatabase smsDatabase;
  private long threadId;
  private MasterSecret masterSecret;

  private final List<File> exportedFiles = new ArrayList<>();
  private final Set<Long> insertedMessageIds = new HashSet<>();

  private void ensureContextIsInitialized() {
    ((ApplicationContext)context.getApplicationContext()).onCreate();
  }

  private List<String> performSafetyChecksToAvoidAnyDataLoss() {
    final List<String> messages = new ArrayList<>();

    try {
      PlaintextBackupImporter.getPlaintextBackupFile();
      messages.add("Existing backup(s) found.");
    } catch (FileNotFoundException e) {
    } catch (NoExternalStorageException e) {
      messages.add("External storage is unavailable.");
    }

    threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);
    int numExistingMessages = smsDatabase.getMessageCountForThread(threadId);
    if (numExistingMessages != 0) {
      final String address = recipient.getAddress().toString();
      messages.add(numExistingMessages + " existing messages found to " + address + ".");
    }

    try {
      masterSecret = MasterSecretUtil.getMasterSecret(context, MasterSecretUtil.UNENCRYPTED_PASSPHRASE);
    } catch (InvalidPassphraseException e) {
      messages.add("Existing passphrase found.");
    }

    return messages;
  }

  @Override
  public void setUp() {
    super.setUp();

    context = getInstrumentation().getTargetContext();
    ensureContextIsInitialized();
    recipient = mock(Recipient.class);
    when(recipient.getAddress()).thenReturn(Address.UNKNOWN);
    smsDatabase = DatabaseFactory.getSmsDatabase(context);

    final List<String> messages = performSafetyChecksToAvoidAnyDataLoss();
    if (!(messages.isEmpty())) {
      String combined = TextUtils.join("\n", messages);
      throw new RuntimeException("Aborting " + TAG + " test for safety reasons:\n" + combined);
    }

    exportedFiles.clear();
    insertedMessageIds.clear();
  }

  @Override
  public void tearDown() {
    for (long messageId: insertedMessageIds) {
      smsDatabase.deleteMessage(messageId);
    }
    insertedMessageIds.clear();

    for (File backup: exportedFiles) {
      Log.d(TAG, "Removing backup file " + backup.getAbsolutePath() + " exported during test.");
      backup.delete();
    }
    exportedFiles.clear();

    masterSecret = null;
    threadId = -1L;
    smsDatabase = null;
    recipient = null;
    context = null;
  }

  public void testPlaintextBackupRoundTripNoMessages() throws Exception {
    do_testPlaintextBackupRoundTrip(0);
  }
  public void testPlaintextBackupRoundTripOneMessage() throws Exception {
    do_testPlaintextBackupRoundTrip(1);
  }
  public void testPlaintextBackupRoundTripMultipleMessages() throws Exception {
    do_testPlaintextBackupRoundTrip(10);
  }

  public void do_testPlaintextBackupRoundTrip(final int numMessages) throws Exception {
    exportedFiles.add(PlaintextBackupExporter.exportPlaintextToSd(context, masterSecret));

    for (int i = 0; i < numMessages; i++) {
      insertedMessageIds.add(smsDatabase.insertMessageOutbox(
        threadId,
        new OutgoingTextMessage(recipient, "Outgoing message " + i, -1),
        MmsSmsColumns.Types.BASE_SENDING_TYPE,
        false,
        System.currentTimeMillis(),
        null
      ));
      exportedFiles.add(PlaintextBackupExporter.exportPlaintextToSd(context, masterSecret));
    }
    assertEquals(
      "Did not insert the right number of messages.",
      numMessages,
      insertedMessageIds.size()
    );
    final Set<Long> allInsertedMessageIds = Collections.unmodifiableSet(new HashSet<>(insertedMessageIds));

    for (long messageId: allInsertedMessageIds) {
      smsDatabase.deleteMessage(messageId);
      insertedMessageIds.remove(messageId);
    }
    assertEquals(
      "Could not delete all inserted messages.",
      0,
      smsDatabase.getMessageCountForThread(threadId)
    );
    assertEquals(
      "Could not delete all inserted messages.",
      0,
      insertedMessageIds.size()
    );

    PlaintextBackupImporter.importPlaintextFromSd(context, masterSecret);
    assertEquals(
      "Did not import all messages.",
      numMessages,
      smsDatabase.getMessageCountForThread(threadId)
    );
    insertedMessageIds.addAll(allInsertedMessageIds);
  }
}
