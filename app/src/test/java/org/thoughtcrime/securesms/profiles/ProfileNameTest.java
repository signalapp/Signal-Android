package org.thoughtcrime.securesms.profiles;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public final class ProfileNameTest {

  @Test
  public void givenEmpty_thenIExpectSaneDefaults() {
    // GIVEN
    ProfileName profileName = ProfileName.EMPTY;

    // THEN
    assertNotNull("ProfileName should be non-null", profileName);
    assertFalse("ProfileName should not be CJKV", profileName.isProfileNameCJKV());
    assertEquals("ProfileName should have empty given name", "", profileName.getGivenName());
    assertEquals("ProfileName should have empty family name", "", profileName.getFamilyName());
    assertTrue(profileName.isEmpty());
  }

  @Test
  public void givenNullProfileName_whenIFromSerialized_thenIExpectExactlyEmpty() {
    // GIVEN
    ProfileName profileName = ProfileName.fromSerialized(null);

    // THEN
    assertSame(ProfileName.EMPTY, profileName);
  }

  @Test
  public void givenProfileNameWithNulls_thenIExpectExactlyEmpty() {
    // GIVEN
    ProfileName profileName = ProfileName.fromParts(null, null);

    // THEN
    assertSame(ProfileName.EMPTY, profileName);
  }

  @Test
  public void givenProfileNameWithGivenNameOnly_whenIFromDataString_thenIExpectValidProfileName() {
    // GIVEN
    String profileName = "Given";

    // WHEN
    ProfileName name = ProfileName.fromSerialized(profileName);

    // THEN
    assertNotNull("ProfileName should be non-null", name);
    assertFalse("ProfileName should not be CJKV", name.isProfileNameCJKV());
    assertEquals("ProfileName should have expected given name", profileName, name.getGivenName());
    assertEquals("ProfileName should have empty family name", "", name.getFamilyName());
  }

  @Test
  public void givenProfileNameWithEnglishGivenNameAndEnglishFamilyName_whenIFromDataString_thenIExpectValidProfileName() {
    // GIVEN
    String profileName = "Given\0Family";

    // WHEN
    ProfileName name = ProfileName.fromSerialized(profileName);

    // THEN
    assertNotNull("ProfileName should be non-null", name);
    assertFalse("ProfileName should not be CJKV", name.isProfileNameCJKV());
    assertEquals("ProfileName should have expected given name", "Given", name.getGivenName());
    assertEquals("ProfileName should have expected family name", "Family", name.getFamilyName());
  }

  @Test
  public void givenProfileNameWithEnglishGivenNameAndCJKVFamilyName_whenIFromDataString_thenIExpectNonCJKVProfileName() {
    // GIVEN
    String profileName = "Given\0码";

    // WHEN
    ProfileName name = ProfileName.fromSerialized(profileName);

    // THEN
    assertNotNull("ProfileName should be non-null", name);
    assertFalse("ProfileName should not be CJKV", name.isProfileNameCJKV());
    assertEquals("ProfileName should have expected given name", "Given", name.getGivenName());
    assertEquals("ProfileName should have expected family name", "码", name.getFamilyName());
  }

  @Test
  public void givenProfileNameWithCJKVGivenNameAndCJKVFamilyName_whenIFromDataString_thenIExpectNonCJKVProfileName() {
    // GIVEN
    String profileName = "统\0码";

    // WHEN
    ProfileName name = ProfileName.fromSerialized(profileName);

    // THEN
    assertNotNull("ProfileName should be non-null", name);
    assertTrue("ProfileName should be CJKV", name.isProfileNameCJKV());
    assertEquals("ProfileName should have expected given name", "统", name.getGivenName());
    assertEquals("ProfileName should have expected family name", "码", name.getFamilyName());
  }

  @Test
  public void givenProfileNameWithCJKVGivenNameAndEnglishFamilyName_whenIFromDataString_thenIExpectNonCJKVProfileName() {
    // GIVEN
    String profileName = "统\0Family";

    // WHEN
    ProfileName name = ProfileName.fromSerialized(profileName);

    // THEN
    assertNotNull("ProfileName should be non-null", name);
    assertFalse("ProfileName should not be CJKV", name.isProfileNameCJKV());
    assertEquals("ProfileName should have expected given name", "统", name.getGivenName());
    assertEquals("ProfileName should have expected family name", "Family", name.getFamilyName());
  }

  @Test
  public void givenProfileNameWithEmptyInputs_whenIToDataString_thenIExpectAnEmptyString() {
    // GIVEN
    ProfileName name = ProfileName.fromParts("", "");

    // WHEN
    String data = name.serialize();

    // THEN
    assertEquals("Blank String should be returned (For back compat)", "", data);
  }

  @Test
  public void givenProfileNameWithEmptyGivenName_whenIToDataString_thenIExpectAnEmptyString() {
    // GIVEN
    ProfileName name = ProfileName.fromParts("", "Family");

    // WHEN
    String data = name.serialize();

    // THEN
    assertEquals("Blank String should be returned (For back compat)", "", data);
    assertEquals("Family", name.getFamilyName());
  }

  @Test
  public void givenProfileNameWithGivenName_whenIToDataString_thenIExpectValidProfileName() {
    // GIVEN
    ProfileName name = ProfileName.fromParts("Given", "");

    // WHEN
    String data = name.serialize();

    // THEN
    assertEquals(data, "Given");
  }

  @Test
  public void givenProfileNameWithGivenNameAndFamilyName_whenIToDataString_thenIExpectValidProfileName() {
    // GIVEN
    ProfileName name = ProfileName.fromParts("Given", "Family");

    // WHEN
    String data = name.serialize();

    // THEN
    assertEquals(data, "Given\0Family");
  }

  @Test
  public void fromParts_with_long_name_parts() {
    ProfileName name = ProfileName.fromParts("GivenSomeVeryLongNameSomeVeryLongNameGivenSomeVeryLongNameSomeVeryLongNameGivenSomeVeryLongNameSomeVeryLongNameGivenSomeVeryLongNameSomeVeryLongName", "FamilySomeVeryLongNameSomeVeryLongName");

    assertEquals("GivenSomeVeryLongNameSomeVeryLongNameGivenSomeVeryLongNameSomeVeryLongNameGivenSomeVeryLongNameSomeVeryLongNameGivenSomeVeryLong", name.getGivenName());
    assertEquals("FamilySomeVeryLongNameSomeVeryLongName", name.getFamilyName());
  }

  @Test
  public void givenProfileNameWithGivenNameAndFamilyNameWithSpaces_whenIToDataString_thenIExpectTrimmedProfileName() {
    // GIVEN
    ProfileName name = ProfileName.fromParts(" Given ", "  Family");

    // WHEN
    String data = name.serialize();

    // THEN
    assertEquals(data, "Given\0Family");
    assertEquals(name.getGivenName(), "Given");
    assertEquals(name.getFamilyName(), "Family");
  }
}
