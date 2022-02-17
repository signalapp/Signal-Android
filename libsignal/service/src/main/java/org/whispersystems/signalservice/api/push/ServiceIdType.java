package org.whispersystems.signalservice.api.push;

public enum ServiceIdType {
  ACI("aci"), PNI("pni");

  private final String queryParamName;

  ServiceIdType(String queryParamName) {
    this.queryParamName = queryParamName;
  }

  public String queryParam() {
    return queryParamName;
  }
}
