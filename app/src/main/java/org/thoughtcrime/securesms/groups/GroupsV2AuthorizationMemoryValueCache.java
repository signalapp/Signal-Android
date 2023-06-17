package org.thoughtcrime.securesms.groups;

import androidx.annotation.NonNull;

import org.signal.libsignal.zkgroup.auth.AuthCredentialWithPniResponse;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class GroupsV2AuthorizationMemoryValueCache implements GroupsV2Authorization.ValueCache {

  private final GroupsV2Authorization.ValueCache   inner;
  private       GroupsV2Api.CredentialResponseMaps values;

  public GroupsV2AuthorizationMemoryValueCache(@NonNull GroupsV2Authorization.ValueCache inner) {
    this.inner = inner;
  }

  @Override
  public synchronized void clear() {
    inner.clear();
    values = null;
  }

  @Override
  public @NonNull synchronized GroupsV2Api.CredentialResponseMaps read() {
    GroupsV2Api.CredentialResponseMaps map = values;

    if (map == null) {
      map    = inner.read();
      values = map;
    }

    return map;
  }

  @Override
  public synchronized void write(@NonNull GroupsV2Api.CredentialResponseMaps values) {
    inner.write(values);
    this.values = values.createUnmodifiableCopy();
  }
}
