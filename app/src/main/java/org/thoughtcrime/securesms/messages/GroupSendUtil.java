package org.thoughtcrime.securesms.messages;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.SenderKeyUtil;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.CancelationException;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.push.http.CancelationSignal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class GroupSendUtil {

  private static final String TAG = Log.tag(GroupSendUtil.class);

  private static final long MAX_KEY_AGE = TimeUnit.DAYS.toMillis(30);

  private GroupSendUtil() {}


  /**
   * Handles all of the logic of sending to a group. Will do sender key sends and legacy 1:1 sends as-needed, and give you back a list of
   * {@link SendMessageResult}s just like we're used to.
   *
   * @param isRecipientUpdate True if you've already sent this message to some recipients in the past, otherwise false.
   */
  @WorkerThread
  public static List<SendMessageResult> sendDataMessage(@NonNull Context context,
                                                        @NonNull GroupId.V2 groupId,
                                                        @NonNull List<Recipient> allTargets,
                                                        boolean isRecipientUpdate,
                                                        ContentHint contentHint,
                                                        @NonNull SignalServiceDataMessage message)
      throws IOException, UntrustedIdentityException
  {
    return sendMessage(context, groupId, allTargets, isRecipientUpdate, new DataSendOperation(message, contentHint), null);
  }

  /**
   * Handles all of the logic of sending to a group. Will do sender key sends and legacy 1:1 sends as-needed, and give you back a list of
   * {@link SendMessageResult}s just like we're used to.
   */
  @WorkerThread
  public static List<SendMessageResult> sendTypingMessage(@NonNull Context context,
                                                          @NonNull GroupId.V2 groupId,
                                                          @NonNull List<Recipient> allTargets,
                                                          @NonNull SignalServiceTypingMessage message,
                                                          @Nullable CancelationSignal cancelationSignal)
      throws IOException, UntrustedIdentityException
  {
    return sendMessage(context, groupId, allTargets, false, new TypingSendOperation(message), cancelationSignal);
  }

  /**
   * Handles all of the logic of sending to a group. Will do sender key sends and legacy 1:1 sends as-needed, and give you back a list of
   * {@link SendMessageResult}s just like we're used to.
   *
   * @param isRecipientUpdate True if you've already sent this message to some recipients in the past, otherwise false.
   */
  @WorkerThread
  private static List<SendMessageResult> sendMessage(@NonNull Context context,
                                                     @NonNull GroupId.V2 groupId,
                                                     @NonNull List<Recipient> allTargets,
                                                     boolean isRecipientUpdate,
                                                     @NonNull SendOperation sendOperation,
                                                     @Nullable CancelationSignal cancelationSignal)
      throws IOException, UntrustedIdentityException
  {
    RecipientData recipients = new RecipientData(context, allTargets);

    List<Recipient> senderKeyTargets = new LinkedList<>();
    List<Recipient> legacyTargets    = new LinkedList<>();

    for (Recipient recipient : allTargets) {
      Optional<UnidentifiedAccessPair> access = recipients.getAccessPair(recipient.getId());

      if (recipient.getSenderKeyCapability() == Recipient.Capability.SUPPORTED &&
          recipient.hasUuid()                                                  &&
          access.isPresent()                                                   &&
          access.get().getTargetUnidentifiedAccess().isPresent())
      {
        senderKeyTargets.add(recipient);
      } else {
        legacyTargets.add(recipient);
      }
    }

    if (FeatureFlags.senderKey()) {
      if (Recipient.self().getSenderKeyCapability() != Recipient.Capability.SUPPORTED) {
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
    } else {
      Log.i(TAG, "Feature flag disabled. Using legacy.");
      legacyTargets.addAll(senderKeyTargets);
      senderKeyTargets.clear();
    }

    List<SendMessageResult>    allResults    = new ArrayList<>(allTargets.size());
    SignalServiceMessageSender messageSender  = ApplicationDependencies.getSignalServiceMessageSender();
    DistributionId             distributionId = DatabaseFactory.getGroupDatabase(context).getOrCreateDistributionId(groupId);

    if (senderKeyTargets.size() > 0) {
      long keyCreateTime = SenderKeyUtil.getCreateTimeForOurKey(context, distributionId);
      long keyAge        = System.currentTimeMillis() - keyCreateTime;

      if (keyCreateTime != -1 && keyAge > MAX_KEY_AGE) {
        Log.w(TAG, "Key is " + (keyAge) + " ms old (~" + TimeUnit.MILLISECONDS.toDays(keyAge) + " days). Rotating.");
        SenderKeyUtil.rotateOurKey(context, distributionId);
      }

      try {
        List<SignalServiceAddress> targets = senderKeyTargets.stream().map(r -> recipients.getAddress(r.getId())).collect(Collectors.toList());
        List<UnidentifiedAccess>   access  = senderKeyTargets.stream().map(r -> recipients.requireAccess(r.getId())).collect(Collectors.toList());
        List<SendMessageResult>    results = sendOperation.sendWithSenderKey(messageSender, distributionId, targets, access, isRecipientUpdate);

        allResults.addAll(results);

        int successCount = (int) results.stream().filter(SendMessageResult::isSuccess).count();
        Log.d(TAG, "Successfully sent using sender key to " + successCount + "/" + targets.size() + " sender key targets.");
      } catch (NoSessionException e) {
        Log.w(TAG, "No session. Falling back to legacy sends.", e);
        legacyTargets.addAll(senderKeyTargets);
      } catch (InvalidKeyException e) {
        Log.w(TAG, "Invalid Key. Falling back to legacy sends.", e);
        legacyTargets.addAll(senderKeyTargets);
      }
    }

    if (cancelationSignal != null && cancelationSignal.isCanceled()) {
      throw new CancelationException();
    }

    if (legacyTargets.size() > 0) {
      Log.i(TAG, "Need to do " + legacyTargets.size() + " legacy sends.");

      List<SignalServiceAddress>             targets         = legacyTargets.stream().map(r -> recipients.getAddress(r.getId())).collect(Collectors.toList());
      List<Optional<UnidentifiedAccessPair>> access          = legacyTargets.stream().map(r -> recipients.getAccessPair(r.getId())).collect(Collectors.toList());
      boolean                                recipientUpdate = isRecipientUpdate || allResults.size() > 0;

      List<SendMessageResult> results = sendOperation.sendLegacy(messageSender, targets, access, recipientUpdate, cancelationSignal);

      allResults.addAll(results);

      int successCount = (int) results.stream().filter(SendMessageResult::isSuccess).count();
      Log.d(TAG, "Successfully using 1:1 to " + successCount + "/" + targets.size() + " legacy targets.");
    }

    return allResults;
  }

  /** Abstraction layer to handle the different types of message send operations we can do */
  private interface SendOperation {
    @NonNull List<SendMessageResult> sendWithSenderKey(@NonNull SignalServiceMessageSender messageSender,
                                                       @NonNull DistributionId distributionId,
                                                       @NonNull List<SignalServiceAddress> targets,
                                                       @NonNull List<UnidentifiedAccess> access,
                                                       boolean isRecipientUpdate)
        throws NoSessionException, UntrustedIdentityException, InvalidKeyException, IOException;

    @NonNull List<SendMessageResult> sendLegacy(@NonNull SignalServiceMessageSender messageSender,
                                                @NonNull List<SignalServiceAddress> targets,
                                                @NonNull List<Optional<UnidentifiedAccessPair>> access,
                                                boolean isRecipientUpdate,
                                                @Nullable CancelationSignal cancelationSignal)
        throws IOException, UntrustedIdentityException;
  }

  private static class DataSendOperation implements SendOperation {
    private final SignalServiceDataMessage message;
    private final ContentHint              contentHint;

    private DataSendOperation(@NonNull SignalServiceDataMessage message, @NonNull ContentHint contentHint) {
      this.message     = message;
      this.contentHint = contentHint;
    }

    @Override
    public @NonNull List<SendMessageResult> sendWithSenderKey(@NonNull SignalServiceMessageSender messageSender,
                                                              @NonNull DistributionId distributionId,
                                                              @NonNull List<SignalServiceAddress> targets,
                                                              @NonNull List<UnidentifiedAccess> access,
                                                              boolean isRecipientUpdate)
        throws NoSessionException, UntrustedIdentityException, InvalidKeyException, IOException
    {
      return messageSender.sendGroupDataMessage(distributionId, targets, access, isRecipientUpdate, contentHint, message);
    }

    @Override
    public @NonNull List<SendMessageResult> sendLegacy(@NonNull SignalServiceMessageSender messageSender,
                                                       @NonNull List<SignalServiceAddress> targets,
                                                       @NonNull List<Optional<UnidentifiedAccessPair>> access,
                                                       boolean isRecipientUpdate,
                                                       @Nullable CancelationSignal cancelationSignal)
        throws IOException, UntrustedIdentityException
    {
      return messageSender.sendDataMessage(targets, access, isRecipientUpdate, contentHint, message);
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
        throws NoSessionException, UntrustedIdentityException, InvalidKeyException, IOException
    {
      messageSender.sendGroupTyping(distributionId, targets, access, message);
      return targets.stream().map(a -> SendMessageResult.success(a, true, false, -1)).collect(Collectors.toList());
    }

    @Override
    public @NonNull List<SendMessageResult> sendLegacy(@NonNull SignalServiceMessageSender messageSender,
                                                       @NonNull List<SignalServiceAddress> targets,
                                                       @NonNull List<Optional<UnidentifiedAccessPair>> access,
                                                       boolean isRecipientUpdate,
                                                       @Nullable CancelationSignal cancelationSignal)
        throws IOException
    {
      messageSender.sendTyping(targets, access, message, cancelationSignal);
      return targets.stream().map(a -> SendMessageResult.success(a, true, false, -1)).collect(Collectors.toList());
    }
  }

  /**
   * Little utility wrapper that lets us get the various different slices of recipient models that we need for different methods.
   */
  private static final class RecipientData {

    private final Map<RecipientId, Optional<UnidentifiedAccessPair>> accessById;
    private final Map<RecipientId, SignalServiceAddress>             addressById;

    RecipientData(@NonNull Context context, @NonNull List<Recipient> recipients) throws IOException {
      this.accessById  = UnidentifiedAccessUtil.getAccessMapFor(context, recipients);
      this.addressById = mapAddresses(context, recipients);
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
