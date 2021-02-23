package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.session.libsession.messaging.jobs.Data;
import org.session.libsession.messaging.sending_receiving.attachments.Attachment;
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment;
import org.session.libsession.messaging.threads.recipients.Recipient;
import org.session.libsession.messaging.threads.Address;
import org.session.libsession.utilities.GroupUtil;

import org.session.libsignal.service.api.crypto.UnidentifiedAccess;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.session.libsignal.utilities.logging.Log;
import org.thoughtcrime.securesms.loki.protocol.ClosedGroupsProtocolV2;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.session.libsignal.libsignal.util.guava.Optional;
import org.session.libsignal.service.api.SignalServiceMessageSender;
import org.session.libsignal.service.api.messages.SendMessageResult;
import org.session.libsignal.service.api.messages.SignalServiceAttachment;
import org.session.libsignal.service.api.messages.SignalServiceDataMessage;
import org.session.libsignal.service.api.messages.SignalServiceDataMessage.Preview;
import org.session.libsignal.service.api.messages.SignalServiceDataMessage.Quote;
import org.session.libsignal.service.api.messages.SignalServiceGroup;
import org.session.libsignal.service.api.messages.shared.SharedContact;
import org.session.libsignal.service.api.push.SignalServiceAddress;
import org.session.libsignal.service.internal.push.SignalServiceProtos.GroupContext;

import java.io.IOException;
import java.util.Collections;
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
    this.filterAddress = filterAddress == null ? null :filterAddress.toString();
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
  public @NonNull
  Data serialize() {
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
      throws MmsException, NoSuchMessageException
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

      List<Address> targets;

      if      (filterAddress != null)              targets = Collections.singletonList(Address.fromSerialized(filterAddress));
      else if (!existingNetworkFailures.isEmpty()) targets = Stream.of(existingNetworkFailures).map(NetworkFailure::getAddress).toList();
      else                                         targets = ClosedGroupsProtocolV2.getMessageDestinations(context, message.getRecipient().getAddress().toGroupString());

      List<SendMessageResult>   results                  = deliver(message, targets);
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
    } catch (Exception e) {
      warn(TAG, e);
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof IOException) return true;
    // Loki - Disable since we have our own retrying
    return false;
  }

  @Override
  public void onCanceled() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
  }

  private List<SendMessageResult> deliver(OutgoingMediaMessage message, @NonNull List<Address> destinations)
      throws IOException {

    Address address = message.getRecipient().getAddress();

    List<SignalServiceAddress>                 addresses          = Stream.of(destinations).map(this::getPushAddress).toList();
    List<Optional<UnidentifiedAccess>>         unidentifiedAccess = Stream.of(addresses)
                                                                      .map(a -> Address.fromSerialized(a.getNumber()))
                                                                      .map(a -> Recipient.from(context, a, false))
                                                                      .map(recipient -> UnidentifiedAccessUtil.getAccessFor(context, recipient))
                                                                      .toList();

    if (message.isGroup() && address.isClosedGroup()) {
      SignalServiceGroup.GroupType groupType = address.isOpenGroup() ? SignalServiceGroup.GroupType.PUBLIC_CHAT : SignalServiceGroup.GroupType.SIGNAL;
      String                                     groupId            = address.toGroupString();
      List<Attachment>                           attachments        = Stream.of(message.getAttachments()).filterNot(Attachment::isSticker).toList();
      List<SignalServiceAttachment>              attachmentPointers = getAttachmentPointersFor(attachments);
      // Loki - Only send GroupUpdate or GroupQuit messages to closed groups
      OutgoingGroupMediaMessage groupMessage     = (OutgoingGroupMediaMessage) message;
      GroupContext              groupContext     = groupMessage.getGroupContext();
      SignalServiceAttachment   avatar           = attachmentPointers.isEmpty() ? null : attachmentPointers.get(0);
      SignalServiceGroup.Type   type             = groupMessage.isGroupQuit() ? SignalServiceGroup.Type.QUIT : SignalServiceGroup.Type.UPDATE;
      SignalServiceGroup        group            = new SignalServiceGroup(type, GroupUtil.getDecodedGroupIDAsData(groupId), groupType, groupContext.getName(), groupContext.getMembersList(), avatar, groupContext.getAdminsList());
      SignalServiceDataMessage  groupDataMessage = SignalServiceDataMessage.newBuilder()
                                                                           .withTimestamp(message.getSentTimeMillis())
                                                                           .withExpiration(message.getRecipient().getExpireMessages())
                                                                           .asGroupMessage(group)
                                                                           .build();

      return messageSender.sendMessage(messageId, addresses, unidentifiedAccess, groupDataMessage);
    } else {
      SignalServiceDataMessage groupMessage = getDataMessage(address, message).build();

      return messageSender.sendMessage(messageId, addresses, unidentifiedAccess, groupMessage);
    }
  }

  public SignalServiceDataMessage.Builder getDataMessage(Address address, OutgoingMediaMessage message) {

    SignalServiceGroup.GroupType groupType = address.isOpenGroup() ? SignalServiceGroup.GroupType.PUBLIC_CHAT : SignalServiceGroup.GroupType.SIGNAL;

    String                                     groupId            = address.toGroupString();
    Optional<byte[]>                           profileKey         = getProfileKey(message.getRecipient());
    Optional<Quote>                            quote              = getQuoteFor(message);
    List<SharedContact>                        sharedContacts     = getSharedContactsFor(message);
    List<Preview>                              previews           = getPreviewsFor(message);
    List<Attachment>                           attachments        = Stream.of(message.getAttachments()).filterNot(Attachment::isSticker).toList();
    List<SignalServiceAttachment>              attachmentPointers = getAttachmentPointersFor(attachments);

    SignalServiceGroup       group        = new SignalServiceGroup(GroupUtil.getDecodedGroupIDAsData(groupId), groupType);
    return SignalServiceDataMessage.newBuilder()
            .withTimestamp(message.getSentTimeMillis())
            .asGroupMessage(group)
            .withAttachments(attachmentPointers)
            .withBody(message.getBody())
            .withExpiration((int)(message.getExpiresIn() / 1000))
            .asExpirationUpdate(message.isExpirationUpdate())
            .withProfileKey(profileKey.orNull())
            .withQuote(quote.orNull())
            .withSharedContacts(sharedContacts)
            .withPreviews(previews);
  }

  public static class Factory implements Job.Factory<PushGroupSendJob> {
    @Override
    public @NonNull PushGroupSendJob create(@NonNull Parameters parameters, @NonNull Data data) {
      String  address = data.getString(KEY_FILTER_ADDRESS);
      Address filter  = address != null ? Address.fromSerialized(data.getString(KEY_FILTER_ADDRESS)) : null;

      return new PushGroupSendJob(parameters, data.getLong(KEY_MESSAGE_ID), filter);
    }
  }
}
