package org.whispersystems.signalservice.internal.registrationpin;

import org.junit.Test;

public final class PinStretchFailureTest {

  @Test(expected = InvalidPinException.class)
  public void non_numeric_pin() throws InvalidPinException {
    PinStretcher.stretchPin("A");
  }

  @Test(expected = InvalidPinException.class)
  public void empty() throws InvalidPinException {
    PinStretcher.stretchPin("");
  }

  @Test(expected = InvalidPinException.class)
  public void too_few_digits() throws InvalidPinException {
    PinStretcher.stretchPin("123");
  }

  @Test(expected = AssertionError.class)
  public void pin_key_2_too_short() throws InvalidPinException {
    PinStretcher.stretchPin("0000").withPinKey2(new byte[31]);
  }

  @Test(expected = AssertionError.class)
  public void pin_key_2_too_long() throws InvalidPinException {
    PinStretcher.stretchPin("0000").withPinKey2(new byte[33]);
  }
}
