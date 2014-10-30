package org.thoughtcrime.securesms.service;

import android.test.AndroidTestCase;

import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyStore;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.push.SignedPreKeyEntity;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class PreKeyServiceTest extends AndroidTestCase {

  public void testSignedPreKeyRotationNotRegistered() throws IOException {
    SignedPreKeyStore signedPreKeyStore = mock(SignedPreKeyStore.class);
    PushServiceSocket pushServiceSocket = mock(PushServiceSocket.class);

    when(pushServiceSocket.getCurrentSignedPreKey()).thenReturn(null);

    PreKeyService.CleanSignedPreKeysTask cleanTask = new PreKeyService.CleanSignedPreKeysTask(signedPreKeyStore,
                                                                                              pushServiceSocket);

    cleanTask.run();

    verify(pushServiceSocket).getCurrentSignedPreKey();
    verifyNoMoreInteractions(signedPreKeyStore);
  }

  public void testSignedPreKeyEviction() throws Exception {
    SignedPreKeyStore  signedPreKeyStore         = mock(SignedPreKeyStore.class);
    PushServiceSocket  pushServiceSocket         = mock(PushServiceSocket.class);
    SignedPreKeyEntity currentSignedPreKeyEntity = mock(SignedPreKeyEntity.class);

    when(currentSignedPreKeyEntity.getKeyId()).thenReturn(3133);
    when(pushServiceSocket.getCurrentSignedPreKey()).thenReturn(currentSignedPreKeyEntity);

    final SignedPreKeyRecord currentRecord = new SignedPreKeyRecord(3133, System.currentTimeMillis(), Curve.generateKeyPair(), new byte[64]);

    List<SignedPreKeyRecord> records = new LinkedList<SignedPreKeyRecord>() {{
      add(new SignedPreKeyRecord(1, 10, Curve.generateKeyPair(), new byte[32]));
      add(new SignedPreKeyRecord(2, 11, Curve.generateKeyPair(), new byte[32]));
      add(new SignedPreKeyRecord(3, System.currentTimeMillis() - 90, Curve.generateKeyPair(), new byte[64]));
      add(new SignedPreKeyRecord(4, System.currentTimeMillis() - 100, Curve.generateKeyPair(), new byte[64]));
      add(currentRecord);
    }};

    when(signedPreKeyStore.loadSignedPreKeys()).thenReturn(records);
    when(signedPreKeyStore.loadSignedPreKey(eq(3133))).thenReturn(currentRecord);

    PreKeyService.CleanSignedPreKeysTask cleanTask = new PreKeyService.CleanSignedPreKeysTask(signedPreKeyStore, pushServiceSocket);
    cleanTask.run();

    verify(signedPreKeyStore).removeSignedPreKey(eq(1));
    verify(signedPreKeyStore).removeSignedPreKey(eq(2));
    verify(signedPreKeyStore, times(2)).removeSignedPreKey(anyInt());
  }

  public void testSignedPreKeyNoEviction() throws Exception {
    SignedPreKeyStore  signedPreKeyStore         = mock(SignedPreKeyStore.class);
    PushServiceSocket  pushServiceSocket         = mock(PushServiceSocket.class);
    SignedPreKeyEntity currentSignedPreKeyEntity = mock(SignedPreKeyEntity.class);

    when(currentSignedPreKeyEntity.getKeyId()).thenReturn(3133);
    when(pushServiceSocket.getCurrentSignedPreKey()).thenReturn(currentSignedPreKeyEntity);

    final SignedPreKeyRecord currentRecord = new SignedPreKeyRecord(3133, System.currentTimeMillis(), Curve.generateKeyPair(), new byte[64]);

    List<SignedPreKeyRecord> records = new LinkedList<SignedPreKeyRecord>() {{
      add(currentRecord);
    }};

    when(signedPreKeyStore.loadSignedPreKeys()).thenReturn(records);
    when(signedPreKeyStore.loadSignedPreKey(eq(3133))).thenReturn(currentRecord);

    PreKeyService.CleanSignedPreKeysTask cleanTask = new PreKeyService.CleanSignedPreKeysTask(signedPreKeyStore, pushServiceSocket);
    cleanTask.run();

    verify(signedPreKeyStore, never()).removeSignedPreKey(anyInt());
  }
}
