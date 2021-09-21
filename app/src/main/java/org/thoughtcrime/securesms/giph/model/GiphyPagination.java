package org.thoughtcrime.securesms.giph.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GiphyPagination {
  @JsonProperty
  private int total_count;

  @JsonProperty
  private int count;

  @JsonProperty
  private int offset;

  public int getTotalCount() {
    return total_count;
  }
}
