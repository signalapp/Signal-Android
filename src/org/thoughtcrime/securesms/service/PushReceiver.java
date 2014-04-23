package org.thoughtcrime.securesms.service;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.Pair;

import com.google.protobuf.InvalidProtocolBufferException;

import org.thoughtcrime.securesms.crypto.DecryptingQueue;
import org.thoughtcrime.securesms.crypto.KeyExchangeProcessor;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.mms.IncomingMediaMessage;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.IncomingEncryptedMessage;
import org.thoughtcrime.securesms.sms.IncomingEndSessionMessage;
import org.thoughtcrime.securesms.sms.IncomingKeyExchangeMessage;
import org.thoughtcrime.securesms.sms.IncomingPreKeyBundleMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.push.IncomingPushMessage;
import org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.textsecure.storage.RecipientDevice;
import org.whispersystems.textsecure.storage.TextSecureSessionStore;
import org.whispersystems.textsecure.util.Base64;

import ws.com.google.android.mms.MmsException;

import static org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent.GroupContext.Type;

public class PushReceiver {

  public static final int RESULT_OK                = 0;
  public static final int RESULT_NO_SESSION        = 1;
  public static final int RESULT_DECRYPT_FAILED    = 2;
  public static final int RESULT_DECRYPT_DUPLICATE = 3;

  private final Context       context;
  private final GroupReceiver groupReceiver;

  public PushReceiver(Context context) {
    this.context       = context.getApplicationContext();
    this.groupReceiver = new GroupReceiver(context);
  }

  public void process(MasterSecret masterSecret, Intent intent) {
    if (SendReceiveService.RECEIVE_PUSH_ACTION.equals(intent.getAction())) {
      handleMessage(masterSecret, intent);
    } else if (SendReceiveService.DECRYPTED_PUSH_ACTION.equals(intent.getAction())) {
      handleDecrypt(masterSecret, intent);
    }
  }

  private void handleDecrypt(MasterSecret masterSecret, Intent intent) {
    IncomingPushMessage message   = intent.getParcelableExtra("message");
    long                messageId = intent.getLongExtra("message_id", -1);
    int                 result    = intent.getIntExtra("result", 0);

    if      (result == RESULT_OK)                handleReceivedMessage(masterSecret, message, true);
    else if (result == RESULT_NO_SESSION)        handleReceivedMessageForNoSession(masterSecret, message);
    else if (result == RESULT_DECRYPT_FAILED)    handleReceivedCorruptedMessage(masterSecret, message, true);
    else if (result == RESULT_DECRYPT_DUPLICATE) handleReceivedDuplicateMessage(message);

    DatabaseFactory.getPushDatabase(context).delete(messageId);
  }

  private void handleMessage(MasterSecret masterSecret, Intent intent) {
    if (intent.getExtras() == null) {
      return;
    }

    IncomingPushMessage message = intent.getExtras().getParcelable("message");

    if (message == null) {
      return;
    }

    if      (message.isSecureMessage()) handleReceivedSecureMessage(masterSecret, message);
    else if (message.isPreKeyBundle())  handleReceivedPreKeyBundle(masterSecret, message);
    else                                handleReceivedMessage(masterSecret, message, false);
  }

  private void handleReceivedSecureMessage(MasterSecret masterSecret, IncomingPushMessage message) {
    long id = DatabaseFactory.getPushDatabase(context).insert(message);

    if (masterSecret != null) {
      DecryptingQueue.scheduleDecryption(context, masterSecret, id, message);
    } else {
      Recipients recipients = RecipientFactory.getRecipientsFromMessage(context, message, false);
      long       threadId   = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
      MessageNotifier.updateNotification(context, null, threadId);
    }
  }

  private void handleReceivedPreKeyBundle(MasterSecret masterSecret, IncomingPushMessage message) {
    if (masterSecret == null) {
      handleReceivedSecureMessage(null, message);
      return;
    }

    try {
      Recipient              recipient       = RecipientFactory.getRecipientsFromString(context, message.getSource(), false).getPrimaryRecipient();
      RecipientDevice        recipientDevice = new RecipientDevice(recipient.getRecipientId(), message.getSourceDevice());
      KeyExchangeProcessor processor       = new KeyExchangeProcessor(context, masterSecret, recipientDevice);
      PreKeyWhisperMessage   preKeyExchange  = new PreKeyWhisperMessage(message.getBody());

      if (processor.isTrusted(preKeyExchange)) {
        processor.processKeyExchangeMessage(preKeyExchange);

        IncomingPushMessage bundledMessage = message.withBody(preKeyExchange.getWhisperMessage().serialize());
        handleReceivedSecureMessage(masterSecret, bundledMessage);
      } else {
        String                      encoded            = Base64.encodeBytes(message.getBody());
        IncomingTextMessage         textMessage        = new IncomingTextMessage(message, encoded, null);
        IncomingPreKeyBundleMessage bundleMessage      = new IncomingPreKeyBundleMessage(textMessage, encoded);
        EncryptingSmsDatabase       database           = DatabaseFactory.getEncryptingSmsDatabase(context);
        Pair<Long, Long>            messageAndThreadId = database.insertMessageInbox(masterSecret, bundleMessage);

        MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
      }
    } catch (InvalidVersionException e) {
      Log.w("PushReceiver", e);
      handleReceivedCorruptedKey(masterSecret, message, true);
    } catch (InvalidKeyException | InvalidKeyIdException | InvalidMessageException |
             RecipientFormattingException e)
    {
      Log.w("PushReceiver", e);
      handleReceivedCorruptedKey(masterSecret, message, false);
    }
  }

  private void handleReceivedMessage(MasterSecret masterSecret,
                                     IncomingPushMessage message,
                                     boolean secure)
  {
    try {
      PushMessageContent messageContent = PushMessageContent.parseFrom(message.getBody());

      if (secure && (messageContent.getFlags() & PushMessageContent.Flags.END_SESSION_VALUE) != 0) {
        Log.w("PushReceiver", "Received end session message...");
        handleEndSessionMessage(masterSecret, message, messageContent);
      } else if (messageContent.hasGroup() && messageContent.getGroup().getType().getNumber() != Type.DELIVER_VALUE) {
        Log.w("PushReceiver", "Received push group message...");
        groupReceiver.process(masterSecret, message, messageContent, secure);
      } else if (messageContent.getAttachmentsCount() > 0) {
        Log.w("PushReceiver", "Received push media message...");
        handleReceivedMediaMessage(masterSecret, message, messageContent, secure);
      } else {
        Log.w("PushReceiver", "Received push text message...");
        handleReceivedTextMessage(masterSecret, message, messageContent, secure);
      }
    } catch (InvalidProtocolBufferException e) {
      Log.w("PushReceiver", e);
      handleReceivedCorruptedMessage(masterSecret, message, secure);
    }
  }

  private void handleEndSessionMessage(MasterSecret masterSecret,
                                       IncomingPushMessage message,
                                       PushMessageContent messageContent)
  {
    try {
      Recipient                 recipient                 = RecipientFactory.getRecipientsFromString(context, message.getSource(), true).getPrimaryRecipient();
      IncomingTextMessage       incomingTextMessage       = new IncomingTextMessage(message, "", null);
      IncomingEndSessionMessage incomingEndSessionMessage = new IncomingEndSessionMessage(incomingTextMessage);

      EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);

      Pair<Long, Long> messageAndThreadId = database.insertMessageInbox(masterSecret, incomingEndSessionMessage);
      database.updateMessageBody(masterSecret, messageAndThreadId.first, messageContent.getBody());

      SessionStore sessionStore = new TextSecureSessionStore(context, masterSecret);
      sessionStore.deleteAll(recipient.getRecipientId());

      KeyExchangeProcessor.broadcastSecurityUpdateEvent(context, messageAndThreadId.second);
    } catch (RecipientFormattingException e) {
      Log.w("PushReceiver", e);
    }
  }

  private void handleReceivedMediaMessage(MasterSecret masterSecret,
                                          IncomingPushMessage message,
                                          PushMessageContent messageContent,
                                          boolean secure)
  {

    try {
      String               localNumber  = TextSecurePreferences.getLocalNumber(context);
      MmsDatabase          database     = DatabaseFactory.getMmsDatabase(context);
      IncomingMediaMessage mediaMessage = new IncomingMediaMessage(masterSecret, localNumber,
                                                                   message, messageContent);

      Pair<Long, Long> messageAndThreadId;

      if (secure) {
        messageAndThreadId = database.insertSecureDecryptedMessageInbox(masterSecret, mediaMessage, -1);
      } else {
        messageAndThreadId = database.insertMessageInbox(masterSecret, mediaMessage, null, -1);
      }

      Intent intent = new Intent(context, SendReceiveService.class);
      intent.setAction(SendReceiveService.DOWNLOAD_PUSH_ACTION);
      intent.putExtra("message_id", messageAndThreadId.first);
      context.startService(intent);

      MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
    } catch (MmsException e) {
      Log.w("PushReceiver", e);
      // XXX
    }
  }

  private void handleReceivedTextMessage(MasterSecret masterSecret,
                                         IncomingPushMessage message,
                                         PushMessageContent messageContent,
                                         boolean secure)
  {
    EncryptingSmsDatabase database    = DatabaseFactory.getEncryptingSmsDatabase(context);
    IncomingTextMessage   textMessage = new IncomingTextMessage(message, "",
                                                                messageContent.hasGroup() ?
                                                                    messageContent.getGroup() : null);

    if (secure) {
      textMessage = new IncomingEncryptedMessage(textMessage, "");
    }

    Pair<Long, Long> messageAndThreadId = database.insertMessageInbox(masterSecret, textMessage);
    database.updateMessageBody(masterSecret, messageAndThreadId.first, messageContent.getBody());

    MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
  }

  private void handleReceivedCorruptedMessage(MasterSecret masterSecret,
                                              IncomingPushMessage message,
                                              boolean secure)
  {
    Pair<Long, Long> messageAndThreadId = insertMessagePlaceholder(masterSecret, message, secure);
    DatabaseFactory.getEncryptingSmsDatabase(context).markAsDecryptFailed(messageAndThreadId.first);

    MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
  }

  private void handleReceivedDuplicateMessage(IncomingPushMessage message) {
    Log.w("PushReceiver", "Received duplicate message: " + message.getSource() + " , " + message.getSourceDevice());
  }

  private void handleReceivedCorruptedKey(MasterSecret masterSecret,
                                          IncomingPushMessage message,
                                          boolean invalidVersion)
  {
    IncomingTextMessage        corruptedMessage    = new IncomingTextMessage(message, "", null);
    IncomingKeyExchangeMessage corruptedKeyMessage = new IncomingKeyExchangeMessage(corruptedMessage, "");

    if (!invalidVersion) corruptedKeyMessage.setCorrupted(true);
    else                 corruptedKeyMessage.setInvalidVersion(true);

    Pair<Long, Long> messageAndThreadId = DatabaseFactory.getEncryptingSmsDatabase(context)
                                                         .insertMessageInbox(masterSecret,
                                                                             corruptedKeyMessage);

    MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
  }

  private void handleReceivedMessageForNoSession(MasterSecret masterSecret,
                                                 IncomingPushMessage message)
  {
    Pair<Long, Long> messageAndThreadId = insertMessagePlaceholder(masterSecret, message, true);
    DatabaseFactory.getEncryptingSmsDatabase(context).markAsNoSession(messageAndThreadId.first);
    MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
  }

  private Pair<Long, Long> insertMessagePlaceholder(MasterSecret masterSecret,
                                                    IncomingPushMessage message,
                                                    boolean secure)
  {
    IncomingTextMessage placeholder = new IncomingTextMessage(message, "", null);

    if (secure) {
      placeholder = new IncomingEncryptedMessage(placeholder, "");
    }

    return DatabaseFactory.getEncryptingSmsDatabase(context)
                          .insertMessageInbox(masterSecret, placeholder);
  }
}
