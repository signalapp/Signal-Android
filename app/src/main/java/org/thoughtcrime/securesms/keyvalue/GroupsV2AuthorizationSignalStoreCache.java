package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.auth.AuthCredentialWithPniResponse;
import org.thoughtcrime.securesms.database.model.databaseprotos.TemporalAuthCredentialResponse;
import org.thoughtcrime.securesms.database.model.databaseprotos.TemporalAuthCredentialResponses;
import org.thoughtcrime.securesms.groups.GroupsV2Authorization;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class GroupsV2AuthorizationSignalStoreCache implements GroupsV2Authorization.ValueCache {

  private static final String TAG = Log.tag(GroupsV2AuthorizationSignalStoreCache.class);

  private static final String ACI_PNI_PREFIX  = "gv2:auth_token_cache";
  private static final int    ACI_PNI_VERSION = 3;

  private final String        key;
  private final KeyValueStore store;

  public static GroupsV2AuthorizationSignalStoreCache createAciCache(@NonNull KeyValueStore store) {
    if (store.containsKey(ACI_PNI_PREFIX)) {
      store.beginWrite()
           .remove(ACI_PNI_PREFIX)
           .commit();
    }

    return new GroupsV2AuthorizationSignalStoreCache(store, ACI_PNI_PREFIX + ":" + ACI_PNI_VERSION);
  }

  private GroupsV2AuthorizationSignalStoreCache(@NonNull KeyValueStore store, @NonNull String key) {
    this.store = store;
    this.key   = key;
  }

  @Override
  public void clear() {
    store.beginWrite()
         .remove(key)
         .commit();

    Log.i(TAG, "Cleared local response cache");
  }

  @Override
  public @NonNull Map<Long, AuthCredentialWithPniResponse> read() {
    byte[] credentialBlob = store.getBlob(key, null);

    if (credentialBlob == null) {
      Log.i(TAG, "No credentials responses are cached locally");
      return Collections.emptyMap();
    }

    try {
      TemporalAuthCredentialResponses              temporalCredentials = TemporalAuthCredentialResponses.parseFrom(credentialBlob);
      HashMap<Long, AuthCredentialWithPniResponse> result              = new HashMap<>(temporalCredentials.getCredentialResponseCount());

      for (TemporalAuthCredentialResponse credential : temporalCredentials.getCredentialResponseList()) {
        result.put(credential.getDate(), new AuthCredentialWithPniResponse(credential.getAuthCredentialResponse().toByteArray()));
      }

      Log.i(TAG, String.format(Locale.US, "Loaded %d credentials from local storage", result.size()));

      return result;
    } catch (InvalidProtocolBufferException | InvalidInputException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public void write(@NonNull Map<Long, AuthCredentialWithPniResponse> values) {
    TemporalAuthCredentialResponses.Builder builder = TemporalAuthCredentialResponses.newBuilder();

    for (Map.Entry<Long, AuthCredentialWithPniResponse> entry : values.entrySet()) {
      builder.addCredentialResponse(TemporalAuthCredentialResponse.newBuilder()
                                                                  .setDate(entry.getKey())
                                                                  .setAuthCredentialResponse(ByteString.copyFrom(entry.getValue().serialize())));
    }

    store.beginWrite()
         .putBlob(key, builder.build().toByteArray())
         .commit();

    Log.i(TAG, String.format(Locale.US, "Written %d credentials to local storage", values.size()));
  }
}
