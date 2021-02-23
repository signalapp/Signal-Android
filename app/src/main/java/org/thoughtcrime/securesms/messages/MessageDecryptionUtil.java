package org.thoughtcrime.securesms.messages;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.metadata.InvalidMetadataMessageException;
import org.signal.libsignal.metadata.InvalidMetadataVersionException;
import org.signal.libsignal.metadata.ProtocolDuplicateMessageException;
import org.signal.libsignal.metadata.ProtocolException;
import org.signal.libsignal.metadata.ProtocolInvalidKeyException;
import org.signal.libsignal.metadata.ProtocolInvalidKeyIdException;
import org.signal.libsignal.metadata.ProtocolInvalidMessageException;
import org.signal.libsignal.metadata.ProtocolInvalidVersionException;
import org.signal.libsignal.metadata.ProtocolLegacyMessageException;
import org.signal.libsignal.metadata.ProtocolNoSessionException;
import org.signal.libsignal.metadata.ProtocolUntrustedIdentityException;
import org.signal.libsignal.metadata.SelfSendException;
import org.thoughtcrime.securesms.crypto.DatabaseSessionLock;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.crypto.storage.SignalProtocolStoreImpl;
import org.thoughtcrime.securesms.groups.BadGroupIdException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobs.AutomaticSessionResetJob;
import org.thoughtcrime.securesms.jobs.RefreshPreKeysJob;
import org.thoughtcrime.securesms.messages.MessageContentProcessor.ExceptionMetadata;
import org.thoughtcrime.securesms.messages.MessageContentProcessor.MessageState;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.push.UnsupportedDataMessageException;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Handles taking an encrypted {@link SignalServiceEnvelope} and turning it into a plaintext model.
 */
public final class MessageDecryptionUtil {

  private static final String TAG = Log.tag(MessageDecryptionUtil.class);

  private MessageDecryptionUtil() {}

  /**
   * Takes a {@link SignalServiceEnvelope} and returns a {@link DecryptionResult}, which has either
   * a plaintext {@link SignalServiceContent} or information about an error that happened.
   *
   * Excluding the data updated in our protocol stores that results from decrypting a message, this
   * method is side-effect free, preferring to return the decryption results to be handled by the
   * caller.
   */
  public static @NonNull DecryptionResult decrypt(@NonNull Context context, @NonNull SignalServiceEnvelope envelope) {
    SignalProtocolStore  axolotlStore = new SignalProtocolStoreImpl(context);
    SignalServiceAddress localAddress = new SignalServiceAddress(Optional.of(TextSecurePreferences.getLocalUuid(context)), Optional.of(TextSecurePreferences.getLocalNumber(context)));
    SignalServiceCipher  cipher       = new SignalServiceCipher(localAddress, axolotlStore, DatabaseSessionLock.INSTANCE, UnidentifiedAccessUtil.getCertificateValidator());
    List<Job>            jobs         = new LinkedList<>();

    if (envelope.isPreKeySignalMessage()) {
      jobs.add(new RefreshPreKeysJob());
    }

    try {
      try {
        return DecryptionResult.forSuccess(cipher.decrypt(envelope), jobs);
      } catch (ProtocolInvalidVersionException e) {
        Log.w(TAG, String.valueOf(envelope.getTimestamp()), e);
        return DecryptionResult.forError(MessageState.INVALID_VERSION, toExceptionMetadata(e), jobs);

      } catch (ProtocolInvalidMessageException | ProtocolInvalidKeyIdException | ProtocolInvalidKeyException | ProtocolUntrustedIdentityException | ProtocolNoSessionException e) {
        Log.w(TAG, String.valueOf(envelope.getTimestamp()), e);
        jobs.add(new AutomaticSessionResetJob(Recipient.external(context, e.getSender()).getId(), e.getSenderDevice(), envelope.getTimestamp()));
        return DecryptionResult.forNoop(jobs);
      } catch (ProtocolLegacyMessageException e) {
        Log.w(TAG, String.valueOf(envelope.getTimestamp()), e);
        return DecryptionResult.forError(MessageState.LEGACY_MESSAGE, toExceptionMetadata(e), jobs);
      } catch (ProtocolDuplicateMessageException e) {
        Log.w(TAG, String.valueOf(envelope.getTimestamp()), e);
        return DecryptionResult.forError(MessageState.DUPLICATE_MESSAGE, toExceptionMetadata(e), jobs);
      } catch (InvalidMetadataVersionException | InvalidMetadataMessageException e) {
        Log.w(TAG, String.valueOf(envelope.getTimestamp()), e);
        return DecryptionResult.forNoop(jobs);
      } catch (SelfSendException e) {
        Log.i(TAG, "Dropping UD message from self.");
        return DecryptionResult.forNoop(jobs);
      } catch (UnsupportedDataMessageException e) {
        Log.w(TAG, String.valueOf(envelope.getTimestamp()), e);
        return DecryptionResult.forError(MessageState.UNSUPPORTED_DATA_MESSAGE, toExceptionMetadata(e), jobs);
      }
    } catch (NoSenderException e) {
      Log.w(TAG, "Invalid message, but no sender info!");
      return DecryptionResult.forNoop(jobs);
    }
  }

  private static ExceptionMetadata toExceptionMetadata(@NonNull UnsupportedDataMessageException e)
      throws NoSenderException
  {
    String sender = e.getSender();

    if (sender == null) throw new NoSenderException();

    GroupId groupId = null;

    if (e.getGroup().isPresent()) {
      try {
        groupId = GroupUtil.idFromGroupContext(e.getGroup().get());
      } catch (BadGroupIdException ex) {
        Log.w(TAG, "Bad group id found in unsupported data message", ex);
      }
    }

    return new ExceptionMetadata(sender, e.getSenderDevice(), groupId);
  }

  private static ExceptionMetadata toExceptionMetadata(@NonNull ProtocolException e) throws NoSenderException {
    String sender = e.getSender();

    if (sender == null) throw new NoSenderException();

    return new ExceptionMetadata(sender, e.getSenderDevice());
  }

  private static class NoSenderException extends Exception {}

  public static class DecryptionResult {
    private final @NonNull  MessageState         state;
    private final @Nullable SignalServiceContent content;
    private final @Nullable ExceptionMetadata    exception;
    private final @NonNull  List<Job>            jobs;

    static @NonNull DecryptionResult forSuccess(@NonNull SignalServiceContent content, @NonNull List<Job> jobs) {
      return new DecryptionResult(MessageState.DECRYPTED_OK, content, null, jobs);
    }

    static @NonNull DecryptionResult forError(@NonNull MessageState messageState,
                                              @NonNull ExceptionMetadata exception,
                                              @NonNull List<Job> jobs)
    {
      return new DecryptionResult(messageState, null, exception, jobs);
    }

    static @NonNull DecryptionResult forNoop(@NonNull List<Job> jobs) {
      return new DecryptionResult(MessageState.NOOP, null, null, jobs);
    }

    private DecryptionResult(@NonNull MessageState state,
                             @Nullable SignalServiceContent content,
                             @Nullable ExceptionMetadata exception,
                             @NonNull List<Job> jobs)
    {
      this.state     = state;
      this.content   = content;
      this.exception = exception;
      this.jobs      = jobs;
    }

    public @NonNull MessageState getState() {
      return state;
    }

    public @Nullable SignalServiceContent getContent() {
      return content;
    }

    public @Nullable ExceptionMetadata getException() {
      return exception;
    }

    public @NonNull List<Job> getJobs() {
      return jobs;
    }
  }
}
