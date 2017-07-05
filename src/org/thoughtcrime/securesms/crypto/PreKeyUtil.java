/**
 * Copyright (C) 2013 Open Whisper Systems
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

import android.content.Context;
import android.util.Log;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.thoughtcrime.securesms.crypto.storage.TextSecurePreKeyStore;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.PreKeyStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyStore;
import org.whispersystems.libsignal.util.Medium;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;

public class PreKeyUtil {

  private static final String TAG = PreKeyUtil.class.getName();

  private static final int BATCH_SIZE = 100;

  public static List<PreKeyRecord> generatePreKeys(Context context) {
    PreKeyStore        preKeyStore    = new TextSecurePreKeyStore(context);
    List<PreKeyRecord> records        = new LinkedList<>();
    int                preKeyIdOffset = getNextPreKeyId(context);

    for (int i=0;i<BATCH_SIZE;i++) {
      int          preKeyId = (preKeyIdOffset + i) % Medium.MAX_VALUE;
      ECKeyPair    keyPair  = Curve.generateKeyPair();
      PreKeyRecord record   = new PreKeyRecord(preKeyId, keyPair);

      preKeyStore.storePreKey(preKeyId, record);
      records.add(record);
    }

    setNextPreKeyId(context, (preKeyIdOffset + BATCH_SIZE + 1) % Medium.MAX_VALUE);
    return records;
  }

  public static SignedPreKeyRecord generateSignedPreKey(Context context, IdentityKeyPair identityKeyPair, boolean active)
  {
    try {
      SignedPreKeyStore  signedPreKeyStore = new TextSecurePreKeyStore(context);
      int                signedPreKeyId    = getNextSignedPreKeyId(context);
      ECKeyPair          keyPair           = Curve.generateKeyPair();
      byte[]             signature         = Curve.calculateSignature(identityKeyPair.getPrivateKey(), keyPair.getPublicKey().serialize());
      SignedPreKeyRecord record            = new SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), keyPair, signature);

      signedPreKeyStore.storeSignedPreKey(signedPreKeyId, record);
      setNextSignedPreKeyId(context, (signedPreKeyId + 1) % Medium.MAX_VALUE);

      if (active) {
        setActiveSignedPreKeyId(context, signedPreKeyId);
      }

      return record;
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  private static synchronized void setNextPreKeyId(Context context, int id) {
    try {
      File             nextFile = new File(getPreKeysDirectory(context), PreKeyIndex.FILE_NAME);
      FileOutputStream fout     = new FileOutputStream(nextFile);
      fout.write(JsonUtils.toJson(new PreKeyIndex(id)).getBytes());
      fout.close();
    } catch (IOException e) {
      Log.w("PreKeyUtil", e);
    }
  }

  private static synchronized void setNextSignedPreKeyId(Context context, int id) {
    try {
      SignedPreKeyIndex index = getSignedPreKeyIndex(context).or(new SignedPreKeyIndex());
      index.nextSignedPreKeyId = id;

      setSignedPreKeyIndex(context, index);
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  public static synchronized void setActiveSignedPreKeyId(Context context, int id) {
    try {
      SignedPreKeyIndex index = getSignedPreKeyIndex(context).or(new SignedPreKeyIndex());
      index.activeSignedPreKeyId = id;

      setSignedPreKeyIndex(context, index);
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  public static synchronized int getActiveSignedPreKeyId(Context context) {
    Optional<SignedPreKeyIndex> index = getSignedPreKeyIndex(context);

    if (index.isPresent()) return index.get().activeSignedPreKeyId;
    else                   return -1;
  }

  private static synchronized int getNextPreKeyId(Context context) {
    try {
      File nextFile = new File(getPreKeysDirectory(context), PreKeyIndex.FILE_NAME);

      if (!nextFile.exists()) {
        return Util.getSecureRandom().nextInt(Medium.MAX_VALUE);
      } else {
        InputStreamReader reader = new InputStreamReader(new FileInputStream(nextFile));
        PreKeyIndex       index  = JsonUtils.fromJson(reader, PreKeyIndex.class);
        reader.close();
        return index.nextPreKeyId;
      }
    } catch (IOException e) {
      Log.w("PreKeyUtil", e);
      return Util.getSecureRandom().nextInt(Medium.MAX_VALUE);
    }
  }

  private static synchronized int getNextSignedPreKeyId(Context context) {
    try {
      File nextFile = new File(getSignedPreKeysDirectory(context), SignedPreKeyIndex.FILE_NAME);

      if (!nextFile.exists()) {
        return Util.getSecureRandom().nextInt(Medium.MAX_VALUE);
      } else {
        InputStreamReader reader = new InputStreamReader(new FileInputStream(nextFile));
        SignedPreKeyIndex index  = JsonUtils.fromJson(reader, SignedPreKeyIndex.class);
        reader.close();
        return index.nextSignedPreKeyId;
      }
    } catch (IOException e) {
      Log.w("PreKeyUtil", e);
      return Util.getSecureRandom().nextInt(Medium.MAX_VALUE);
    }
  }

  private static synchronized Optional<SignedPreKeyIndex> getSignedPreKeyIndex(Context context) {
    File indexFile = new File(getSignedPreKeysDirectory(context), SignedPreKeyIndex.FILE_NAME);

    if (!indexFile.exists()) {
      return Optional.absent();
    }

    try {
      InputStreamReader reader = new InputStreamReader(new FileInputStream(indexFile));
      SignedPreKeyIndex index  = JsonUtils.fromJson(reader, SignedPreKeyIndex.class);
      reader.close();

      return Optional.of(index);
    } catch (IOException e) {
      Log.w(TAG, e);
      return Optional.absent();
    }
  }

  private static synchronized void setSignedPreKeyIndex(Context context, SignedPreKeyIndex index) throws IOException {
    File             indexFile = new File(getSignedPreKeysDirectory(context), SignedPreKeyIndex.FILE_NAME);
    FileOutputStream fout     = new FileOutputStream(indexFile);
    fout.write(JsonUtils.toJson(index).getBytes());
    fout.close();
  }

  private static File getPreKeysDirectory(Context context) {
    return getKeysDirectory(context, TextSecurePreKeyStore.PREKEY_DIRECTORY);
  }

  private static File getSignedPreKeysDirectory(Context context) {
    return getKeysDirectory(context, TextSecurePreKeyStore.SIGNED_PREKEY_DIRECTORY);
  }

  private static File getKeysDirectory(Context context, String name) {
    File directory = new File(context.getFilesDir(), name);

    if (!directory.exists())
      directory.mkdirs();

    return directory;
  }

  private static class PreKeyIndex {
    public static final String FILE_NAME = "index.dat";

    @JsonProperty
    private int nextPreKeyId;

    public PreKeyIndex() {}

    public PreKeyIndex(int nextPreKeyId) {
      this.nextPreKeyId = nextPreKeyId;
    }
  }

  private static class SignedPreKeyIndex {
    public static final String FILE_NAME = "index.dat";

    @JsonProperty
    private int nextSignedPreKeyId;

    @JsonProperty
    private int activeSignedPreKeyId = -1;

    public SignedPreKeyIndex() {}

  }


}
