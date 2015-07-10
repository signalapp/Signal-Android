/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.sms;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.NotInDirectoryException;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.jobs.MmsSendJob;
import org.thoughtcrime.securesms.jobs.PushGroupSendJob;
import org.thoughtcrime.securesms.jobs.PushMediaSendJob;
import org.thoughtcrime.securesms.jobs.PushProfileSendJob;
import org.thoughtcrime.securesms.jobs.PushTextSendJob;
import org.thoughtcrime.securesms.jobs.SmsSendJob;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.push.TextSecureCommunicationFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureAccountManager;
import org.whispersystems.textsecure.api.push.ContactTokenDetails;
import org.thoughtcrime.securesms.util.DirectoryUtil;
import org.whispersystems.textsecure.api.util.InvalidNumberException;

import java.io.IOException;
import ws.com.google.android.mms.MmsException;

public class MessageSender {

  private static final String TAG = MessageSender.class.getSimpleName();

  public static long send(final Context context,
                          final MasterSecret masterSecret,
                          final OutgoingTextMessage message,
                          final long threadId,
                          final boolean forceSms)
  {
    EncryptingSmsDatabase database    = DatabaseFactory.getEncryptingSmsDatabase(context);
    Recipients            recipients  = message.getRecipients();
    boolean               keyExchange = message.isKeyExchange();

    long allocatedThreadId;

    if (threadId == -1) {
      allocatedThreadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
    } else {
      allocatedThreadId = threadId;
    }

    long messageId = database.insertMessageOutbox(masterSecret, allocatedThreadId, message, forceSms, System.currentTimeMillis());

    sendTextMessage(context, recipients, forceSms, keyExchange, messageId);

    return allocatedThreadId;
  }

  public static long send(final Context context,
                          final MasterSecret masterSecret,
                          final OutgoingMediaMessage message,
                          final long threadId,
                          final boolean forceSms)
  {
    try {
      ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
      MmsDatabase    database       = DatabaseFactory.getMmsDatabase(context);

      long allocatedThreadId;

      if (threadId == -1) {
        allocatedThreadId = threadDatabase.getThreadIdFor(message.getRecipients(), message.getDistributionType());
      } else {
        allocatedThreadId = threadId;
      }

      Recipients recipients = message.getRecipients();
      long       messageId  = database.insertMessageOutbox(masterSecret, message, allocatedThreadId, forceSms, System.currentTimeMillis());

      sendMediaMessage(context, masterSecret, recipients, forceSms, messageId, message.isProfileUpdate());

      return allocatedThreadId;
    } catch (MmsException e) {
      Log.w(TAG, e);
      return threadId;
    }
  }

  public static void resend(Context context, MasterSecret masterSecret, MessageRecord messageRecord) {
    try {
      Recipients recipients  = messageRecord.getRecipients();
      long       messageId   = messageRecord.getId();
      boolean    forceSms    = messageRecord.isForcedSms();
      boolean    keyExchange = messageRecord.isKeyExchange();

      if (messageRecord.isMms()) {
        sendMediaMessage(context, masterSecret, recipients, forceSms, messageId, messageRecord.isUpdateProfile());
      } else {
        sendTextMessage(context, recipients, forceSms, keyExchange, messageId);
      }
    } catch (MmsException e) {
      Log.w(TAG, e);
    }
  }

  private static void sendMediaMessage(Context context, MasterSecret masterSecret,
                                       Recipients recipients, boolean forceSms, long messageId, boolean profileUpdate)
      throws MmsException
  {
    if(!profileUpdate) {
      if (!forceSms && isSelfSend(context, recipients)) {
        sendMediaSelf(context, masterSecret, messageId);
      } else if (isGroupPushSend(recipients)) {
        sendGroupPush(context, recipients, messageId);
      } else if (!forceSms && isPushMediaSend(context, recipients)) {
        sendMediaPush(context, recipients, messageId);
      } else {
        sendMms(context, messageId);
      }
    } else {
        sendProfileUpdate(context, recipients, messageId);
      }
  }

  private static void sendTextMessage(Context context, Recipients recipients,
                                      boolean forceSms, boolean keyExchange, long messageId)
  {
    if (!forceSms && isSelfSend(context, recipients)) {
      sendTextSelf(context, messageId);
    } else if (!forceSms && isPushTextSend(context, recipients, keyExchange)) {
      sendTextPush(context, recipients, messageId);
    } else {
      sendSms(context, recipients, messageId);
    }
  }

  private static void sendTextSelf(Context context, long messageId) {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);

    database.markAsSent(messageId);
    database.markAsPush(messageId);

    Pair<Long, Long> messageAndThreadId = database.copyMessageInbox(messageId);
    database.markAsPush(messageAndThreadId.first);
  }

  private static void sendMediaSelf(Context context, MasterSecret masterSecret, long messageId)
      throws MmsException
  {
    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    database.markAsSent(messageId, "self-send".getBytes(), 0);
    database.markAsPush(messageId);

    long newMessageId = database.copyMessageInbox(masterSecret, messageId);
    database.markAsPush(newMessageId);
  }

  private static void sendTextPush(Context context, Recipients recipients, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new PushTextSendJob(context, messageId, recipients.getPrimaryRecipient().getNumber()));
  }

  private static void sendMediaPush(Context context, Recipients recipients, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new PushMediaSendJob(context, messageId, recipients.getPrimaryRecipient().getNumber()));
  }
  public static void sendProfileUpdate(Context context, Recipients recipients, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new PushProfileSendJob(context, messageId, recipients.getPrimaryRecipient().getNumber()));
  }
  private static void sendGroupPush(Context context, Recipients recipients, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new PushGroupSendJob(context, messageId, recipients.getPrimaryRecipient().getNumber()));
  }

  private static void sendSms(Context context, Recipients recipients, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new SmsSendJob(context, messageId, recipients.getPrimaryRecipient().getName()));
  }

  private static void sendMms(Context context, long messageId) {
    Log.d("MYLOG", "sendMMS");
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new MmsSendJob(context, messageId));
  }

  private static boolean isPushTextSend(Context context, Recipients recipients, boolean keyExchange) {
    try {
      if (!TextSecurePreferences.isPushRegistered(context)) {
        return false;
      }

      if (keyExchange) {
        return false;
      }

      Recipient recipient   = recipients.getPrimaryRecipient();
      String    destination = Util.canonicalizeNumber(context, recipient.getNumber());

      return isPushDestination(context, destination);
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      return false;
    }
  }

  private static boolean isPushMediaSend(Context context, Recipients recipients) {
    try {
      if (!TextSecurePreferences.isPushRegistered(context)) {
        return false;
      }

      if (recipients.getRecipientsList().size() > 1) {
        return false;
      }

      Recipient recipient   = recipients.getPrimaryRecipient();
      String    destination = Util.canonicalizeNumber(context, recipient.getNumber());

      return isPushDestination(context, destination);
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      return false;
    }
  }

  private static boolean isGroupPushSend(Recipients recipients) {
    return GroupUtil.isEncodedGroup(recipients.getPrimaryRecipient().getNumber());
  }

  private static boolean isSelfSend(Context context, Recipients recipients) {
    try {
      if (!TextSecurePreferences.isPushRegistered(context)) {
        return false;
      }

      if (!recipients.isSingleRecipient()) {
        return false;
      }

      if (recipients.isGroupRecipient()) {
        return false;
      }

      String e164number = Util.canonicalizeNumber(context, recipients.getPrimaryRecipient().getNumber());
      return TextSecurePreferences.getLocalNumber(context).equals(e164number);
    } catch (InvalidNumberException e) {
      Log.w("MessageSender", e);
      return false;
    }
  }

  private static boolean isPushDestination(Context context, String destination) {
    TextSecureDirectory directory = TextSecureDirectory.getInstance(context);

    try {
      return directory.isActiveNumber(destination);
    } catch (NotInDirectoryException e) {
      try {
        TextSecureAccountManager      accountManager = TextSecureCommunicationFactory.createManager(context);
        String                        contactToken   = DirectoryUtil.getDirectoryServerToken(destination);
        Optional<ContactTokenDetails> registeredUser = accountManager.getContact(contactToken);

        if (!registeredUser.isPresent()) {
          registeredUser = Optional.of(new ContactTokenDetails());
          registeredUser.get().setNumber(destination);
          directory.setNumber(registeredUser.get(), false);
          return false;
        } else {
          registeredUser.get().setNumber(destination);
          directory.setNumber(registeredUser.get(), true);
          return true;
        }
      } catch (IOException e1) {
        Log.w(TAG, e1);
        return false;
      }
    }
  }

}
