package org.thoughtcrime.securesms.l10n;

import android.app.Application;
import android.content.res.Resources;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public final class LanguageResourcesTest {

  @Test
  public void language_entries_match_language_values_in_length() {
    Resources resources = ApplicationProvider.getApplicationContext().getResources();
    String[]  values    = resources.getStringArray(R.array.language_values);
    String[]  entries   = resources.getStringArray(R.array.language_entries);
    assertEquals(values.length, entries.length);
  }

  @Test
  public void language_options_matches_available_resources() {
    Set<String> languageEntries = languageEntries();
    Set<String> foundResources  = buildConfigResources();
    if (!languageEntries.equals(foundResources)) {
      assertSubset(foundResources, languageEntries, "Missing language_entries for resources");
      assertSubset(languageEntries, foundResources, "Missing resources for language_entries");
      fail("Unexpected");
    }
  }

  private static Set<String> languageEntries() {
    Resources resources = ApplicationProvider.getApplicationContext().getResources();
    String[]  values    = resources.getStringArray(R.array.language_values);

    List<String> tail = Arrays.asList(values).subList(1, values.length);
    Set<String>  set  = new HashSet<>(tail);

    assertEquals("First is not the default", "zz", values[0]);
    assertEquals("List contains duplicates", tail.size(), set.size());
    return set;
  }

  private static Set<String> buildConfigResources() {
    Set<String> set = new HashSet<>();
    Collections.addAll(set, BuildConfig.LANGUAGES);
    assertEquals("List contains duplicates", BuildConfig.LANGUAGES.length, set.size());
    return set;
  }

  /**
   * Fails if "a" is not a subset of "b", lists the additional values found in "a"
   */
  private static void assertSubset(Set<String> a, Set<String> b, String message) {
    Set<String> delta = subtract(a, b);
    if (!delta.isEmpty()) {
      fail(message + ": " + String.join(", ", delta));
    }
  }

  /**
   * Set a - Set b
   */
  private static Set<String> subtract(Set<String> a, Set<String> b) {
    Set<String> set = new HashSet<>(a);
    set.removeAll(b);
    return set;
  }
}
