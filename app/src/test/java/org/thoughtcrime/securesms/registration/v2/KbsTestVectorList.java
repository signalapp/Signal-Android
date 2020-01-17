package org.thoughtcrime.securesms.registration.v2;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class KbsTestVectorList {

  @JsonProperty("vectors")
  private List<KbsTestVector> vectors;

  public List<KbsTestVector> getVectors() {
    return vectors;
  }
}