package org.thoughtcrime.securesms.messages;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.SenderKeyUtil;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import org.thoughtcrime.securesms.database.MessageSendLogDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.DistributionListId;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.RecipientAccessList;
import org.thoughtcrime.securesms.util.SignalLocalMetrics;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidRegistrationIdException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.CancelationException;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.SignalServiceMessageSender.LegacyGroupEvents;
import org.whispersystems.signalservice.api.SignalServiceMessageSender.SenderKeyGroupEvents;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceStoryMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.internal.push.exceptions.InvalidUnidentifiedAccessHeaderException;
import org.whispersystems.signalservice.internal.push.http.CancelationSignal;
import org.whispersystems.signalservice.internal.push.http.PartialSendCompleteListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public final class GroupSendUtil {

  private static final String TAG = Log.tag(GroupSendUtil.class);

  private GroupSendUtil() {}


  /**
   * Handles all of the logic of sending to a group. Will do sender key sends and legacy 1:1 sends as-needed, and give you back a list of
   * {@link SendMessageResult}s just like we're used to.
   *
   * Messages sent this way, if failed to be decrypted by the receiving party, can be requested to be resent.
   *
   * @param groupId The groupId of the group you're sending to, or null if you're sending to a collection of recipients not joined by a group.
   * @param isRecipientUpdate True if you've already sent this message to some recipients in the past, otherwise false.
   */
  @WorkerThread
  public static List<SendMessageResult> sendResendableDataMessage(@NonNull Context context,
                                                                  @Nullable GroupId.V2 groupId,
                                                                  @NonNull List<Recipient> allTargets,
                                                                  boolean isRecipientUpdate,
                                                                  ContentHint contentHint,
                                                                  @NonNull MessageId messageId,
                                                                  @NonNull SignalServiceDataMessage message)
      throws IOException, UntrustedIdentityException
  {
    return sendMessage(context, groupId, getDistributionId(groupId), messageId, allTargets, isRecipientUpdate, DataSendOperation.resendable(message, contentHint, messageId), null);
  }

  /**
   * Handles all of the logic of sending to a group. Will do sender key sends and legacy 1:1 sends as-needed, and give you back a list of
   * {@link SendMessageResult}s just like we're used to.
   *
   * Messages sent this way, if failed to be decrypted by the receiving party, can *not* be requested to be resent.
   *
   * @param groupId The groupId of the group you're sending to, or null if you're sending to a collection of recipients not joined by a group.
   * @param isRecipientUpdate True if you've already sent this message to some recipients in the past, otherwise false.
   */
  @WorkerThread
  public static List<SendMessageResult> sendUnresendableDataMessage(@NonNull Context context,
                                                                    @Nullable GroupId.V2 groupId,
                                                                    @NonNull List<Recipient> allTargets,
                                                                    boolean isRecipientUpdate,
                                                                    ContentHint contentHint,
                                                                    @NonNull SignalServiceDataMessage message)
      throws IOException, UntrustedIdentityException
  {
    return sendMessage(context, groupId, getDistributionId(groupId), null, allTargets, isRecipientUpdate, DataSendOperation.unresendable(message, contentHint), null);
  }

  /**
   * Handles all of the logic of sending to a group. Will do sender key sends and legacy 1:1 sends as-needed, and give you back a list of
   * {@link SendMessageResult}s just like we're used to.
   *
   * @param groupId The groupId of the group you're sending to, or null if you're sending to a collection of recipients not joined by a group.
   */
  @WorkerThread
  public static List<SendMessageResult> sendTypingMessage(@NonNull Context context,
                                                          @Nullable GroupId.V2 groupId,
                                                          @NonNull List<Recipient> allTargets,
                                                          @NonNull SignalServiceTypingMessage message,
                                                          @Nullable CancelationSignal cancelationSignal)
      throws IOException, UntrustedIdentityException
  {
    return sendMessage(context, groupId, getDistributionId(groupId), null, allTargets, false, new TypingSendOperation(message), cancelationSignal);
  }

  /**
   * Handles all of the logic of sending to a group. Will do sender key sends and legacy 1:1 sends as-needed, and give you back a list of
   * {@link SendMessageResult}s just like we're used to.
   *
   * @param groupId The groupId of the group you're sending to, or null if you're sending to a collection of recipients not joined by a group.
   */
  @WorkerThread
  public static List<SendMessageResult> sendCallMessage(@NonNull Context context,
                                                        @Nullable GroupId.V2 groupId,
                                                        @NonNull List<Recipient> allTargets,
                                                        @NonNull SignalServiceCallMessage message)
      throws IOException, UntrustedIdentityException
  {
    return sendMessage(context, groupId, getDistributionId(groupId), null, allTargets, false, new CallSendOperation(message), null);
  }

  /**
   * Handles all of the logic of sending a story to a group. Will do sender key sends and legacy 1:1 sends as-needed, and give you back a list of
   * {@link SendMessageResult}s just like we're used to.
   *
   * @param isRecipientUpdate True if you've already sent this message to some recipients in the past, otherwise false.
   */
  public static List<SendMessageResult> sendStoryMessage(@NonNull Context context,
                                                         @NonNull DistributionListId distributionListId,
                                                         @NonNull List<Recipient> allTargets,
                                                         boolean isRecipientUpdate,
                                                         @NonNull MessageId messageId,
                                                         long sentTimestamp,
                                                         @NonNull SignalServiceStoryMessage message)
      throws IOException, UntrustedIdentityException
  {
    return sendMessage(
        context,
        null,
        getDistributionId(distributionListId),
        messageId,
        allTargets,
        isRecipientUpdate,
        new StorySendOperation(messageId, null, sentTimestamp, message),
        null);
  }

  /**
   * Handles all of the logic of sending a story to a group. Will do sender key sends and legacy 1:1 sends as-needed, and give you back a list of
   * {@link SendMessageResult}s just like we're used to.
   *
   * @param isRecipientUpdate True if you've already sent this message to some recipients in the past, otherwise false.
   */
  public static List<SendMessageResult> sendGroupStoryMessage(@NonNull Context context,
                                                              @NonNull GroupId.V2 groupId,
                                                              @NonNull List<Recipient> allTargets,
                                                              boolean isRecipientUpdate,
                                                              @NonNull MessageId messageId,
                                                              long sentTimestamp,
                                                              @NonNull SignalServiceStoryMessage message)
      throws IOException, UntrustedIdentityException
  {
    return sendMessage(
        context,
        groupId,
        getDistributionId(groupId),
        messageId,
        allTargets,
        isRecipientUpdate,
        new StorySendOperation(messageId, groupId, sentTimestamp, message),
        null);
  }

  /**
   * Handles all of the logic of sending to a group. Will do sender key sends and legacy 1:1 sends as-needed, and give you back a list of
   * {@link SendMessageResult}s just like we're used to.
   *
   * @param groupId The groupId of the group you're sending to, or null if you're sending to a collection of recipients not joined by a group.
   * @param isRecipientUpdate True if you've already sent this message to some recipients in the past, otherwise false.
   */
  @WorkerThread
  private static List<SendMessageResult> sendMessage(@NonNull Context context,
                                                     @Nullable GroupId.V2 groupId,
                                                     @Nullable DistributionId distributionId,
                                                     @Nullable MessageId relatedMessageId,
                                                     @NonNull List<Recipient> allTargets,
                                                     boolean isRecipientUpdate,
                                                     @NonNull SendOperation sendOperation,
                                                     @Nullable CancelationSignal cancelationSignal)
      throws IOException, UntrustedIdentityException
  {
    Log.i(TAG, "Starting group send. GroupId: " + (groupId != null ? groupId.toString() : "none") + ", DistributionListId: " + (distributionId != null ? distributionId.toString() : "none") + " RelatedMessageId: " + (relatedMessageId != null ? relatedMessageId.toString() : "none") + ", Targets: " + allTargets.size() + ", RecipientUpdate: " + isRecipientUpdate + ", Operation: " + sendOperation.getClass().getSimpleName());

    Set<Recipient>  unregisteredTargets = allTargets.stream().filter(Recipient::isUnregistered).collect(Collectors.toSet());
    List<Recipient> registeredTargets   = allTargets.stream().filter(r -> !unregisteredTargets.contains(r)).collect(Collectors.toList());

    RecipientData         recipients  = new RecipientData(context, registeredTargets);
    Optional<GroupRecord> groupRecord = groupId != null ? SignalDatabase.groups().getGroup(groupId) : Optional.absent();

    List<Recipient> senderKeyTargets = new LinkedList<>();
    List<Recipient> legacyTargets    = new LinkedList<>();

    for (Recipient recipient : registeredTargets) {
      Optional<UnidentifiedAccessPair> access          = recipients.getAccessPair(recipient.getId());
      boolean                          validMembership = true;

      if (groupId != null && (!groupRecord.isPresent() || !groupRecord.get().getMembers().contains(recipient.getId()))) {
        validMembership = false;
      }

      if (recipient.getSenderKeyCapability() == Recipient.Capability.SUPPORTED &&
          recipient.hasServiceId() &&
          access.isPresent() &&
          access.get().getTargetUnidentifiedAccess().isPresent() &&
          validMembership)
      {
        senderKeyTargets.add(recipient);
      } else {
        legacyTargets.add(recipient);
      }
    }

    if (distributionId == null) {
      Log.i(TAG, "No DistributionId. Using legacy.");
      legacyTargets.addAll(senderKeyTargets);
      senderKeyTargets.clear();
    } else if (Recipient.self().getSenderKeyCapability() != Recipient.Capability.SUPPORTED) {
      Log.i(TAG, "All of our devices do not support sender key. Using legacy.");
      legacyTargets.addAll(senderKeyTargets);
      senderKeyTargets.clear();
    } else if (SignalStore.internalValues().removeSenderKeyMinimum()) {
      Log.i(TAG, "Sender key minimum removed. Using for " + senderKeyTargets.size() + " recipients.");
    } else if (senderKeyTargets.size() < 2) {
      Log.i(TAG, "Too few sender-key-capable users (" + senderKeyTargets.size() + "). Doing all legacy sends.");
      legacyTargets.addAll(senderKeyTargets);
      senderKeyTargets.clear();
    } else {
      Log.i(TAG, "Can use sender key for " + senderKeyTargets.size() + "/" + allTargets.size() + " recipients.");
    }

    if (relatedMessageId != null && groupId != null) {
      SignalLocalMetrics.GroupMessageSend.onSenderKeyStarted(relatedMessageId.getId());
    }

    List<SendMessageResult>    allResults    = new ArrayList<>(allTargets.size());
    SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();

    if (senderKeyTargets.size() > 0 && distributionId != null) {
      long           keyCreateTime  = SenderKeyUtil.getCreateTimeForOurKey(distributionId);
      long           keyAge         = System.currentTimeMillis() - keyCreateTime;

      if (keyCreateTime != -1 && keyAge > FeatureFlags.senderKeyMaxAge()) {
        Log.w(TAG, "DistributionId " + distributionId + " was created at " + keyCreateTime + " and is " + (keyAge) + " ms old (~" + TimeUnit.MILLISECONDS.toDays(keyAge) + " days). Rotating.");
        SenderKeyUtil.rotateOurKey(distributionId);
      }

      try {
        List<SignalServiceAddress> targets = senderKeyTargets.stream().map(r -> recipients.getAddress(r.getId())).collect(Collectors.toList());
        List<UnidentifiedAccess>   access  = senderKeyTargets.stream().map(r -> recipients.requireAccess(r.getId())).collect(Collectors.toList());
        List<SendMessageResult>    results = sendOperation.sendWithSenderKey(messageSender, distributionId, targets, access, isRecipientUpdate);

        allResults.addAll(results);

        int successCount = (int) results.stream().filter(SendMessageResult::isSuccess).count();
        Log.d(TAG, "Successfully sent using sender key to " + successCount + "/" + targets.size() + " sender key targets.");

        if (sendOperation.shouldIncludeInMessageLog()) {
          SignalDatabase.messageLog().insertIfPossible(sendOperation.getSentTimestamp(), senderKeyTargets, results, sendOperation.getContentHint(), sendOperation.getRelatedMessageId());
        }

        if (relatedMessageId != null) {
          SignalLocalMetrics.GroupMessageSend.onSenderKeyMslInserted(relatedMessageId.getId());
        }
      } catch (InvalidUnidentifiedAccessHeaderException e) {
        Log.w(TAG, "Someone had a bad UD header. Falling back to legacy sends.", e);
        legacyTargets.addAll(senderKeyTargets);
      } catch (NoSessionException e) {
        Log.w(TAG, "No session. Falling back to legacy sends.", e);
        legacyTargets.addAll(senderKeyTargets);
      } catch (InvalidKeyException e) {
        Log.w(TAG, "Invalid key. Falling back to legacy sends.", e);
        legacyTargets.addAll(senderKeyTargets);
      } catch (InvalidRegistrationIdException e) {
        Log.w(TAG, "Invalid registrationId. Falling back to legacy sends.", e);
        legacyTargets.addAll(senderKeyTargets);
      } catch (NotFoundException e) {
        Log.w(TAG, "Someone was unregistered. Falling back to legacy sends.", e);
        legacyTargets.addAll(senderKeyTargets);
      }
    } else if (relatedMessageId != null) {
      SignalLocalMetrics.GroupMessageSend.onSenderKeyShared(relatedMessageId.getId());
      SignalLocalMetrics.GroupMessageSend.onSenderKeyEncrypted(relatedMessageId.getId());
      SignalLocalMetrics.GroupMessageSend.onSenderKeyMessageSent(relatedMessageId.getId());
      SignalLocalMetrics.GroupMessageSend.onSenderKeySyncSent(relatedMessageId.getId());
      SignalLocalMetrics.GroupMessageSend.onSenderKeyMslInserted(relatedMessageId.getId());
    }

    if (cancelationSignal != null && cancelationSignal.isCanceled()) {
      throw new CancelationException();
    }

    boolean onlyTargetIsSelfWithLinkedDevice = legacyTargets.isEmpty() && senderKeyTargets.isEmpty() && TextSecurePreferences.isMultiDevice(context);

    if (legacyTargets.size() > 0 || onlyTargetIsSelfWithLinkedDevice) {
      if (legacyTargets.size() > 0) {
        Log.i(TAG, "Need to do " + legacyTargets.size() + " legacy sends.");
      } else {
        Log.i(TAG, "Need to do a legacy send to send a sync message for a group of only ourselves.");
      }

      List<SignalServiceAddress>             targets         = legacyTargets.stream().map(r -> recipients.getAddress(r.getId())).collect(Collectors.toList());
      List<Optional<UnidentifiedAccessPair>> access          = legacyTargets.stream().map(r -> recipients.getAccessPair(r.getId())).collect(Collectors.toList());
      boolean                                recipientUpdate = isRecipientUpdate || allResults.size() > 0;

      final MessageSendLogDatabase messageLogDatabase  = SignalDatabase.messageLog();
      final AtomicLong             entryId             = new AtomicLong(-1);
      final boolean                includeInMessageLog = sendOperation.shouldIncludeInMessageLog();

      List<SendMessageResult> results = sendOperation.sendLegacy(messageSender, targets, access, recipientUpdate, result -> {
        if (!includeInMessageLog) {
          return;
        }

        synchronized (entryId) {
          if (entryId.get() == -1) {
            entryId.set(messageLogDatabase.insertIfPossible(recipients.requireRecipientId(result.getAddress()), sendOperation.getSentTimestamp(), result, sendOperation.getContentHint(), sendOperation.getRelatedMessageId()));
          } else {
            messageLogDatabase.addRecipientToExistingEntryIfPossible(entryId.get(), recipients.requireRecipientId(result.getAddress()), result);
          }
        }
      }, cancelationSignal);

      allResults.addAll(results);

      int successCount = (int) results.stream().filter(SendMessageResult::isSuccess).count();
      Log.d(TAG, "Successfully sent using 1:1 to " + successCount + "/" + targets.size() + " legacy targets.");
    } else if (relatedMessageId != null) {
      SignalLocalMetrics.GroupMessageSend.onLegacyMessageSent(relatedMessageId.getId());
      SignalLocalMetrics.GroupMessageSend.onLegacySyncFinished(relatedMessageId.getId());
    }

    if (unregisteredTargets.size() > 0) {
      Log.w(TAG, "There are " + unregisteredTargets.size() + " unregistered targets. Including failure results.");

      List<SendMessageResult> unregisteredResults = unregisteredTargets.stream()
                                                                       .filter(Recipient::hasServiceId)
                                                                       .map(t -> SendMessageResult.unregisteredFailure(new SignalServiceAddress(t.requireServiceId(), t.getE164().orNull())))
                                                                       .collect(Collectors.toList());

      if (unregisteredResults.size() < unregisteredTargets.size()) {
        Log.w(TAG, "There are " + (unregisteredTargets.size() - unregisteredResults.size()) + " targets that have no UUID! Cannot report a failure for them.");
      }

      allResults.addAll(unregisteredResults);
    }

    return allResults;
  }

  private static @Nullable DistributionId getDistributionId(@Nullable GroupId.V2 groupId) {
    if (groupId != null) {
      return SignalDatabase.groups().getOrCreateDistributionId(groupId);
    } else {
      return null;
    }
  }

  private static @Nullable DistributionId getDistributionId(@Nullable DistributionListId distributionListId) {
    if (distributionListId != null) {
      return Optional.fromNullable(SignalDatabase.distributionLists().getDistributionId(distributionListId)).orNull();
    } else {
      return null;
    }
  }

  /** Abstraction layer to handle the different types of message send operations we can do */
  private interface SendOperation {
    @NonNull List<SendMessageResult> sendWithSenderKey(@NonNull SignalServiceMessageSender messageSender,
                                                       @NonNull DistributionId distributionId,
                                                       @NonNull List<SignalServiceAddress> targets,
                                                       @NonNull List<UnidentifiedAccess> access,
                                                       boolean isRecipientUpdate)
        throws NoSessionException, UntrustedIdentityException, InvalidKeyException, IOException, InvalidRegistrationIdException;

    @NonNull List<SendMessageResult> sendLegacy(@NonNull SignalServiceMessageSender messageSender,
                                                @NonNull List<SignalServiceAddress> targets,
                                                @NonNull List<Optional<UnidentifiedAccessPair>> access,
                                                boolean isRecipientUpdate,
                                                @Nullable PartialSendCompleteListener partialListener,
                                                @Nullable CancelationSignal cancelationSignal)
        throws IOException, UntrustedIdentityException;

    @NonNull ContentHint getContentHint();
    long getSentTimestamp();
    boolean shouldIncludeInMessageLog();
    @NonNull MessageId getRelatedMessageId();
  }

  private static class DataSendOperation implements SendOperation {
    private final SignalServiceDataMessage message;
    private final ContentHint              contentHint;
    private final MessageId                relatedMessageId;
    private final boolean                  resendable;

    public static DataSendOperation resendable(@NonNull SignalServiceDataMessage message, @NonNull ContentHint contentHint, @NonNull MessageId relatedMessageId) {
      return new DataSendOperation(message, contentHint, true, relatedMessageId);
    }

    public static DataSendOperation unresendable(@NonNull SignalServiceDataMessage message, @NonNull ContentHint contentHint) {
      return new DataSendOperation(message, contentHint, false, null);
    }

    private DataSendOperation(@NonNull SignalServiceDataMessage message, @NonNull ContentHint contentHint, boolean resendable, @Nullable MessageId relatedMessageId) {
      this.message             = message;
      this.contentHint         = contentHint;
      this.resendable          = resendable;
      this.relatedMessageId    = relatedMessageId;

      if (resendable && relatedMessageId == null) {
        throw new IllegalArgumentException("If a message is resendable, it must have a related message ID!");
      }
    }

    @Override
    public @NonNull List<SendMessageResult> sendWithSenderKey(@NonNull SignalServiceMessageSender messageSender,
                                                              @NonNull DistributionId distributionId,
                                                              @NonNull List<SignalServiceAddress> targets,
                                                              @NonNull List<UnidentifiedAccess> access,
                                                              boolean isRecipientUpdate)
        throws NoSessionException, UntrustedIdentityException, InvalidKeyException, IOException, InvalidRegistrationIdException
    {
      SenderKeyGroupEvents listener = relatedMessageId != null ? new SenderKeyMetricEventListener(relatedMessageId.getId()) : SenderKeyGroupEvents.EMPTY;
      return messageSender.sendGroupDataMessage(distributionId, targets, access, isRecipientUpdate, contentHint, message, listener);
    }

    @Override
    public @NonNull List<SendMessageResult> sendLegacy(@NonNull SignalServiceMessageSender messageSender,
                                                       @NonNull List<SignalServiceAddress> targets,
                                                       @NonNull List<Optional<UnidentifiedAccessPair>> access,
                                                       boolean isRecipientUpdate,
                                                       @Nullable PartialSendCompleteListener partialListener,
                                                       @Nullable CancelationSignal cancelationSignal)
        throws IOException, UntrustedIdentityException
    {
      LegacyGroupEvents listener = relatedMessageId != null ? new LegacyMetricEventListener(relatedMessageId.getId()) : LegacyGroupEvents.EMPTY;
      return messageSender.sendDataMessage(targets, access, isRecipientUpdate, contentHint, message, listener, partialListener, cancelationSignal);
    }

    @Override
    public @NonNull ContentHint getContentHint() {
      return contentHint;
    }

    @Override
    public long getSentTimestamp() {
      return message.getTimestamp();
    }

    @Override
    public boolean shouldIncludeInMessageLog() {
      return resendable;
    }

    @Override
    public @NonNull MessageId getRelatedMessageId() {
      if (relatedMessageId != null) {
        return relatedMessageId;
      } else {
        throw new UnsupportedOperationException();
      }
    }
  }

  private static class TypingSendOperation implements SendOperation {

    private final SignalServiceTypingMessage message;

    private TypingSendOperation(@NonNull SignalServiceTypingMessage message) {
      this.message = message;
    }

    @Override
    public @NonNull List<SendMessageResult> sendWithSenderKey(@NonNull SignalServiceMessageSender messageSender,
                                                              @NonNull DistributionId distributionId,
                                                              @NonNull List<SignalServiceAddress> targets,
                                                              @NonNull List<UnidentifiedAccess> access,
                                                              boolean isRecipientUpdate)
        throws NoSessionException, UntrustedIdentityException, InvalidKeyException, IOException, InvalidRegistrationIdException
    {
      messageSender.sendGroupTyping(distributionId, targets, access, message);
      return targets.stream().map(a -> SendMessageResult.success(a, Collections.emptyList(), true, false, -1, Optional.absent())).collect(Collectors.toList());
    }

    @Override
    public @NonNull List<SendMessageResult> sendLegacy(@NonNull SignalServiceMessageSender messageSender,
                                                       @NonNull List<SignalServiceAddress> targets,
                                                       @NonNull List<Optional<UnidentifiedAccessPair>> access,
                                                       boolean isRecipientUpdate,
                                                       @Nullable PartialSendCompleteListener partialListener,
                                                       @Nullable CancelationSignal cancelationSignal)
        throws IOException
    {
      messageSender.sendTyping(targets, access, message, cancelationSignal);
      return targets.stream().map(a -> SendMessageResult.success(a, Collections.emptyList(), true, false, -1, Optional.absent())).collect(Collectors.toList());
    }

    @Override
    public @NonNull ContentHint getContentHint() {
      return ContentHint.IMPLICIT;
    }

    @Override
    public long getSentTimestamp() {
      return message.getTimestamp();
    }

    @Override
    public boolean shouldIncludeInMessageLog() {
      return false;
    }

    @Override
    public @NonNull MessageId getRelatedMessageId() {
      throw new UnsupportedOperationException();
    }
  }

  private static class CallSendOperation implements SendOperation {

    private final SignalServiceCallMessage message;

    private CallSendOperation(@NonNull SignalServiceCallMessage message) {
      this.message = message;
    }

    @Override
    public @NonNull List<SendMessageResult> sendWithSenderKey(@NonNull SignalServiceMessageSender messageSender,
                                                              @NonNull DistributionId distributionId,
                                                              @NonNull List<SignalServiceAddress> targets,
                                                              @NonNull List<UnidentifiedAccess> access,
                                                              boolean isRecipientUpdate)
        throws NoSessionException, UntrustedIdentityException, InvalidKeyException, IOException, InvalidRegistrationIdException
    {
      return messageSender.sendCallMessage(distributionId, targets, access, message);
    }

    @Override
    public @NonNull List<SendMessageResult> sendLegacy(@NonNull SignalServiceMessageSender messageSender,
                                                       @NonNull List<SignalServiceAddress> targets,
                                                       @NonNull List<Optional<UnidentifiedAccessPair>> access,
                                                       boolean isRecipientUpdate,
                                                       @Nullable PartialSendCompleteListener partialListener,
                                                       @Nullable CancelationSignal cancelationSignal)
        throws IOException
    {
      return messageSender.sendCallMessage(targets, access, message);
    }

    @Override
    public @NonNull ContentHint getContentHint() {
      return ContentHint.IMPLICIT;
    }

    @Override
    public long getSentTimestamp() {
      return message.getTimestamp().get();
    }

    @Override
    public boolean shouldIncludeInMessageLog() {
      return false;
    }

    @Override
    public @NonNull MessageId getRelatedMessageId() {
      throw new UnsupportedOperationException();
    }
  }

  public static class StorySendOperation implements SendOperation {

    private final MessageId                 relatedMessageId;
    private final GroupId                   groupId;
    private final long                      sentTimestamp;
    private final SignalServiceStoryMessage message;

    public StorySendOperation(@NonNull MessageId relatedMessageId, @Nullable GroupId groupId, long sentTimestamp, @NonNull SignalServiceStoryMessage message) {
      this.relatedMessageId = relatedMessageId;
      this.groupId          = groupId;
      this.sentTimestamp    = sentTimestamp;
      this.message          = message;
    }

    @Override
    public @NonNull List<SendMessageResult> sendWithSenderKey(@NonNull SignalServiceMessageSender messageSender,
                                                              @NonNull DistributionId distributionId,
                                                              @NonNull List<SignalServiceAddress> targets,
                                                              @NonNull List<UnidentifiedAccess> access,
                                                              boolean isRecipientUpdate)
        throws NoSessionException, UntrustedIdentityException, InvalidKeyException, IOException, InvalidRegistrationIdException
    {
      return messageSender.sendGroupStory(distributionId, Optional.fromNullable(groupId).transform(GroupId::getDecodedId), targets, access, message, getSentTimestamp());
    }

    @Override
    public @NonNull List<SendMessageResult> sendLegacy(@NonNull SignalServiceMessageSender messageSender,
                                                       @NonNull List<SignalServiceAddress> targets,
                                                       @NonNull List<Optional<UnidentifiedAccessPair>> access,
                                                       boolean isRecipientUpdate,
                                                       @Nullable PartialSendCompleteListener partialListener,
                                                       @Nullable CancelationSignal cancelationSignal)
        throws IOException, UntrustedIdentityException
    {
      return messageSender.sendStory(targets, access, message, getSentTimestamp());
    }

    @Override
    public @NonNull ContentHint getContentHint() {
      return ContentHint.RESENDABLE;
    }

    @Override
    public long getSentTimestamp() {
      return sentTimestamp;
    }

    @Override
    public boolean shouldIncludeInMessageLog() {
      return true;
    }

    @Override
    public @NonNull MessageId getRelatedMessageId() {
      return relatedMessageId;
    }
  }

  private static final class SenderKeyMetricEventListener implements SenderKeyGroupEvents {

    private final long messageId;

    private SenderKeyMetricEventListener(long messageId) {
      this.messageId = messageId;
    }

    @Override
    public void onSenderKeyShared() {
      SignalLocalMetrics.GroupMessageSend.onSenderKeyShared(messageId);
    }

    @Override
    public void onMessageEncrypted() {
      SignalLocalMetrics.GroupMessageSend.onSenderKeyEncrypted(messageId);
    }

    @Override
    public void onMessageSent() {
      SignalLocalMetrics.GroupMessageSend.onSenderKeyMessageSent(messageId);
    }

    @Override
    public void onSyncMessageSent() {
      SignalLocalMetrics.GroupMessageSend.onSenderKeySyncSent(messageId);
    }
  }

  private static final class LegacyMetricEventListener implements LegacyGroupEvents {

    private final long messageId;

    private LegacyMetricEventListener(long messageId) {
      this.messageId = messageId;
    }

    @Override
    public void onMessageSent() {
      SignalLocalMetrics.GroupMessageSend.onLegacyMessageSent(messageId);
    }

    @Override
    public void onSyncMessageSent() {
      SignalLocalMetrics.GroupMessageSend.onLegacySyncFinished(messageId);
    }
  }

  /**
   * Little utility wrapper that lets us get the various different slices of recipient models that we need for different methods.
   */
  private static final class RecipientData {

    private final Map<RecipientId, Optional<UnidentifiedAccessPair>> accessById;
    private final Map<RecipientId, SignalServiceAddress>             addressById;
    private final RecipientAccessList                                accessList;

    RecipientData(@NonNull Context context, @NonNull List<Recipient> recipients) throws IOException {
      this.accessById  = UnidentifiedAccessUtil.getAccessMapFor(context, recipients);
      this.addressById = mapAddresses(context, recipients);
      this.accessList  = new RecipientAccessList(recipients);
    }

    @NonNull SignalServiceAddress getAddress(@NonNull RecipientId id) {
      return Objects.requireNonNull(addressById.get(id));
    }

    @NonNull Optional<UnidentifiedAccessPair> getAccessPair(@NonNull RecipientId id) {
      return Objects.requireNonNull(accessById.get(id));
    }

    @NonNull UnidentifiedAccess requireAccess(@NonNull RecipientId id) {
      return Objects.requireNonNull(accessById.get(id)).get().getTargetUnidentifiedAccess().get();
    }

    @NonNull RecipientId requireRecipientId(@NonNull SignalServiceAddress address) {
      return accessList.requireIdByAddress(address);
    }

    private static @NonNull Map<RecipientId, SignalServiceAddress> mapAddresses(@NonNull Context context, @NonNull List<Recipient> recipients) throws IOException {
      List<SignalServiceAddress> addresses = RecipientUtil.toSignalServiceAddressesFromResolved(context, recipients);

      Iterator<Recipient>            recipientIterator = recipients.iterator();
      Iterator<SignalServiceAddress> addressIterator   = addresses.iterator();

      Map<RecipientId, SignalServiceAddress> map = new HashMap<>(recipients.size());

      while (recipientIterator.hasNext()) {
        map.put(recipientIterator.next().getId(), addressIterator.next());
      }

      return map;
    }
  }
}
