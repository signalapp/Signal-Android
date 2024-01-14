/*
 * Copyright (C) 2013-2018 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.securesms.crypto;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidKeyIdException;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.protocol.kem.KEMKeyPair;
import org.signal.libsignal.protocol.kem.KEMKeyType;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SignalProtocolStore;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.util.Medium;
import org.thoughtcrime.securesms.crypto.storage.PreKeyMetadataStore;
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PreKeyUtil {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(PreKeyUtil.class);

  private static final int  BATCH_SIZE  = 100;
  private static final long ARCHIVE_AGE = TimeUnit.DAYS.toMillis(30);

  public synchronized static @NonNull List<PreKeyRecord> generateAndStoreOneTimeEcPreKeys(@NonNull SignalServiceAccountDataStore protocolStore, @NonNull PreKeyMetadataStore metadataStore) {
    int                      startingId = metadataStore.getNextEcOneTimePreKeyId();
    final List<PreKeyRecord> records    = generateOneTimeEcPreKeys(startingId);

    protocolStore.markAllOneTimeEcPreKeysStaleIfNecessary(System.currentTimeMillis());
    storeOneTimeEcPreKeys(protocolStore, metadataStore, records);

    return records;
  }

  public synchronized static List<PreKeyRecord> generateOneTimeEcPreKeys(int startingId) {
    Log.i(TAG, "Generating one-time EC prekeys...");

    List<PreKeyRecord> records = new ArrayList<>(BATCH_SIZE);

    for (int i = 0; i < BATCH_SIZE; i++) {
      int          preKeyId = (startingId + i) % Medium.MAX_VALUE;
      ECKeyPair    keyPair  = Curve.generateKeyPair();
      PreKeyRecord record   = new PreKeyRecord(preKeyId, keyPair);

      records.add(record);
    }

    return records;
  }

  public synchronized static void storeOneTimeEcPreKeys(@NonNull SignalProtocolStore protocolStore, PreKeyMetadataStore metadataStore, List<PreKeyRecord> prekeys) {
    Log.i(TAG, "Storing one-time EC prekeys...");

    if (prekeys.isEmpty()) {
      Log.w(TAG, "Empty list of one-time EC prekeys! Nothing to store.");
      return;
    }

    for (PreKeyRecord record : prekeys) {
      protocolStore.storePreKey(record.getId(), record);
    }

    int lastId = prekeys.get(prekeys.size() - 1).getId();

    metadataStore.setNextEcOneTimePreKeyId((lastId + 1) % Medium.MAX_VALUE);

  }

  public synchronized static @NonNull List<KyberPreKeyRecord> generateAndStoreOneTimeKyberPreKeys(@NonNull SignalServiceAccountDataStore protocolStore, @NonNull PreKeyMetadataStore metadataStore) {
    int                     startingId = metadataStore.getNextKyberPreKeyId();
    List<KyberPreKeyRecord> records    = generateOneTimeKyberPreKeyRecords(startingId, protocolStore.getIdentityKeyPair().getPrivateKey());

    protocolStore.markAllOneTimeKyberPreKeysStaleIfNecessary(System.currentTimeMillis());
    storeOneTimeKyberPreKeys(protocolStore, metadataStore, records);

    return records;
  }

  @NonNull
  public static List<KyberPreKeyRecord> generateOneTimeKyberPreKeyRecords(int startingId, @NonNull ECPrivateKey privateKey) {
    Log.i(TAG, "Generating one-time kyber prekeys...");

    List<KyberPreKeyRecord> records = new LinkedList<>();

    for (int i = 0; i < BATCH_SIZE; i++) {
      int               preKeyId = (startingId + i) % Medium.MAX_VALUE;
      KyberPreKeyRecord record   = generateKyberPreKey(preKeyId, privateKey);

      records.add(record);
    }

    return records;
  }

  public synchronized static void storeOneTimeKyberPreKeys(@NonNull SignalProtocolStore protocolStore, PreKeyMetadataStore metadataStore, List<KyberPreKeyRecord> prekeys) {
    Log.i(TAG, "Storing one-time kyber prekeys...");

    if (prekeys.isEmpty()) {
      Log.w(TAG, "Empty list of kyber prekeys! Nothing to store.");
      return;
    }

    for (KyberPreKeyRecord record : prekeys) {
      protocolStore.storeKyberPreKey(record.getId(), record);
    }

    int lastId = prekeys.get(prekeys.size() - 1).getId();

    metadataStore.setNextKyberPreKeyId((lastId + 1) % Medium.MAX_VALUE);
  }

  public synchronized static @NonNull SignedPreKeyRecord generateAndStoreSignedPreKey(@NonNull SignalProtocolStore protocolStore, @NonNull PreKeyMetadataStore metadataStore) {
    return generateAndStoreSignedPreKey(protocolStore, metadataStore, protocolStore.getIdentityKeyPair().getPrivateKey());
  }

  public synchronized static @NonNull SignedPreKeyRecord generateAndStoreSignedPreKey(@NonNull SignalProtocolStore protocolStore,
                                                                                      @NonNull PreKeyMetadataStore metadataStore,
                                                                                      @NonNull ECPrivateKey privateKey)
  {
    int                signedPreKeyId = metadataStore.getNextSignedPreKeyId();
    SignedPreKeyRecord record         = generateSignedPreKey(signedPreKeyId, privateKey);

    storeSignedPreKey(protocolStore, metadataStore, record);

    return record;
  }

  public synchronized static @NonNull SignedPreKeyRecord generateSignedPreKey(int signedPreKeyId, @NonNull ECPrivateKey privateKey) {
    Log.i(TAG, "Generating signed prekeys...");

    try {
      ECKeyPair keyPair   = Curve.generateKeyPair();
      byte[]    signature = Curve.calculateSignature(privateKey, keyPair.getPublicKey().serialize());

      return new SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), keyPair, signature);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public synchronized static void storeSignedPreKey(@NonNull SignalProtocolStore protocolStore, @NonNull PreKeyMetadataStore metadataStore, SignedPreKeyRecord record) {
    Log.i(TAG, "Storing signed prekey...");

    protocolStore.storeSignedPreKey(record.getId(), record);
    metadataStore.setNextSignedPreKeyId((record.getId() + 1) % Medium.MAX_VALUE);
  }

  public synchronized static @NonNull KyberPreKeyRecord generateAndStoreLastResortKyberPreKey(@NonNull SignalServiceAccountDataStore protocolStore, @NonNull PreKeyMetadataStore metadataStore) {
    return generateAndStoreLastResortKyberPreKey(protocolStore, metadataStore, protocolStore.getIdentityKeyPair().getPrivateKey());
  }

  public synchronized static @NonNull KyberPreKeyRecord generateAndStoreLastResortKyberPreKey(@NonNull SignalServiceAccountDataStore protocolStore,
                                                                                              @NonNull PreKeyMetadataStore metadataStore,
                                                                                              @NonNull ECPrivateKey privateKey)
  {
    int               id     = metadataStore.getNextKyberPreKeyId();
    KyberPreKeyRecord record = generateKyberPreKey(id, privateKey);

    storeLastResortKyberPreKey(protocolStore, metadataStore, record);

    return record;
  }

  public synchronized static @NonNull KyberPreKeyRecord generateLastResortKyberPreKey(int id, @NonNull ECPrivateKey privateKey) {
    Log.i(TAG, "Generating last resort kyber prekey...");
    return generateKyberPreKey(id, privateKey);
  }

  private synchronized static @NonNull KyberPreKeyRecord generateKyberPreKey(int id, @NonNull ECPrivateKey privateKey) {
    KEMKeyPair keyPair   = KEMKeyPair.generate(KEMKeyType.KYBER_1024);
    byte[]     signature = privateKey.calculateSignature(keyPair.getPublicKey().serialize());

    return new KyberPreKeyRecord(id, System.currentTimeMillis(), keyPair, signature);
  }

  public synchronized static void storeLastResortKyberPreKey(@NonNull SignalServiceAccountDataStore protocolStore, @NonNull PreKeyMetadataStore metadataStore, KyberPreKeyRecord record) {
    Log.i(TAG, "Storing kyber prekeys...");
    protocolStore.storeKyberPreKey(record.getId(), record);
    metadataStore.setNextKyberPreKeyId((record.getId() + 1) % Medium.MAX_VALUE);
  }


  /**
   * Finds all of the signed prekeys that are older than the archive age, and archive all but the youngest of those.
   */
  public synchronized static void cleanSignedPreKeys(@NonNull SignalProtocolStore protocolStore, @NonNull PreKeyMetadataStore metadataStore) {
    Log.i(TAG, "Cleaning signed prekeys...");

    int activeSignedPreKeyId = metadataStore.getActiveSignedPreKeyId();
    if (activeSignedPreKeyId < 0) {
      return;
    }

    try {
      long                     now           = System.currentTimeMillis();
      SignedPreKeyRecord       currentRecord = protocolStore.loadSignedPreKey(activeSignedPreKeyId);
      List<SignedPreKeyRecord> allRecords    = protocolStore.loadSignedPreKeys();

      allRecords.stream()
                .filter(r -> r.getId() != currentRecord.getId())
                .filter(r -> (now - r.getTimestamp()) > ARCHIVE_AGE)
                .sorted(Comparator.comparingLong(SignedPreKeyRecord::getTimestamp).reversed())
                .skip(1)
                .forEach(record -> {
                  Log.i(TAG, "Removing signed prekey record: " + record.getId() + " with timestamp: " + record.getTimestamp());
                  protocolStore.removeSignedPreKey(record.getId());
                });
    } catch (InvalidKeyIdException e) {
      Log.w(TAG, e);
    }
  }

  /**
   * Finds all of the signed prekeys that are older than the archive age, and archive all but the youngest of those.
   */
  public synchronized static void cleanLastResortKyberPreKeys(@NonNull SignalServiceAccountDataStore protocolStore, @NonNull PreKeyMetadataStore metadataStore) {
    Log.i(TAG, "Cleaning kyber prekeys...");

    int activeLastResortKeyId = metadataStore.getLastResortKyberPreKeyId();
    if (activeLastResortKeyId < 0) {
      return;
    }

    try {
      long                    now           = System.currentTimeMillis();
      KyberPreKeyRecord       currentRecord = protocolStore.loadKyberPreKey(activeLastResortKeyId);
      List<KyberPreKeyRecord> allRecords    = protocolStore.loadLastResortKyberPreKeys();

      allRecords.stream()
                .filter(r -> r.getId() != currentRecord.getId())
                .filter(r -> (now - r.getTimestamp()) > ARCHIVE_AGE)
                .sorted(Comparator.comparingLong(KyberPreKeyRecord::getTimestamp).reversed())
                .skip(1)
                .forEach(record -> {
                  Log.i(TAG, "Removing kyber prekey record: " + record.getId() + " with timestamp: " + record.getTimestamp());
                  protocolStore.removeKyberPreKey(record.getId());
                });
    } catch (InvalidKeyIdException e) {
      Log.w(TAG, e);
    }
  }

  public synchronized static void cleanOneTimePreKeys(@NonNull SignalServiceAccountDataStore protocolStore) {
    long threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90);
    int  minCount  = 200;

    protocolStore.deleteAllStaleOneTimeEcPreKeys(threshold, minCount);
    protocolStore.deleteAllStaleOneTimeKyberPreKeys(threshold, minCount);
  }
}
