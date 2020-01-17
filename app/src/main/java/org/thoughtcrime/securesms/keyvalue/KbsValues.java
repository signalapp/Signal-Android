package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.util.JsonUtils;
import org.whispersystems.signalservice.api.RegistrationLockData;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;
import org.whispersystems.signalservice.internal.registrationpin.PinStretcher;

import java.io.IOException;

public final class KbsValues {

  private static final String REGISTRATION_LOCK_PREF_V2        = "kbs.registration_lock_v2";
  private static final String REGISTRATION_LOCK_TOKEN_PREF     = "kbs.registration_lock_token";
  private static final String REGISTRATION_LOCK_PIN_KEY_2_PREF = "kbs.registration_lock_pin_key_2";
  private static final String REGISTRATION_LOCK_MASTER_KEY     = "kbs.registration_lock_master_key";
  private static final String REGISTRATION_LOCK_TOKEN_RESPONSE = "kbs.registration_lock_token_response";

  private final KeyValueStore store;

  KbsValues(KeyValueStore store) {
    this.store = store;
  }

  public void setRegistrationLockMasterKey(@Nullable RegistrationLockData registrationLockData) {
    KeyValueStore.Writer editor = store.beginWrite();

    if (registrationLockData == null) {
      editor.remove(REGISTRATION_LOCK_PREF_V2)
            .remove(REGISTRATION_LOCK_TOKEN_RESPONSE)
            .remove(REGISTRATION_LOCK_MASTER_KEY)
            .remove(REGISTRATION_LOCK_TOKEN_PREF)
            .remove(REGISTRATION_LOCK_PIN_KEY_2_PREF);
    } else {
      PinStretcher.MasterKey masterKey     = registrationLockData.getMasterKey();
      String                 tokenResponse;
      try {
        tokenResponse = JsonUtils.toJson(registrationLockData.getTokenResponse());
      } catch (IOException e) {
        throw new AssertionError(e);
      }

      editor.putBoolean(REGISTRATION_LOCK_PREF_V2, true)
            .putString(REGISTRATION_LOCK_TOKEN_RESPONSE, tokenResponse)
            .putBlob(REGISTRATION_LOCK_MASTER_KEY, masterKey.getMasterKey())
            .putString(REGISTRATION_LOCK_TOKEN_PREF, masterKey.getRegistrationLock())
            .putBlob(REGISTRATION_LOCK_PIN_KEY_2_PREF, masterKey.getPinKey2());
    }

    editor.commit();
  }

  public byte[] getMasterKey() {
    return store.getBlob(REGISTRATION_LOCK_MASTER_KEY, null);
  }

  public @Nullable String getRegistrationLockToken() {
    return store.getString(REGISTRATION_LOCK_TOKEN_PREF, null);
  }

  public @Nullable byte[] getRegistrationLockPinKey2() {
    return store.getBlob(REGISTRATION_LOCK_PIN_KEY_2_PREF, null);
  }

  public boolean isV2RegistrationLockEnabled() {
    return store.getBoolean(REGISTRATION_LOCK_PREF_V2, false);
  }

  public @Nullable
  TokenResponse getRegistrationLockTokenResponse() {
    String token = store.getString(REGISTRATION_LOCK_TOKEN_RESPONSE, null);

    if (token == null) return null;

    try {
      return JsonUtils.fromJson(token, TokenResponse.class);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}
