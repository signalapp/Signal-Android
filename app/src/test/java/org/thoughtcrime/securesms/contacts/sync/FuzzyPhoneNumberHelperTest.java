package org.thoughtcrime.securesms.contacts.sync;

import org.junit.Test;
import org.thoughtcrime.securesms.contacts.sync.FuzzyPhoneNumberHelper.InputResult;
import org.thoughtcrime.securesms.contacts.sync.FuzzyPhoneNumberHelper.OutputResult;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.thoughtcrime.securesms.testutil.TestHelpers.mapOf;

public class FuzzyPhoneNumberHelperTest {

  private static final String US_A = "+16108675309";
  private static final String US_B = "+16101234567";

  private static final String MX_A   = "+525512345678";
  private static final String MX_A_1 = "+5215512345678";

  private static final ACI ACI_A = ACI.parseOrThrow("db980097-1e02-452f-9937-899630508705");
  private static final ACI ACI_B = ACI.parseOrThrow("11ccd6de-8fcc-49d6-bb9e-df21ff88bd6f");

  @Test
  public void generateInput_noMxNumbers() {
    InputResult result = FuzzyPhoneNumberHelper.generateInput(setOf(US_A, US_B), setOf(US_A, US_B));

    assertEquals(2, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(US_A, US_B)));
    assertTrue(result.getMapOfOriginalToVariant().isEmpty());
  }

  @Test
  public void generateInput_mxWith1_without1NotStored() {
    InputResult result = FuzzyPhoneNumberHelper.generateInput(setOf(US_A, MX_A_1), setOf(US_A, MX_A_1));

    assertEquals(3, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(US_A, MX_A_1, MX_A)));
    assertEquals(MX_A, result.getMapOfOriginalToVariant().get(MX_A_1));
  }

  @Test
  public void generateInput_mxWith1_without1AlreadyStored() {
    InputResult result = FuzzyPhoneNumberHelper.generateInput(setOf(US_A, MX_A_1), setOf(US_A, MX_A_1, MX_A));

    assertEquals(2, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(US_A, MX_A_1)));
    assertTrue(result.getMapOfOriginalToVariant().isEmpty());
  }

  @Test
  public void generateInput_mxWithout1_with1NotStored() {
    InputResult result = FuzzyPhoneNumberHelper.generateInput(setOf(US_A, MX_A), setOf(US_A, MX_A));

    assertEquals(3, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(US_A, MX_A_1, MX_A)));
    assertEquals(MX_A_1, result.getMapOfOriginalToVariant().get(MX_A));
  }

  @Test
  public void generateInput_mxWithout1_with1AlreadyStored() {
    InputResult result = FuzzyPhoneNumberHelper.generateInput(setOf(US_A, MX_A), setOf(US_A, MX_A_1, MX_A));

    assertEquals(2, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(US_A, MX_A)));
    assertTrue(result.getMapOfOriginalToVariant().isEmpty());
  }

  @Test
  public void generateInput_mxWithAndWithout1_neitherStored() {
    InputResult result = FuzzyPhoneNumberHelper.generateInput(setOf(US_A, MX_A_1, MX_A), setOf(US_A));

    assertEquals(3, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(US_A, MX_A_1, MX_A)));
    assertTrue(result.getMapOfOriginalToVariant().isEmpty());
  }

  @Test
  public void generateInput_mxWithAndWithout1_with1AlreadyStored() {
    InputResult result = FuzzyPhoneNumberHelper.generateInput(setOf(US_A, MX_A_1, MX_A), setOf(US_A, MX_A_1));

    assertEquals(3, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(US_A, MX_A_1, MX_A)));
    assertTrue(result.getMapOfOriginalToVariant().isEmpty());
  }

  @Test
  public void generateInput_mxWithAndWithout1_without1AlreadyStored() {
    InputResult result = FuzzyPhoneNumberHelper.generateInput(setOf(US_A, MX_A_1, MX_A), setOf(US_A, MX_A));

    assertEquals(3, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(US_A, MX_A_1, MX_A)));
    assertTrue(result.getMapOfOriginalToVariant().isEmpty());
  }

  @Test
  public void generateOutput_noMxNumbers() {
    OutputResult<ACI> result = FuzzyPhoneNumberHelper.generateOutput(mapOf(US_A, ACI_A, US_B, ACI_B), new InputResult(setOf(US_A, US_B), Collections.emptyMap()));

    assertEquals(2, result.getNumbers().size());
    assertEquals(ACI_A, result.getNumbers().get(US_A));
    assertEquals(ACI_B, result.getNumbers().get(US_B));
    assertTrue(result.getRewrites().isEmpty());
  }

  @Test
  public void generateOutput_bothMatch_no1To1() {
    OutputResult<ACI> result = FuzzyPhoneNumberHelper.generateOutput(mapOf(MX_A, ACI_A, MX_A_1, ACI_B), new InputResult(setOf(MX_A, MX_A_1), Collections.singletonMap(MX_A, MX_A_1)));

    assertEquals(1, result.getNumbers().size());
    assertEquals(ACI_A, result.getNumbers().get(MX_A));
    assertTrue(result.getRewrites().isEmpty());
  }

  @Test
  public void generateOutput_bothMatch_1toNo1() {
    OutputResult<ACI> result = FuzzyPhoneNumberHelper.generateOutput(mapOf(MX_A, ACI_A, MX_A_1, ACI_B), new InputResult(setOf(MX_A, MX_A_1), Collections.singletonMap(MX_A_1, MX_A)));

    assertEquals(1, result.getNumbers().size());
    assertEquals(ACI_A, result.getNumbers().get(MX_A));
    assertEquals(MX_A, result.getRewrites().get(MX_A_1));
  }

  @Test
  public void generateOutput_no1Match_no1To1() {
    OutputResult<ACI> result = FuzzyPhoneNumberHelper.generateOutput(mapOf(MX_A, ACI_A), new InputResult(setOf(MX_A, MX_A_1), Collections.singletonMap(MX_A, MX_A_1)));

    assertEquals(1, result.getNumbers().size());
    assertEquals(ACI_A, result.getNumbers().get(MX_A));
    assertTrue(result.getRewrites().isEmpty());
  }

  @Test
  public void generateOutput_no1Match_1ToNo1() {
    OutputResult<ACI> result = FuzzyPhoneNumberHelper.generateOutput(mapOf(MX_A, ACI_A), new InputResult(setOf(MX_A, MX_A_1), Collections.singletonMap(MX_A_1, MX_A)));

    assertEquals(1, result.getNumbers().size());
    assertEquals(ACI_A, result.getNumbers().get(MX_A));
    assertEquals(MX_A, result.getRewrites().get(MX_A_1));
  }

  @Test
  public void generateOutput_1Match_1ToNo1() {
    OutputResult<ACI> result = FuzzyPhoneNumberHelper.generateOutput(mapOf(MX_A_1, ACI_A), new InputResult(setOf(MX_A, MX_A_1), Collections.singletonMap(MX_A_1, MX_A)));

    assertEquals(1, result.getNumbers().size());
    assertEquals(ACI_A, result.getNumbers().get(MX_A_1));
    assertTrue(result.getRewrites().isEmpty());
  }

  @Test
  public void generateOutput_1Match_no1To1() {
    OutputResult<ACI> result = FuzzyPhoneNumberHelper.generateOutput(mapOf(MX_A_1, ACI_A), new InputResult(setOf(MX_A, MX_A_1), Collections.singletonMap(MX_A, MX_A_1)));

    assertEquals(1, result.getNumbers().size());
    assertEquals(ACI_A, result.getNumbers().get(MX_A_1));
    assertEquals(MX_A_1, result.getRewrites().get(MX_A));
  }

  private static <E> Set<E> setOf(E... values) {
    //noinspection unchecked
    return new HashSet<>(Arrays.asList(values));
  }
}
