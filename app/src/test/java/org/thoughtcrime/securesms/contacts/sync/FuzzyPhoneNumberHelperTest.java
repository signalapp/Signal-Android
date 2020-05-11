package org.thoughtcrime.securesms.contacts.sync;

import org.junit.Test;
import org.thoughtcrime.securesms.contacts.sync.FuzzyPhoneNumberHelper.InputResult;
import org.thoughtcrime.securesms.contacts.sync.FuzzyPhoneNumberHelper.OutputResult;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import edu.emory.mathcs.backport.java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FuzzyPhoneNumberHelperTest {

  private static final String US_A = "+16108675309";
  private static final String US_B = "+16101234567";

  private static final String MX_A   = "+525512345678";
  private static final String MX_A_1 = "+5215512345678";

  @Test
  public void generateInput_noMxNumbers() {
    InputResult result = FuzzyPhoneNumberHelper.generateInput(setOf(US_A, US_B), setOf(US_A, US_B));

    assertEquals(2, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(US_A, US_B)));
    assertTrue(result.getFuzzies().isEmpty());
  }

  @Test
  public void generateInput_mxWith1_without1NotStored() {
    InputResult result = FuzzyPhoneNumberHelper.generateInput(setOf(US_A, MX_A_1), setOf(US_A, MX_A_1));

    assertEquals(3, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(US_A, MX_A_1, MX_A)));
    assertEquals(MX_A, result.getFuzzies().get(MX_A_1));
  }

  @Test
  public void generateInput_mxWith1_without1AlreadyStored() {
    InputResult result = FuzzyPhoneNumberHelper.generateInput(setOf(US_A, MX_A_1), setOf(US_A, MX_A_1, MX_A));

    assertEquals(2, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(US_A, MX_A_1)));
    assertTrue(result.getFuzzies().isEmpty());
  }

  @Test
  public void generateInput_mxWithout1_with1NotStored() {
    InputResult result = FuzzyPhoneNumberHelper.generateInput(setOf(US_A, MX_A), setOf(US_A, MX_A));

    assertEquals(3, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(US_A, MX_A_1, MX_A)));
    assertEquals(MX_A_1, result.getFuzzies().get(MX_A));
  }

  @Test
  public void generateInput_mxWithout1_with1AlreadyStored() {
    InputResult result = FuzzyPhoneNumberHelper.generateInput(setOf(US_A, MX_A), setOf(US_A, MX_A_1, MX_A));

    assertEquals(2, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(US_A, MX_A)));
    assertTrue(result.getFuzzies().isEmpty());
  }

  @Test
  public void generateInput_mxWithAndWithout1_neitherStored() {
    InputResult result = FuzzyPhoneNumberHelper.generateInput(setOf(US_A, MX_A_1, MX_A), setOf(US_A));

    assertEquals(3, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(US_A, MX_A_1, MX_A)));
    assertTrue(result.getFuzzies().isEmpty());
  }

  @Test
  public void generateInput_mxWithAndWithout1_with1AlreadyStored() {
    InputResult result = FuzzyPhoneNumberHelper.generateInput(setOf(US_A, MX_A_1, MX_A), setOf(US_A, MX_A_1));

    assertEquals(3, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(US_A, MX_A_1, MX_A)));
    assertTrue(result.getFuzzies().isEmpty());
  }

  @Test
  public void generateInput_mxWithAndWithout1_without1AlreadyStored() {
    InputResult result = FuzzyPhoneNumberHelper.generateInput(setOf(US_A, MX_A_1, MX_A), setOf(US_A, MX_A));

    assertEquals(3, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(US_A, MX_A_1, MX_A)));
    assertTrue(result.getFuzzies().isEmpty());
  }

  @Test
  public void generateOutput_noMxNumbers() {
    OutputResult result = FuzzyPhoneNumberHelper.generateOutput(setOf(US_A, US_B), new InputResult(setOf(US_A, US_B), Collections.emptyMap()));

    assertEquals(2, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(US_A, US_B)));
    assertTrue(result.getRewrites().isEmpty());
  }

  @Test
  public void generateOutput_bothMatch_no1To1() {
    OutputResult result = FuzzyPhoneNumberHelper.generateOutput(setOf(MX_A, MX_A_1), new InputResult(setOf(MX_A, MX_A_1), Collections.singletonMap(MX_A, MX_A_1)));

    assertEquals(1, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(MX_A)));
    assertTrue(result.getRewrites().isEmpty());
  }

  @Test
  public void generateOutput_bothMatch_1toNo1() {
    OutputResult result = FuzzyPhoneNumberHelper.generateOutput(setOf(MX_A, MX_A_1), new InputResult(setOf(MX_A, MX_A_1), Collections.singletonMap(MX_A_1, MX_A)));

    assertEquals(1, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(MX_A)));
    assertEquals(MX_A, result.getRewrites().get(MX_A_1));
  }

  @Test
  public void generateOutput_no1Match_no1To1() {
    OutputResult result = FuzzyPhoneNumberHelper.generateOutput(setOf(MX_A), new InputResult(setOf(MX_A, MX_A_1), Collections.singletonMap(MX_A, MX_A_1)));

    assertEquals(1, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(MX_A)));
    assertTrue(result.getRewrites().isEmpty());
  }

  @Test
  public void generateOutput_no1Match_1ToNo1() {
    OutputResult result = FuzzyPhoneNumberHelper.generateOutput(setOf(MX_A), new InputResult(setOf(MX_A, MX_A_1), Collections.singletonMap(MX_A_1, MX_A)));

    assertEquals(1, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(MX_A)));
    assertEquals(MX_A, result.getRewrites().get(MX_A_1));
  }

  @Test
  public void generateOutput_1Match_1ToNo1() {
    OutputResult result = FuzzyPhoneNumberHelper.generateOutput(setOf(MX_A_1), new InputResult(setOf(MX_A, MX_A_1), Collections.singletonMap(MX_A_1, MX_A)));

    assertEquals(1, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(MX_A_1)));
    assertTrue(result.getRewrites().isEmpty());
  }

  @Test
  public void generateOutput_1Match_no1To1() {
    OutputResult result = FuzzyPhoneNumberHelper.generateOutput(setOf(MX_A_1), new InputResult(setOf(MX_A, MX_A_1), Collections.singletonMap(MX_A, MX_A_1)));

    assertEquals(1, result.getNumbers().size());
    assertTrue(result.getNumbers().containsAll(setOf(MX_A_1)));
    assertEquals(MX_A_1, result.getRewrites().get(MX_A));
  }


  private static <E> Set<E> setOf(E... values) {
    //noinspection unchecked
    return new HashSet<>(Arrays.asList(values));
  }
}
