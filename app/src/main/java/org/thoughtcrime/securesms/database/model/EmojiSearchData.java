package org.thoughtcrime.securesms.database.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Ties together an emoji with it's associated search tags.
 */
public final class EmojiSearchData {
  @JsonProperty
  private String emoji;

  @JsonProperty
  private List<String> tags;

  @JsonProperty
  private String shortName;

  @JsonProperty
  private int rank;

  public EmojiSearchData() {}

  public @NonNull String getEmoji() {
    return emoji;
  }

  public @NonNull List<String> getTags() {
    return tags;
  }

  public @Nullable String getShortName() {
    return shortName;
  }

  /**
   * A value representing how popular an emoji is, with 1 being the best rank. A value of 0 means this emoji has no rank at all.
   */
  public int getRank() {
    return rank;
  }
}
