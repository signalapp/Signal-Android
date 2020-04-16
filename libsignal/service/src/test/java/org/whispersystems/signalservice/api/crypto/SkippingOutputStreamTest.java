package org.whispersystems.signalservice.api.crypto;

import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.*;

public class SkippingOutputStreamTest {

  private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

  @Test
  public void givenZeroToSkip_whenIWriteInt_thenIGetIntInOutput() throws Exception {
    // GIVEN
    SkippingOutputStream testSubject = new SkippingOutputStream(0, outputStream);

    // WHEN
    testSubject.write(0);

    // THEN
    assertEquals(1, outputStream.toByteArray().length);
    assertEquals(0, outputStream.toByteArray()[0]);
  }

  @Test
  public void givenOneToSkip_whenIWriteIntTwice_thenIGetSecondIntInOutput() throws Exception {
    // GIVEN
    SkippingOutputStream testSubject = new SkippingOutputStream(1, outputStream);

    // WHEN
    testSubject.write(0);
    testSubject.write(1);

    // THEN
    assertEquals(1, outputStream.toByteArray().length);
    assertEquals(1, outputStream.toByteArray()[0]);
  }

  @Test
  public void givenZeroToSkip_whenIWriteArray_thenIGetArrayInOutput() throws Exception {
    // GIVEN
    byte[]               expected    = new byte[]{1, 2, 3, 4, 5};
    SkippingOutputStream testSubject = new SkippingOutputStream(0, outputStream);

    // WHEN
    testSubject.write(expected);

    // THEN
    assertEquals(expected.length, outputStream.toByteArray().length);
    assertArrayEquals(expected, outputStream.toByteArray());
  }

  @Test
  public void givenNonZeroToSkip_whenIWriteArray_thenIGetEndOfArrayInOutput() throws Exception {
    // GIVEN
    byte[]               expected    = new byte[]{1, 2, 3, 4, 5};
    SkippingOutputStream testSubject = new SkippingOutputStream(3, outputStream);

    // WHEN
    testSubject.write(expected);

    // THEN
    assertEquals(2, outputStream.toByteArray().length);
    assertArrayEquals(new byte[]{4, 5}, outputStream.toByteArray());
  }

  @Test
  public void givenSkipGreaterThanByteArray_whenIWriteArray_thenIGetNoOutput() throws Exception {
    // GIVEN
    byte[]               array       = new byte[]{1, 2, 3, 4, 5};
    SkippingOutputStream testSubject = new SkippingOutputStream(10, outputStream);

    // WHEN
    testSubject.write(array);

    // THEN
    assertEquals(0, outputStream.toByteArray().length);
  }

  @Test
  public void givenZeroToSkip_whenIWriteArrayRange_thenIGetArrayRangeInOutput() throws Exception {
    // GIVEN
    byte[]               expected    = new byte[]{1, 2, 3, 4, 5};
    SkippingOutputStream testSubject = new SkippingOutputStream(0, outputStream);

    // WHEN
    testSubject.write(expected, 1, 3);

    // THEN
    assertEquals(3, outputStream.toByteArray().length);
    assertArrayEquals(new byte[]{2, 3, 4}, outputStream.toByteArray());
  }

  @Test
  public void givenNonZeroToSkip_whenIWriteArrayRange_thenIGetEndOfArrayRangeInOutput() throws Exception {
    // GIVEN
    byte[]               expected    = new byte[]{1, 2, 3, 4, 5};
    SkippingOutputStream testSubject = new SkippingOutputStream(1, outputStream);

    // WHEN
    testSubject.write(expected, 3, 2);

    // THEN
    assertEquals(1, outputStream.toByteArray().length);
    assertArrayEquals(new byte[]{5}, outputStream.toByteArray());
  }

  @Test
  public void givenSkipGreaterThanByteArrayRange_whenIWriteArrayRange_thenIGetNoOutput() throws Exception {
    // GIVEN
    byte[]               array       = new byte[]{1, 2, 3, 4, 5};
    SkippingOutputStream testSubject = new SkippingOutputStream(10, outputStream);

    // WHEN
    testSubject.write(array, 3, 2);

    // THEN
    assertEquals(0, outputStream.toByteArray().length);
  }

  @Test
  public void givenSkipGreaterThanByteArrayRange_whenIWriteArrayRangeTwice_thenIGetExpectedOutput() throws Exception {
    // GIVEN
    byte[]               array       = new byte[]{1, 2, 3, 4, 5};
    SkippingOutputStream testSubject = new SkippingOutputStream(3, outputStream);

    // WHEN
    testSubject.write(array, 3, 2);
    testSubject.write(array, 3, 2);

    // THEN
    assertEquals(1, outputStream.toByteArray().length);
    assertArrayEquals(new byte[]{5}, outputStream.toByteArray());
  }
}