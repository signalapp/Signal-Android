package org.thoughtcrime.securesms.contactshare;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.app.Application;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * Covers the name shown on the bubble, the details header, the share screen, the avatar initials and
 * the add-to-contacts prefill, all of which route through getDisplayName.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class ContactUtilTest_getDisplayName {

  @Test
  public void givenNoContact_thenIExpectEmpty() {
    assertEquals("", ContactUtil.getDisplayName(null));
  }

  @Test
  public void givenGivenAndFamily_thenIExpectGivenFirst() {
    assertEquals("Paige Hall", ContactUtil.getDisplayName(contactWithName("Paige", "Hall", null, null, null, null)));
  }

  @Test
  public void givenEveryNamePart_thenIExpectAllOfThemInOrder() {
    assertEquals("Dr Paige A Hall II", ContactUtil.getDisplayName(contactWithName("Paige", "Hall", "Dr", "II", "A", null)));
  }

  @Test
  public void givenCjkvGivenAndFamily_thenIExpectFamilyFirst() {
    assertEquals("山田 太郎", ContactUtil.getDisplayName(contactWithName("太郎", "山田", null, null, null, null)));
  }

  @Test
  public void givenCjkvWithASuffix_thenIExpectTheFlatJoin() {
    assertEquals("太郎 山田 II", ContactUtil.getDisplayName(contactWithName("太郎", "山田", null, "II", null, null)));
  }

  @Test
  public void givenMixedScript_thenIExpectGivenFirst() {
    assertEquals("Taro 山田", ContactUtil.getDisplayName(contactWithName("Taro", "山田", null, null, null, null)));
  }

  @Test
  public void givenOnlyANickname_thenIExpectTheNickname() {
    assertEquals("Paigey", ContactUtil.getDisplayName(contactWithName(null, null, null, null, null, "Paigey")));
  }

  @Test
  public void givenANameAndANickname_thenIExpectTheNameToWin() {
    assertEquals("Paige Hall", ContactUtil.getDisplayName(contactWithName("Paige", "Hall", null, null, null, "Paigey")));
  }

  @Test
  public void givenOnlyAnOrganization_thenIExpectTheOrganization() {
    assertEquals("Pacific Plumbing", ContactUtil.getDisplayName(contactWithOrganization("Pacific Plumbing")));
  }

  @Test
  public void givenANicknameAndAnOrganization_thenIExpectTheNickname() {
    Contact contact = new Contact(new Contact.Name(null, null, null, null, null, "Paigey"),
                                  "Pacific Plumbing",
                                  Collections.emptyList(),
                                  Collections.emptyList(),
                                  Collections.emptyList(),
                                  null,
                                  null,
                                  null,
                                  null);

    assertEquals("Paigey", ContactUtil.getDisplayName(contact));
  }

  @Test
  public void givenNothingToShow_thenIExpectEmpty() {
    assertEquals("", ContactUtil.getDisplayName(contactWithOrganization(null)));
  }

  private static Contact contactWithName(String given, String family, String prefix, String suffix, String middle, String nickname) {
    return new Contact(new Contact.Name(given, family, prefix, suffix, middle, nickname),
                       null,
                       Collections.emptyList(),
                       Collections.emptyList(),
                       Collections.emptyList(),
                       null,
                       null,
                       null,
                       null);
  }

  private static Contact contactWithOrganization(String organization) {
    return new Contact(new Contact.Name(null, null, null, null, null, null),
                       organization,
                       Collections.emptyList(),
                       Collections.emptyList(),
                       Collections.emptyList(),
                       null,
                       null,
                       null,
                       null);
  }
}
