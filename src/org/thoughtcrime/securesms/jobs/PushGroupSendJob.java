package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase.GroupReceiptInfo;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Preview;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Quote;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;
import org.whispersystems.signalservice.loki.api.LokiPublicChat;
import org.whispersystems.signalservice.loki.api.LokiStorageAPI;
import org.whispersystems.signalservice.loki.utilities.PromiseUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class PushGroupSendJob extends PushSendJob implements InjectableType {

  public static final String KEY = "PushGroupSendJob";

  private static final String TAG = PushGroupSendJob.class.getSimpleName();

  @Inject SignalServiceMessageSender messageSender;

  private static final String KEY_MESSAGE_ID     = "message_id";
  private static final String KEY_FILTER_ADDRESS = "filter_address";

  private long   messageId;
  private String filterAddress;

  public PushGroupSendJob(long messageId, @NonNull Address destination, @Nullable Address filterAddress) {
    this(new Job.Parameters.Builder()
                           .setQueue(destination.toGroupString())
                           .addConstraint(NetworkConstraint.KEY)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build(),
         messageId, filterAddress);

  }

  private PushGroupSendJob(@NonNull Job.Parameters parameters, long messageId, @Nullable Address filterAddress) {
    super(parameters);

    this.messageId     = messageId;
    this.filterAddress = filterAddress == null ? null :filterAddress.toPhoneString();
  }

  @WorkerThread
  public static void enqueue(@NonNull Context context, @NonNull JobManager jobManager, long messageId, @NonNull Address destination, @Nullable Address filterAddress) {
    try {
      MmsDatabase          database    = DatabaseFactory.getMmsDatabase(context);
      OutgoingMediaMessage message     = database.getOutgoingMessage(messageId);
      List<Attachment>     attachments = new LinkedList<>();

      attachments.addAll(message.getAttachments());
      attachments.addAll(Stream.of(message.getLinkPreviews()).filter(p -> p.getThumbnail().isPresent()).map(p -> p.getThumbnail().get()).toList());
      attachments.addAll(Stream.of(message.getSharedContacts()).filter(c -> c.getAvatar() != null).map(c -> c.getAvatar().getAttachment()).withoutNulls().toList());

      List<AttachmentUploadJob> attachmentJobs = Stream.of(attachments).map(a -> new AttachmentUploadJob(((DatabaseAttachment) a).getAttachmentId(), destination)).toList();

      if (attachmentJobs.isEmpty()) {
        jobManager.add(new PushGroupSendJob(messageId, destination, filterAddress));
      } else {
        jobManager.startChain(attachmentJobs)
                  .then(new PushGroupSendJob(messageId, destination, filterAddress))
                  .enqueue();
      }

    } catch (NoSuchMessageException | MmsException e) {
      Log.w(TAG, "Failed to enqueue message.", e);
      DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_MESSAGE_ID, messageId)
                             .putString(KEY_FILTER_ADDRESS, filterAddress)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onAdded() {
    DatabaseFactory.getMmsDatabase(context).markAsSending(messageId);
  }

  @Override
  public void onPushSend()
      throws IOException, MmsException, NoSuchMessageException,  RetryLaterException
  {
    MmsDatabase               database                   = DatabaseFactory.getMmsDatabase(context);
    OutgoingMediaMessage      message                    = database.getOutgoingMessage(messageId);
    List<NetworkFailure>      existingNetworkFailures    = message.getNetworkFailures();
    List<IdentityKeyMismatch> existingIdentityMismatches = message.getIdentityKeyMismatches();

    if (database.isSent(messageId)) {
      log(TAG, "Message " + messageId + " was already sent. Ignoring.");
      return;
    }

    try {
      log(TAG, "Sending message: " + messageId);

      List<Address> target;

      if      (filterAddress != null)              target = Collections.singletonList(Address.fromSerialized(filterAddress));
      else if (!existingNetworkFailures.isEmpty()) target = Stream.of(existingNetworkFailures).map(NetworkFailure::getAddress).toList();
      else                                         target = getGroupMessageRecipients(message.getRecipient().getAddress().toGroupString(), messageId);

      String localNumber = TextSecurePreferences.getLocalNumber(context);

      // Only send messages to the contacts we have sessions with
      List<Address> validTargets = Stream.of(target).filter(member -> {
        if (member.isPublicChat()) { return true; }

        // Our device is always valid
        if (member.serialize().equalsIgnoreCase(localNumber)) { return true; }

        SignalProtocolAddress protocolAddress = new SignalProtocolAddress(member.toPhoneString(), SignalServiceAddress.DEFAULT_DEVICE_ID);
        boolean hasSession = new TextSecureSessionStore(context).containsSession(protocolAddress);
        if (hasSession) { return true; }

        // We should allow sending if we have a prekeybundle for the contact
        return DatabaseFactory.getLokiPreKeyBundleDatabase(context).hasPreKeyBundle(member.toPhoneString());
      }).toList();

      // Send a session request to the other devices
      List<Address> others = Stream.of(target).filter(t -> !validTargets.contains(t)).toList();
      for (Address device : others) {
        MessageSender.sendBackgroundSessionRequest(context, device.toPhoneString());
      }

      List<SendMessageResult>   results                  = deliver(message, validTargets);
      List<NetworkFailure>      networkFailures          = Stream.of(results).filter(SendMessageResult::isNetworkFailure).map(result -> new NetworkFailure(Address.fromSerialized(result.getAddress().getNumber()))).toList();
      List<IdentityKeyMismatch> identityMismatches       = Stream.of(results).filter(result -> result.getIdentityFailure() != null).map(result -> new IdentityKeyMismatch(Address.fromSerialized(result.getAddress().getNumber()), result.getIdentityFailure().getIdentityKey())).toList();
      Set<Address>              successAddresses         = Stream.of(results).filter(result -> result.getSuccess() != null).map(result -> Address.fromSerialized(result.getAddress().getNumber())).collect(Collectors.toSet());
      List<NetworkFailure>      resolvedNetworkFailures  = Stream.of(existingNetworkFailures).filter(failure -> successAddresses.contains(failure.getAddress())).toList();
      List<IdentityKeyMismatch> resolvedIdentityFailures = Stream.of(existingIdentityMismatches).filter(failure -> successAddresses.contains(failure.getAddress())).toList();
      List<SendMessageResult>   successes                = Stream.of(results).filter(result -> result.getSuccess() != null).toList();

      for (NetworkFailure resolvedFailure : resolvedNetworkFailures) {
        database.removeFailure(messageId, resolvedFailure);
        existingNetworkFailures.remove(resolvedFailure);
      }

      for (IdentityKeyMismatch resolvedIdentity : resolvedIdentityFailures) {
        database.removeMismatchedIdentity(messageId, resolvedIdentity.getAddress(), resolvedIdentity.getIdentityKey());
        existingIdentityMismatches.remove(resolvedIdentity);
      }

      if (!networkFailures.isEmpty()) {
        database.addFailures(messageId, networkFailures);
      }

      for (IdentityKeyMismatch mismatch : identityMismatches) {
        database.addMismatchedIdentity(messageId, mismatch.getAddress(), mismatch.getIdentityKey());
      }

      for (SendMessageResult success : successes) {
        DatabaseFactory.getGroupReceiptDatabase(context).setUnidentified(Address.fromSerialized(success.getAddress().getNumber()),
                                                                         messageId,
                                                                         success.getSuccess().isUnidentified());
      }

      if (existingNetworkFailures.isEmpty() && networkFailures.isEmpty() && identityMismatches.isEmpty() && existingIdentityMismatches.isEmpty()) {
        database.markAsSent(messageId, true);

        markAttachmentsUploaded(messageId, message.getAttachments());

        if (message.getExpiresIn() > 0 && !message.isExpirationUpdate()) {
          database.markExpireStarted(messageId);
          ApplicationContext.getInstance(context)
                            .getExpiringMessageManager()
                            .scheduleDeletion(messageId, true, message.getExpiresIn());
        }
      } else if (!networkFailures.isEmpty()) {
        throw new RetryLaterException();
      } else if (!identityMismatches.isEmpty()) {
        database.markAsSentFailed(messageId);
        notifyMediaMessageDeliveryFailed(context, messageId);
      }
    } catch (UntrustedIdentityException | UndeliverableMessageException e) {
      warn(TAG, e);
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof IOException)         return true;

    // Loki - Disable since we have our own retrying
    // if (exception instanceof RetryLaterException) return true;
    return false;
  }

  @Override
  public void onCanceled() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
  }

  private List<SendMessageResult> deliver(OutgoingMediaMessage message, @NonNull List<Address> destinations)
      throws IOException, UntrustedIdentityException, UndeliverableMessageException {
    // rotateSenderCertificateIfNecessary();

    // Messages shouldn't be able to be sent to RSS Feeds
    Address groupAddress = message.getRecipient().getAddress();
    if (groupAddress.isRSSFeed()) {
      List<SendMessageResult> results = new ArrayList<>();
      for (Address destination : destinations) results.add(SendMessageResult.networkFailure(new SignalServiceAddress(destination.toPhoneString())));
      return results;
    }

    String                                     groupId            = groupAddress.toGroupString();
    Optional<byte[]>                           profileKey         = getProfileKey(message.getRecipient());
    Optional<Quote>                            quote              = getQuoteFor(message);
    Optional<SignalServiceDataMessage.Sticker> sticker            = getStickerFor(message);
    List<SharedContact>                        sharedContacts     = getSharedContactsFor(message);
    List<Preview>                              previews           = getPreviewsFor(message);
    List<SignalServiceAddress>                 addresses          = Stream.of(destinations).map(this::getPushAddress).toList();
    List<Attachment>                           attachments        = Stream.of(message.getAttachments()).filterNot(Attachment::isSticker).toList();
    List<SignalServiceAttachment>              attachmentPointers = getAttachmentPointersFor(attachments);

    List<Optional<UnidentifiedAccessPair>> unidentifiedAccess = Stream.of(addresses)
                                                                      .map(address -> Address.fromSerialized(address.getNumber()))
                                                                      .map(address -> Recipient.from(context, address, false))
                                                                      .map(recipient -> UnidentifiedAccessUtil.getAccessFor(context, recipient))
                                                                      .toList();

    SignalServiceGroup.GroupType groupType = SignalServiceGroup.GroupType.SIGNAL;
    if (groupAddress.isPublicChat()) {
      groupType = SignalServiceGroup.GroupType.PUBLIC_CHAT;
    }

    if (message.isGroup() && groupAddress.isSignalGroup()) {
      // Loki - Only send GroupUpdate or GroupQuit to signal groups
      OutgoingGroupMediaMessage groupMessage     = (OutgoingGroupMediaMessage) message;
      GroupContext              groupContext     = groupMessage.getGroupContext();
      SignalServiceAttachment   avatar           = attachmentPointers.isEmpty() ? null : attachmentPointers.get(0);
      SignalServiceGroup.Type   type             = groupMessage.isGroupQuit() ? SignalServiceGroup.Type.QUIT : SignalServiceGroup.Type.UPDATE;
      SignalServiceGroup        group            = new SignalServiceGroup(type, GroupUtil.getDecodedId(groupId), groupType, groupContext.getName(), groupContext.getMembersList(), avatar, groupContext.getAdminsList());
      SignalServiceDataMessage  groupDataMessage = SignalServiceDataMessage.newBuilder()
                                                                           .withTimestamp(message.getSentTimeMillis())
                                                                           .withExpiration(message.getRecipient().getExpireMessages())
                                                                           .withBody(message.getBody())
                                                                           .asGroupMessage(group)
                                                                           .build();

      return messageSender.sendMessage(messageId, addresses, unidentifiedAccess, groupDataMessage);
    } else {
      SignalServiceGroup       group        = new SignalServiceGroup(GroupUtil.getDecodedId(groupId), groupType);
      SignalServiceDataMessage groupMessage = SignalServiceDataMessage.newBuilder()
                                                                      .withTimestamp(message.getSentTimeMillis())
                                                                      .asGroupMessage(group)
                                                                      .withAttachments(attachmentPointers)
                                                                      .withBody(message.getBody())
                                                                      .withExpiration((int)(message.getExpiresIn() / 1000))
                                                                      .asExpirationUpdate(message.isExpirationUpdate())
                                                                      .withProfileKey(profileKey.orNull())
                                                                      .withQuote(quote.orNull())
                                                                      .withSticker(sticker.orNull())
                                                                      .withSharedContacts(sharedContacts)
                                                                      .withPreviews(previews)
                                                                      .build();

      return messageSender.sendMessage(messageId, addresses, unidentifiedAccess, groupMessage);
    }
  }

  private @NonNull List<Address> getGroupMessageRecipients(String groupId, long messageId) {
    if (GroupUtil.isRssFeed(groupId)) { return new ArrayList<>(); }

    // Loki - All public chat group messages should be directed to their respective servers
    if (GroupUtil.isPublicChat(groupId)) {
      ArrayList<Address> result = new ArrayList<>();
      long threadID = GroupManager.getThreadIdFromGroupId(groupId, context);
      LokiPublicChat publicChat = DatabaseFactory.getLokiThreadDatabase(context).getPublicChat(threadID);
      if (publicChat != null) {
        result.add(Address.fromSerialized(groupId));
      }
      return result;
    } else {
      /*
        Our biggest assumption here is that group members will only consist of primary devices.
        No secondary device should be able to be added to a group.
       */
      List<GroupReceiptInfo> destinations = DatabaseFactory.getGroupReceiptDatabase(context).getGroupReceiptInfo(messageId);

      Set<Address> memberSet = new HashSet<>();
      if (destinations.isEmpty()) {
        List<Recipient> groupMembers = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupId, false);
        memberSet.addAll(Stream.of(groupMembers).map(Recipient::getAddress).toList());
      } else {
        memberSet.addAll(Stream.of(destinations).map(GroupReceiptInfo::getAddress).toList());
      }

      // Replace primary device public key with ours so message syncing works correctly
      String masterHexEncodedPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context);
      String localNumber = TextSecurePreferences.getLocalNumber(context);
      if (masterHexEncodedPublicKey != null &&  memberSet.contains(Address.fromSerialized(masterHexEncodedPublicKey))) {
        memberSet.remove(Address.fromSerialized(masterHexEncodedPublicKey));
        memberSet.add(Address.fromSerialized(localNumber));
      }

      // Add secondary devices to the list. We shouldn't add our secondary devices
      try {
        Set<Address> originalMemberSet = new HashSet<>(memberSet);
        for (Address member : originalMemberSet) {
          if (!member.isPhone() || member.serialize().equalsIgnoreCase(localNumber)) { continue; }

          try {
            List<String> secondaryDevices = PromiseUtil.timeout(LokiStorageAPI.shared.getSecondaryDevicePublicKeys(member.serialize()), 5000).get();
            memberSet.addAll(Stream.of(secondaryDevices).map(string -> {
              // Loki - Calling .map(Address::fromSerialized) is causing errors, thus we use the long method :(
              return Address.fromSerialized(string);
            }).toList());
          } catch (Exception e) {
            // Timed out, go to the next member
          }
        }
      } catch (Exception e) {
        Log.e("PushGroupSend", "Error occurred while adding secondary devices: " + e);
      }

      return new LinkedList<>(memberSet);
    }
  }

  public static class Factory implements Job.Factory<PushGroupSendJob> {
    @Override
    public @NonNull PushGroupSendJob create(@NonNull Parameters parameters, @NonNull org.thoughtcrime.securesms.jobmanager.Data data) {
      String  address = data.getString(KEY_FILTER_ADDRESS);
      Address filter  = address != null ? Address.fromSerialized(data.getString(KEY_FILTER_ADDRESS)) : null;

      return new PushGroupSendJob(parameters, data.getLong(KEY_MESSAGE_ID), filter);
    }
  }
}
