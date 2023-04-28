package org.thoughtcrime.securesms.payments;

import android.app.Application;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.whispersystems.signalservice.api.payments.Currency;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public class MoneyViewTest {

  private final Currency currency = Currency.fromCodeAndPrecision("MOB", 12);

  private MoneyView testSubject;

  @Before
  public void setUp() {
    testSubject = new MoneyView(ApplicationProvider.getApplicationContext());
  }

  @Test
  public void given1AndMOB_whenISetMoney_thenIExpect1MOB() {
    testSubject.setMoney("1", currency);

    String formatted = testSubject.getText().toString();

    assertEquals("1MOB", formatted);
  }

  @Test
  public void givenTrailingDecimal_whenISetMoney_thenIExpectTrailingDecimal() {
    testSubject.setMoney("1.", currency);

    String formatted = testSubject.getText().toString();

    assertEquals("1.MOB", formatted);
  }

  @Test
  public void givenNoTrailingDecimal_whenISetMoney_thenIExpectNoTrailingDecimal() {
    testSubject.setMoney("1.0", currency);

    String formatted = testSubject.getText().toString();

    assertEquals("1.0MOB", formatted);
  }

  @Test
  public void givenLongNoTrailingDecimal_whenISetMoney_thenIExpectNoTrailingDecimal() {
    testSubject.setMoney("1.00000000000", currency);

    String formatted = testSubject.getText().toString();

    assertEquals("1.00000000000MOB", formatted);
  }

  @Test
  public void givenDecimalWithTrailingZero_whenISetMoney_thenIExpectDecimalWithTrailingZero() {
    testSubject.setMoney("1.230", currency);

    String formatted = testSubject.getText().toString();

    assertEquals("1.230MOB", formatted);
  }
}
