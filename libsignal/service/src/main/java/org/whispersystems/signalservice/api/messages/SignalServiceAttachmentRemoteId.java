package org.whispersystems.signalservice.api.messages;

import org.signal.libsignal.metadata.ProtocolInvalidMessageException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.AttachmentPointer;

/**
 * Represents a signal service attachment identifier. This can be either a CDN key or a long, but
 * not both at once. Attachments V2 used a long as an attachment identifier. This lacks sufficient
 * entropy to reduce the likelihood of any two uploads going to the same location within a 30-day
 * window. Attachments V3 uses an opaque string as an attachment identifier which provides more
 * flexibility in the amount of entropy present.
 */
public final class SignalServiceAttachmentRemoteId {
    private final Optional<Long> v2;
    private final Optional<String> v3;

    public SignalServiceAttachmentRemoteId(long v2) {
        this.v2 = Optional.of(v2);
        this.v3 = Optional.absent();
    }

    public SignalServiceAttachmentRemoteId(String v3) {
        this.v2 = Optional.absent();
        this.v3 = Optional.of(v3);
    }

    public Optional<Long> getV2() {
        return v2;
    }

    public Optional<String> getV3() {
        return v3;
    }

    @Override
    public String toString() {
        if (v2.isPresent()) {
            return v2.get().toString();
        } else {
            return v3.get();
        }
    }

    public static SignalServiceAttachmentRemoteId from(AttachmentPointer attachmentPointer) throws ProtocolInvalidMessageException {
        switch (attachmentPointer.getAttachmentIdentifierCase()) {
            case CDNID:
                return new SignalServiceAttachmentRemoteId(attachmentPointer.getCdnId());
            case CDNKEY:
                return new SignalServiceAttachmentRemoteId(attachmentPointer.getCdnKey());
            case ATTACHMENTIDENTIFIER_NOT_SET:
                throw new ProtocolInvalidMessageException(new InvalidMessageException("AttachmentPointer CDN location not set"), null, 0);
        }
        return null;
    }

    /**
     * Guesses that strings which contain values parseable to {@code long} should use an id-based
     * CDN path. Otherwise, use key-based CDN path.
     */
    public static SignalServiceAttachmentRemoteId from(String string) {
        try {
            return new SignalServiceAttachmentRemoteId(Long.parseLong(string));
        } catch (NumberFormatException e) {
            return new SignalServiceAttachmentRemoteId(string);
        }
    }
}
