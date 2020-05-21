package org.whispersystems.signalservice.api.groupsv2;

import org.signal.zkgroup.auth.AuthCredentialPresentation;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.signalservice.internal.util.Hex;

import okhttp3.Credentials;

public final class GroupsV2AuthorizationString {

  private final String authString;

  GroupsV2AuthorizationString(GroupSecretParams groupSecretParams, AuthCredentialPresentation authCredentialPresentation) {
    String username = Hex.toStringCondensed(groupSecretParams.getPublicParams().serialize());
    String password = Hex.toStringCondensed(authCredentialPresentation.serialize());

    authString = Credentials.basic(username, password);
  }

  @Override
  public String toString() {
    return authString;
  }
}
