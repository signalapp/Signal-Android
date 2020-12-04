package org.thoughtcrime.securesms.util;

import androidx.annotation.Nullable;

import com.annimon.stream.ComparatorCompat;

import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SemanticVersion implements Comparable<SemanticVersion> {

  private static final Pattern VERSION_PATTERN = Pattern.compile("^([0-9]+)\\.([0-9]+)\\.([0-9]+)$");

  private static final Comparator<SemanticVersion> MAJOR_COMPARATOR = (s1, s2) -> Integer.compare(s1.major, s2.major);
  private static final Comparator<SemanticVersion> MINOR_COMPARATOR = (s1, s2) -> Integer.compare(s1.minor, s2.minor);
  private static final Comparator<SemanticVersion> PATCH_COMPARATOR = (s1, s2) -> Integer.compare(s1.patch, s2.patch);
  private static final Comparator<SemanticVersion> COMPARATOR       = ComparatorCompat.chain(MAJOR_COMPARATOR)
                                                                                      .thenComparing(MINOR_COMPARATOR)
                                                                                      .thenComparing(PATCH_COMPARATOR);

  private final int major;
  private final int minor;
  private final int patch;

  public SemanticVersion(int major, int minor, int patch) {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
  }

  public static @Nullable SemanticVersion parse(@Nullable String value) {
    if (value == null) {
      return null;
    }

    Matcher matcher = VERSION_PATTERN.matcher(value);
    if (Util.isEmpty(value) || !matcher.matches()) {
      return null;
    }

    int major = Integer.parseInt(matcher.group(1));
    int minor = Integer.parseInt(matcher.group(2));
    int patch = Integer.parseInt(matcher.group(3));

    return new SemanticVersion(major, minor, patch);
  }

  @Override
  public int compareTo(SemanticVersion other) {
    return COMPARATOR.compare(this, other);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SemanticVersion that = (SemanticVersion) o;
    return major == that.major &&
           minor == that.minor &&
           patch == that.patch;
  }

  @Override
  public int hashCode() {
    return Objects.hash(major, minor, patch);
  }
}
