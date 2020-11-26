/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 * <p>
 * Licensed according to the LICENSE file in this repository.
 */
package org.session.libsignal.libsignal.protocol;

public interface CiphertextMessage {

    public static final int CURRENT_VERSION = 3;

    public static final int WHISPER_TYPE = 2;
    public static final int PREKEY_TYPE = 3;
    public static final int SENDERKEY_TYPE = 4;
    public static final int SENDERKEY_DISTRIBUTION_TYPE = 5;
    public static final int CLOSED_GROUP_CIPHERTEXT = 6;
    public static final int FALLBACK_MESSAGE_TYPE = 999; // Loki

    // This should be the worst case (worse than V2).  So not always accurate, but good enough for padding.
    public static final int ENCRYPTED_MESSAGE_OVERHEAD = 53;

    public byte[] serialize();

    public int getType();

}
