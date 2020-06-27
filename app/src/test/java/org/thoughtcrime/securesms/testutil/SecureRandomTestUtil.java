package org.thoughtcrime.securesms.testutil;

import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.security.SecureRandom;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public final class SecureRandomTestUtil {

  private SecureRandomTestUtil() {
  }

  /**
   * Creates a {@link SecureRandom} that returns exactly the {@param returnValue} the first time
   * its {@link SecureRandom#nextBytes(byte[])}} method is called.
   * <p>
   * Any attempt to call with the incorrect length, or a second time will fail.
   */
  public static SecureRandom mockRandom(byte[] returnValue) {
    SecureRandom           mock     = mock(SecureRandom.class);
    ArgumentCaptor<byte[]> argument = ArgumentCaptor.forClass(byte[].class);

    doAnswer(new Answer<Void>() {

      private int count;

      @Override
      public Void answer(InvocationOnMock invocation) {
        assertEquals("SecureRandom Mock: nextBytes only expected to be called once", 1, ++count);

        byte[] output = argument.getValue();

        assertEquals("SecureRandom Mock: nextBytes byte[] length requested does not match byte[] setup", returnValue.length, output.length);

        System.arraycopy(returnValue, 0, output, 0, returnValue.length);

        return null;
      }
    }).when(mock).nextBytes(argument.capture());

    return mock;
  }
}
