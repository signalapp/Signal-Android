package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.core.util.logging.Log;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.auth.AuthCredentialResponse;
import org.thoughtcrime.securesms.database.model.databaseprotos.TemporalAuthCredentialResponse;
import org.thoughtcrime.securesms.database.model.databaseprotos.TemporalAuthCredentialResponses;
import org.thoughtcrime.securesms.groups.GroupsV2Authorization;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class GroupsV2AuthorizationSignalStoreCache implements GroupsV2Authorization.ValueCache {

  private static final String TAG = Log.tag(GroupsV2AuthorizationSignalStoreCache.class);

  private static final String PREFIX  = "gv2:auth_token_cache";
  private static final int    VERSION = 2;
  private static final String KEY     = PREFIX + ":" + VERSION;

  private final KeyValueStore store;

  GroupsV2AuthorizationSignalStoreCache(KeyValueStore store) {
    this.store = store;

    if (store.containsKey(PREFIX)) {
      store.beginWrite()
           .remove(PREFIX)
           .commit();
    }
  }

  @Override
  public void clear() {
    store.beginWrite()
         .remove(KEY)
         .commit();

    Log.i(TAG, "Cleared local response cache");
  }

  @Override
  public @NonNull Map<Integer, AuthCredentialResponse> read() {
    byte[] credentialBlob = store.getBlob(KEY, null);

    if (credentialBlob == null) {
      Log.i(TAG, "No credentials responses are cached locally");
      return Collections.emptyMap();
    }

    try {
      TemporalAuthCredentialResponses          temporalCredentials = TemporalAuthCredentialResponses.parseFrom(credentialBlob);
      HashMap<Integer, AuthCredentialResponse> result              = new HashMap<>(temporalCredentials.getCredentialResponseCount());

      for (TemporalAuthCredentialResponse credential : temporalCredentials.getCredentialResponseList()) {
        result.put(credential.getDate(), new AuthCredentialResponse(credential.getAuthCredentialResponse().toByteArray()));
      }

      Log.i(TAG, String.format(Locale.US, "Loaded %d credentials from local storage", result.size()));

      return result;
    } catch (InvalidProtocolBufferException | InvalidInputException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public void write(@NonNull Map<Integer, AuthCredentialResponse> values) {
    TemporalAuthCredentialResponses.Builder builder = TemporalAuthCredentialResponses.newBuilder();

    for (Map.Entry<Integer, AuthCredentialResponse> entry : values.entrySet()) {
      builder.addCredentialResponse(TemporalAuthCredentialResponse.newBuilder()
                                                                  .setDate(entry.getKey())
                                                                  .setAuthCredentialResponse(ByteString.copyFrom(entry.getValue().serialize())));
    }

    store.beginWrite()
         .putBlob(KEY, builder.build().toByteArray())
         .commit();

    Log.i(TAG, String.format(Locale.US, "Written %d credentials to local storage", values.size()));
  }
}
