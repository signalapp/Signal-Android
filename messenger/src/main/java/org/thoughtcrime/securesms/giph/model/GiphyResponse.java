package org.thoughtcrime.securesms.giph.model;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class GiphyResponse {

  @JsonProperty
  private List<GiphyImage> data;

  public List<GiphyImage> getData() {
    return data;
  }

}
