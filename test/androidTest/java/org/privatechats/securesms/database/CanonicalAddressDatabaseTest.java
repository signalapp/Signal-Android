package org.privatechats.securesms.database;

import org.privatechats.securesms.TextSecureTestCase;

import static org.assertj.core.api.Assertions.assertThat;

public class CanonicalAddressDatabaseTest extends TextSecureTestCase {
  private static final String AMBIGUOUS_NUMBER = "222-3333";
  private static final String SPECIFIC_NUMBER  = "+49 444 222 3333";
  private static final String EMAIL            = "a@b.fom";
  private static final String SIMILAR_EMAIL    = "a@b.com";
  private static final String GROUP            = "__textsecure_group__!000111222333";
  private static final String SIMILAR_GROUP    = "__textsecure_group__!100111222333";
  private static final String ALPHA            = "T-Mobile";
  private static final String SIMILAR_ALPHA    = "T-Mobila";

  private CanonicalAddressDatabase db;

  public void setUp() throws Exception {
    super.setUp();
    this.db = CanonicalAddressDatabase.getInstance(getInstrumentation().getTargetContext());
  }

  public void tearDown() throws Exception {

  }

  /**
   * Throw two equivalent numbers (one without locale info, one with full info) at the canonical
   * address db and see that the caching and DB operations work properly in revealing the right
   * addresses. This is run twice to ensure cache logic is hit.
   *
   * @throws Exception
   */
  public void testNumberAddressUpdates() throws Exception {
    final long id = db.getCanonicalAddressId(AMBIGUOUS_NUMBER);

    assertThat(db.getAddressFromId(id)).isEqualTo(AMBIGUOUS_NUMBER);
    assertThat(db.getCanonicalAddressId(SPECIFIC_NUMBER)).isEqualTo(id);
    assertThat(db.getAddressFromId(id)).isEqualTo(SPECIFIC_NUMBER);
    assertThat(db.getCanonicalAddressId(AMBIGUOUS_NUMBER)).isEqualTo(id);

    assertThat(db.getCanonicalAddressId(AMBIGUOUS_NUMBER)).isEqualTo(id);
    assertThat(db.getAddressFromId(id)).isEqualTo(AMBIGUOUS_NUMBER);
    assertThat(db.getCanonicalAddressId(SPECIFIC_NUMBER)).isEqualTo(id);
    assertThat(db.getAddressFromId(id)).isEqualTo(SPECIFIC_NUMBER);
    assertThat(db.getCanonicalAddressId(AMBIGUOUS_NUMBER)).isEqualTo(id);
  }

  public void testSimilarNumbers() throws Exception {
    assertThat(db.getCanonicalAddressId("This is a phone number 222-333-444"))
        .isNotEqualTo(db.getCanonicalAddressId("222-333-4444"));
    assertThat(db.getCanonicalAddressId("222-333-444"))
        .isNotEqualTo(db.getCanonicalAddressId("222-333-4444"));
    assertThat(db.getCanonicalAddressId("222-333-44"))
        .isNotEqualTo(db.getCanonicalAddressId("222-333-4444"));
    assertThat(db.getCanonicalAddressId("222-333-4"))
        .isNotEqualTo(db.getCanonicalAddressId("222-333-4444"));
    assertThat(db.getCanonicalAddressId("+49 222-333-4444"))
        .isNotEqualTo(db.getCanonicalAddressId("+1 222-333-4444"));

    assertThat(db.getCanonicalAddressId("1 222-333-4444"))
        .isEqualTo(db.getCanonicalAddressId("222-333-4444"));
    assertThat(db.getCanonicalAddressId("1 (222) 333-4444"))
        .isEqualTo(db.getCanonicalAddressId("222-333-4444"));
    assertThat(db.getCanonicalAddressId("+12223334444"))
        .isEqualTo(db.getCanonicalAddressId("222-333-4444"));
    assertThat(db.getCanonicalAddressId("+1 (222) 333.4444"))
        .isEqualTo(db.getCanonicalAddressId("222-333-4444"));
    assertThat(db.getCanonicalAddressId("+49 (222) 333.4444"))
        .isEqualTo(db.getCanonicalAddressId("222-333-4444"));

  }

  public void testEmailAddresses() throws Exception {
    final long emailId        = db.getCanonicalAddressId(EMAIL);
    final long similarEmailId = db.getCanonicalAddressId(SIMILAR_EMAIL);

    assertThat(emailId).isNotEqualTo(similarEmailId);

    assertThat(db.getAddressFromId(emailId)).isEqualTo(EMAIL);
    assertThat(db.getAddressFromId(similarEmailId)).isEqualTo(SIMILAR_EMAIL);
  }

  public void testGroups() throws Exception {
    final long groupId        = db.getCanonicalAddressId(GROUP);
    final long similarGroupId = db.getCanonicalAddressId(SIMILAR_GROUP);

    assertThat(groupId).isNotEqualTo(similarGroupId);

    assertThat(db.getAddressFromId(groupId)).isEqualTo(GROUP);
    assertThat(db.getAddressFromId(similarGroupId)).isEqualTo(SIMILAR_GROUP);
  }

  public void testAlpha() throws Exception {
    final long id        = db.getCanonicalAddressId(ALPHA);
    final long similarId = db.getCanonicalAddressId(SIMILAR_ALPHA);

    assertThat(id).isNotEqualTo(similarId);

    assertThat(db.getAddressFromId(id)).isEqualTo(ALPHA);
    assertThat(db.getAddressFromId(similarId)).isEqualTo(SIMILAR_ALPHA);
  }

  public void testIsNumber() throws Exception {
    assertThat(CanonicalAddressDatabase.isNumberAddress("+495556666777")).isTrue();
    assertThat(CanonicalAddressDatabase.isNumberAddress("(222) 333-4444")).isTrue();
    assertThat(CanonicalAddressDatabase.isNumberAddress("1 (222) 333-4444")).isTrue();
    assertThat(CanonicalAddressDatabase.isNumberAddress("T-Mobile123")).isTrue();
    assertThat(CanonicalAddressDatabase.isNumberAddress("333-4444")).isTrue();
    assertThat(CanonicalAddressDatabase.isNumberAddress("12345")).isTrue();
    assertThat(CanonicalAddressDatabase.isNumberAddress("T-Mobile")).isFalse();
    assertThat(CanonicalAddressDatabase.isNumberAddress("T-Mobile1")).isFalse();
    assertThat(CanonicalAddressDatabase.isNumberAddress("Wherever bank")).isFalse();
    assertThat(CanonicalAddressDatabase.isNumberAddress("__textsecure_group__!afafafafafaf")).isFalse();
    assertThat(CanonicalAddressDatabase.isNumberAddress("email@domain.com")).isFalse();
  }
}