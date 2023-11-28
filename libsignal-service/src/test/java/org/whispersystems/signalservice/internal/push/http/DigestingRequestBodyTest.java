/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push.http;

import org.junit.Test;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.internal.crypto.AttachmentDigest;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.ByteArrayInputStream;

import okio.Buffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DigestingRequestBodyTest {

  private static final int CONTENT_LENGTH = 70000;
  private static final int TOTAL_LENGTH   = (int) AttachmentCipherStreamUtil.getCiphertextLength(CONTENT_LENGTH);

  private final byte[] attachmentKey = Util.getSecretBytes(64);
  private final byte[] attachmentIV  = Util.getSecretBytes(16);
  private final byte[] input         = Util.getSecretBytes(CONTENT_LENGTH);

  private final OutputStreamFactory outputStreamFactory = new AttachmentCipherOutputStreamFactory(attachmentKey, attachmentIV);

  @Test
  public void givenSameKeyAndIV_whenIWriteToBuffer_thenIExpectSameDigests() throws Exception {
    DigestingRequestBody fromStart  = getBody(0);
    DigestingRequestBody fromMiddle = getBody(CONTENT_LENGTH / 2);

    try (Buffer buffer = new Buffer()) {
      fromStart.writeTo(buffer);
    }

    try (Buffer buffer = new Buffer()) {
      fromMiddle.writeTo(buffer);
    }

    final AttachmentDigest fullResult = fromStart.getAttachmentDigest();
    assertNotNull(fullResult);

    final AttachmentDigest partialResult = fromMiddle.getAttachmentDigest();
    assertNotNull(partialResult);

    assertArrayEquals(fullResult.getDigest(), partialResult.getDigest());
    assertArrayEquals(fullResult.getIncrementalDigest(), partialResult.getIncrementalDigest());
  }

  @Test
  public void givenSameKeyAndIV_whenIWriteToBuffer_thenIExpectSameContents() throws Exception {
    DigestingRequestBody fromStart  = getBody(0);
    DigestingRequestBody fromMiddle = getBody(CONTENT_LENGTH / 2);

    byte[] cipher1;

    try (Buffer buffer = new Buffer()) {
      fromStart.writeTo(buffer);

      cipher1 = buffer.readByteArray();
    }

    byte[] cipher2;

    try (Buffer buffer = new Buffer()) {
      fromMiddle.writeTo(buffer);

      cipher2 = buffer.readByteArray();
    }

    assertEquals(cipher1.length, TOTAL_LENGTH);
    assertEquals(cipher2.length, TOTAL_LENGTH - (CONTENT_LENGTH / 2));

    for (int i = 0; i < cipher2.length; i++) {
      assertEquals(cipher2[i], cipher1[i + (CONTENT_LENGTH / 2)]);
    }
  }

  private DigestingRequestBody getBody(long contentStart) {
    return new DigestingRequestBody(new ByteArrayInputStream(input), outputStreamFactory, "application/octet", CONTENT_LENGTH, false, new SignalServiceAttachment.ProgressListener() {
      @Override
      public void onAttachmentProgress(long total, long progress) {
        // no-op
      }

      @Override public boolean shouldCancel() {
        return false;
      }
    }, () -> false, contentStart);
  }
}