package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.lock.v2.PinKeyboardType;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.whispersystems.signalservice.api.RegistrationLockData;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;

import java.io.IOException;
import java.security.SecureRandom;

public final class KbsValues {

  private static final String V2_LOCK_ENABLED     = "kbs.v2_lock_enabled";
  private static final String MASTER_KEY          = "kbs.registration_lock_master_key";
  private static final String TOKEN_RESPONSE      = "kbs.token_response";
  private static final String LOCK_LOCAL_PIN_HASH = "kbs.registration_lock_local_pin_hash";
  private static final String KEYBOARD_TYPE       = "kbs.keyboard_type";

  private final KeyValueStore store;

  KbsValues(KeyValueStore store) {
    this.store = store;
  }

  /**
   * Deliberately does not clear the {@link #MASTER_KEY}.
   */
  public void clearRegistrationLock() {
    store.beginWrite()
         .remove(V2_LOCK_ENABLED)
         .remove(TOKEN_RESPONSE)
         .remove(LOCK_LOCAL_PIN_HASH)
         .remove(KEYBOARD_TYPE)
         .commit();
  }

  public synchronized void setRegistrationLockMasterKey(@NonNull RegistrationLockData registrationLockData, @NonNull String localPinHash) {
    MasterKey masterKey     = registrationLockData.getMasterKey();
    String    tokenResponse;
    try {
      tokenResponse = JsonUtils.toJson(registrationLockData.getTokenResponse());
    } catch (IOException e) {
      throw new AssertionError(e);
    }

    store.beginWrite()
         .putBoolean(V2_LOCK_ENABLED, true)
         .putString(TOKEN_RESPONSE, tokenResponse)
         .putBlob(MASTER_KEY, masterKey.serialize())
         .putString(LOCK_LOCAL_PIN_HASH, localPinHash)
         .commit();
  }

  /**
   * Finds or creates the master key. Therefore this will always return a master key whether backed
   * up or not.
   * <p>
   * If you only want a key when it's backed up, use {@link #getPinBackedMasterKey()}.
   */
  public synchronized @NonNull MasterKey getOrCreateMasterKey() {
    byte[] blob = store.getBlob(MASTER_KEY, null);

    if (blob == null) {
      store.beginWrite()
           .putBlob(MASTER_KEY, MasterKey.createNew(new SecureRandom()).serialize())
           .commit();
      blob = store.getBlob(MASTER_KEY, null);
    }

    return new MasterKey(blob);
  }

  /**
   * Returns null if master key is not backed up by a pin.
   */
  public synchronized @Nullable MasterKey getPinBackedMasterKey() {
    if (!isV2RegistrationLockEnabled()) return null;
    return getMasterKey();
  }

  private synchronized @Nullable MasterKey getMasterKey() {
    byte[] blob = store.getBlob(MASTER_KEY, null);
    return blob != null ? new MasterKey(blob) : null;
  }

  public @Nullable String getRegistrationLockToken() {
    MasterKey masterKey = getPinBackedMasterKey();
    if (masterKey == null) {
      return null;
    } else {
      return masterKey.deriveRegistrationLock();
    }
  }

  public @Nullable String getLocalPinHash() {
    return store.getString(LOCK_LOCAL_PIN_HASH, null);
  }

  public boolean isV2RegistrationLockEnabled() {
    return store.getBoolean(V2_LOCK_ENABLED, false);
  }

  public @Nullable TokenResponse getRegistrationLockTokenResponse() {
    String token = store.getString(TOKEN_RESPONSE, null);

    if (token == null) return null;

    try {
      return JsonUtils.fromJson(token, TokenResponse.class);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public void setKeyboardType(@NonNull PinKeyboardType keyboardType) {
    store.beginWrite()
         .putString(KEYBOARD_TYPE, keyboardType.getCode())
         .commit();
  }

  @CheckResult
  public @NonNull PinKeyboardType getKeyboardType() {
    return PinKeyboardType.fromCode(store.getString(KEYBOARD_TYPE, null));
  }

  public boolean hasMigratedToPinsForAll() {
    return store.getString(KEYBOARD_TYPE, null) != null;
  }
}
