package org.thoughtcrime.securesms.service;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.Pair;

import com.google.protobuf.InvalidProtocolBufferException;

import org.thoughtcrime.securesms.crypto.DecryptingQueue;
import org.thoughtcrime.securesms.crypto.KeyExchangeProcessorV2;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.mms.IncomingMediaMessage;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.IncomingEncryptedMessage;
import org.thoughtcrime.securesms.sms.IncomingKeyExchangeMessage;
import org.thoughtcrime.securesms.sms.IncomingPreKeyBundleMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.SmsTransportDetails;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.InvalidVersionException;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.protocol.PreKeyWhisperMessage;
import org.whispersystems.textsecure.push.IncomingPushMessage;
import org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent;
import org.whispersystems.textsecure.storage.InvalidKeyIdException;
import org.whispersystems.textsecure.storage.RecipientDevice;

import ws.com.google.android.mms.MmsException;

import static org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent.GroupContext;
import static org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent.GroupContext.Type;

public class PushReceiver {

  public static final int RESULT_OK             = 0;
  public static final int RESULT_NO_SESSION     = 1;
  public static final int RESULT_DECRYPT_FAILED = 2;

  private final Context context;

  public PushReceiver(Context context) {
    this.context = context.getApplicationContext();
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

    if      (result == RESULT_OK)             handleReceivedMessage(masterSecret, message, true);
    else if (result == RESULT_NO_SESSION)     handleReceivedMessageForNoSession(masterSecret, message);
    else if (result == RESULT_DECRYPT_FAILED) handleReceivedCorruptedMessage(masterSecret, message, true);

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
      KeyExchangeProcessorV2 processor       = new KeyExchangeProcessorV2(context, masterSecret, recipientDevice);
      PreKeyWhisperMessage   preKeyExchange  = new PreKeyWhisperMessage(message.getBody());

      if (processor.isTrusted(preKeyExchange)) {
        processor.processKeyExchangeMessage(preKeyExchange);

        IncomingPushMessage bundledMessage = message.withBody(preKeyExchange.getWhisperMessage().serialize());
        handleReceivedSecureMessage(masterSecret, bundledMessage);
      } else {
        SmsTransportDetails transportDetails = new SmsTransportDetails();
        String              encoded          = new String(transportDetails.getEncodedMessage(message.getBody()));
        IncomingTextMessage textMessage      = new IncomingTextMessage(message, "", null);

        textMessage = new IncomingPreKeyBundleMessage(textMessage, encoded);
        DatabaseFactory.getEncryptingSmsDatabase(context).insertMessageInbox(masterSecret, textMessage);
      }
    } catch (InvalidKeyException e) {
      Log.w("PushReceiver", e);
      handleReceivedCorruptedKey(masterSecret, message, false);
    } catch (InvalidVersionException e) {
      Log.w("PushReceiver", e);
      handleReceivedCorruptedKey(masterSecret, message, true);
    } catch (InvalidKeyIdException e) {
      Log.w("PushReceiver", e);
      handleReceivedCorruptedKey(masterSecret, message, false);
    } catch (InvalidMessageException e) {
      Log.w("PushReceiver", e);
      handleReceivedCorruptedKey(masterSecret, message, false);
    } catch (RecipientFormattingException e) {
      Log.w("PushReceiver", e);
      handleReceivedCorruptedKey(masterSecret, message, false);
    }
  }

  private void handleReceivedMessage(MasterSecret masterSecret,
                                     IncomingPushMessage message,
                                     boolean secure)
  {
    try {
      Log.w("PushReceiver", "Processing: " + new String(message.getBody()));
      PushMessageContent messageContent = PushMessageContent.parseFrom(message.getBody());

      if (messageContent.hasGroup()) {
        Log.w("PushReceiver", "Received push group message...");
        handleReceivedGroupMessage(masterSecret, message, messageContent, secure);
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

  private void handleReceivedGroupMessage(MasterSecret masterSecret,
                                          IncomingPushMessage message,
                                          PushMessageContent messageContent,
                                          boolean secure)
  {
    if (messageContent.getGroup().getType().equals(Type.UNKNOWN)) {
      Log.w("PushReceiver", "Received group message of unknown type: " +
                            messageContent.getGroup().getType().getNumber());
      return;
    }

    if (!messageContent.getGroup().hasId()) {
      Log.w("PushReceiver", "Received group message with no id!");
      return;
    }

    GroupDatabase database = DatabaseFactory.getGroupDatabase(context);
    GroupContext  group    = messageContent.getGroup();
    byte[]        id       = group.getId().toByteArray();
    int           type     = group.getType().getNumber();

    switch (type) {
      case Type.CREATE_VALUE:
        database.create(id, message.getSource(), group.getName(), group.getMembersList(), group.getAvatar(), message.getRelay());
        break;
      case Type.ADD_VALUE:
        database.add(id, message.getSource(), group.getMembersList());
        break;
      case Type.QUIT_VALUE:
        database.remove(id, message.getSource());
        break;
      case Type.MODIFY_VALUE:
        database.update(id, message.getSource(), group.getName(), group.getAvatar());
        break;
      case Type.DELIVER_VALUE:
        break;
      case Type.UNKNOWN_VALUE:
      default:
        Log.w("PushReceiver", "Received group message of unknown type: " + type);
        return;
    }

    if (group.hasAvatar()) {
      Intent intent = new Intent(context, SendReceiveService.class);
      intent.setAction(SendReceiveService.DOWNLOAD_AVATAR_ACTION);
      intent.putExtra("group_id", group.getId().toByteArray());
      context.startService(intent);
    }

    if (messageContent.getAttachmentsCount() > 0) {
      handleReceivedMediaMessage(masterSecret, message, messageContent, secure);
    } else {
      handleReceivedTextMessage(masterSecret, message, messageContent, secure);
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
    IncomingTextMessage   textMessage = new IncomingTextMessage(message, "", messageContent.getGroup());

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
