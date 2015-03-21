package org.thoughtcrime.securesms.jobs;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.jobs.requirements.MasterSecretRequirement;
import org.thoughtcrime.securesms.mms.ApnUnavailableException;
import org.thoughtcrime.securesms.mms.IncomingLollipopMmsConnection;
import org.thoughtcrime.securesms.mms.IncomingMediaMessage;
import org.thoughtcrime.securesms.mms.IncomingLegacyMmsConnection;
import org.thoughtcrime.securesms.mms.IncomingMmsConnection;
import org.thoughtcrime.securesms.mms.LegacyMmsConnection;
import org.thoughtcrime.securesms.mms.MmsRadio;
import org.thoughtcrime.securesms.mms.MmsRadioException;
import org.thoughtcrime.securesms.mms.OutgoingLegacyMmsConnection;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.protocol.WirePrefix;
import org.thoughtcrime.securesms.providers.MmsBodyProvider;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libaxolotl.DuplicateMessageException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.NoSessionException;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.internal.util.Hex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import ws.com.google.android.mms.InvalidHeaderValueException;
import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.NotificationInd;
import ws.com.google.android.mms.pdu.NotifyRespInd;
import ws.com.google.android.mms.pdu.PduComposer;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.PduParser;
import ws.com.google.android.mms.pdu.RetrieveConf;

import static org.thoughtcrime.securesms.mms.LegacyMmsConnection.Apn;

public class MmsDownloadJob extends MasterSecretJob {

  private static final String TAG = MmsDownloadJob.class.getSimpleName();

  private final long    messageId;
  private final long    threadId;
  private final boolean automatic;

  public MmsDownloadJob(Context context, long messageId, long threadId, boolean automatic) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withRequirement(new MasterSecretRequirement(context))
                                .withRequirement(new NetworkRequirement(context))
                                .withGroupId("mms-operation")
                                .withWakeLock(true, 30, TimeUnit.SECONDS)
                                .create());

    this.messageId = messageId;
    this.threadId  = threadId;
    this.automatic = automatic;
  }

  @Override
  public void onAdded() {
    if (automatic && KeyCachingService.getMasterSecret(context) == null) {
      DatabaseFactory.getMmsDatabase(context).markIncomingNotificationReceived(threadId);
      MessageNotifier.updateNotification(context, null);
    }
  }

  @Override
  public void onRun(MasterSecret masterSecret) {
    Log.w(TAG, "onRun()");

    MmsDatabase               database     = DatabaseFactory.getMmsDatabase(context);
    Optional<NotificationInd> notification = database.getNotification(messageId);

    if (!notification.isPresent()) {
      Log.w(TAG, "No notification for ID: " + messageId);
      return;
    }

    database.markDownloadState(messageId, MmsDatabase.Status.DOWNLOAD_CONNECTING);

    String contentLocation = new String(notification.get().getContentLocation());
    byte[] transactionId = notification.get().getTransactionId();

    Log.w(TAG, "Downloading mms at " + Uri.parse(contentLocation).getHost());

    download(masterSecret, contentLocation, transactionId);
  }

  private void download(MasterSecret masterSecret, String contentLocation, byte[] transactionId) {
    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    try {
      RetrieveConf retrieveConf = getMmsConnection(context, contentLocation, transactionId, messageId).retrieve();
      storeRetrievedMms(context, masterSecret, contentLocation, messageId, threadId, retrieveConf);
    } catch (ApnUnavailableException e) {
      Log.w(TAG, e);
      handleDownloadError(context, masterSecret, messageId, threadId, MmsDatabase.Status.DOWNLOAD_APN_UNAVAILABLE,
                          automatic);
    } catch (MmsException e) {
      Log.w(TAG, e);
      handleDownloadError(context, masterSecret, messageId, threadId,
                          MmsDatabase.Status.DOWNLOAD_HARD_FAILURE,
                          automatic);
    } catch (MmsRadioException | IOException e) {
      Log.w(TAG, e);
      handleDownloadError(context, masterSecret, messageId, threadId,
                          MmsDatabase.Status.DOWNLOAD_SOFT_FAILURE,
                          automatic);
    } catch (DuplicateMessageException e) {
      Log.w(TAG, e);
      database.markAsDecryptDuplicate(messageId, threadId);
    } catch (LegacyMessageException e) {
      Log.w(TAG, e);
      database.markAsLegacyVersion(messageId, threadId);
    } catch (NoSessionException e) {
      Log.w(TAG, e);
      database.markAsNoSession(messageId, threadId);
    } catch (InvalidMessageException e) {
      Log.w(TAG, e);
      database.markAsDecryptFailed(messageId, threadId);
    }
  }

  private IncomingMmsConnection getMmsConnection(Context context, String contentLocation, byte[] transactionId, long messageId)
      throws ApnUnavailableException
  {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      return new IncomingLollipopMmsConnection(context, contentLocation, messageId);
    } else {
      return new IncomingLegacyMmsConnection(context, getApn(context, contentLocation), transactionId);
    }
  }

  @Override
  public void onCanceled() {
    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    database.markDownloadState(messageId, MmsDatabase.Status.DOWNLOAD_SOFT_FAILURE);

    if (automatic) {
      database.markIncomingNotificationReceived(threadId);
      MessageNotifier.updateNotification(context, null, threadId);
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    return false;
  }

  private Apn getApn(Context context, String contentLocation) throws ApnUnavailableException {
    Apn dbApn = LegacyMmsConnection.getApn(context);
    return new Apn(contentLocation, dbApn.getProxy(), Integer.toString(dbApn.getPort()), dbApn.getUsername(), dbApn.getPassword());
  }

  private static void storeRetrievedMms(Context context, MasterSecret masterSecret, String contentLocation,
                                 long messageId, long threadId, RetrieveConf retrieved)
      throws MmsException, NoSessionException, DuplicateMessageException, InvalidMessageException,
             LegacyMessageException
  {
    MmsDatabase          database = DatabaseFactory.getMmsDatabase(context);
    IncomingMediaMessage message  = new IncomingMediaMessage(retrieved);

    Pair<Long, Long> messageAndThreadId;

    if (retrieved.getSubject() != null && WirePrefix.isEncryptedMmsSubject(retrieved.getSubject().getString())) {
      database.markAsLegacyVersion(messageId, threadId);
      messageAndThreadId = new Pair<>(messageId, threadId);
    } else {
      messageAndThreadId = database.insertMessageInbox(masterSecret, message,
                                                       contentLocation, threadId);
      database.delete(messageId);
    }

    MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
  }

  private static void handleDownloadError(Context context, MasterSecret masterSecret, long messageId, long threadId,
                                   int downloadStatus, boolean automatic)
  {
    MmsDatabase db = DatabaseFactory.getMmsDatabase(context);

    db.markDownloadState(messageId, downloadStatus);

    if (automatic) {
      db.markIncomingNotificationReceived(threadId);
      MessageNotifier.updateNotification(context, masterSecret, threadId);
    }
  }
}
