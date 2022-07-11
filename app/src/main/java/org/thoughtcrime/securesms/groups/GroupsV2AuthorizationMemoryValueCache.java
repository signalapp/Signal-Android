package org.thoughtcrime.securesms.groups;

import androidx.annotation.NonNull;

import org.signal.libsignal.zkgroup.auth.AuthCredentialWithPniResponse;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class GroupsV2AuthorizationMemoryValueCache implements GroupsV2Authorization.ValueCache {

  private final GroupsV2Authorization.ValueCache         inner;
  private       Map<Long, AuthCredentialWithPniResponse> values;

  public GroupsV2AuthorizationMemoryValueCache(@NonNull GroupsV2Authorization.ValueCache inner) {
    this.inner = inner;
  }

  @Override
  public synchronized void clear() {
    inner.clear();
    values = null;
  }

  @Override
  public @NonNull synchronized Map<Long, AuthCredentialWithPniResponse> read() {
    Map<Long, AuthCredentialWithPniResponse> map = values;

    if (map == null) {
      map    = inner.read();
      values = map;
    }

    return map;
  }

  @Override
  public synchronized void write(@NonNull Map<Long, AuthCredentialWithPniResponse> values) {
    inner.write(values);
    this.values = Collections.unmodifiableMap(new HashMap<>(values));
  }
}
