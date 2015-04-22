/**
 * Copyright (C) 2015 Open Whisper Systems
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

package org.thoughtcrime.securesms.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Telephony;
import android.util.Log;

import org.mockito.ArgumentCaptor;
import org.spongycastle.util.encoders.Hex;
import org.thoughtcrime.securesms.TextSecureTestCase;

import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SmsListenerTest extends TextSecureTestCase {

  /*
  http://rednaxela.net/pdu.php
    'Your TextSecure verification code: 337-337'
    'Your TextSecure verification code: 337-1337'
    'Your TextSecure verification code: 1337-337'
    'Your TextSecure verification code: 1337-1337'
    'XXXYour TextSecure verification code: 1337-1337'
    'Your TextSecure verification code: 1337-1337XXX'
  */
  private static final String CHALLENGE_SMS_3_3         = "07914400000000F001000B811000000000F000002AD9775D0EA296F1F469795C979741F6B23C6D4E8FC3F4F4DB0D1ABFC9651D6836BBB566B31B";
  private static final String CHALLENGE_SMS_3_4         = "07914400000000F001000B811000000000F000002BD9775D0EA296F1F469795C979741F6B23C6D4E8FC3F4F4DB0D1ABFC9651D6836BBB562B3D90D";
  private static final String CHALLENGE_SMS_4_3         = "07914400000000F001000B811000000000F000002BD9775D0EA296F1F469795C979741F6B23C6D4E8FC3F4F4DB0D1ABFC9651D28369BDD5AB3D90D";
  private static final String CHALLENGE_SMS_4_4         = "07914400000000F001000B811000000000F000002CD9775D0EA296F1F469795C979741F6B23C6D4E8FC3F4F4DB0D1ABFC9651D28369BDD5AB1D9EC06";
  private static final String CHALLENGE_SMS_4_4_PREPEND = "07914400000000F001000B811000000000F000002F582C36FBAECB41D4329E3E2D8FEBF232C85E96A7CDE971989E7EBB41E337B9AC03C566B35B2B369BDD00";
  private static final String CHALLENGE_SMS_4_4_APPEND  = "07914400000000F001000B811000000000F000002FD9775D0EA296F1F469795C979741F6B23C6D4E8FC3F4F4DB0D1ABFC9651D28369BDD5AB1D9EC86C56201";
  private static final String[] CHALLENGE_SMS = {
      CHALLENGE_SMS_3_3, CHALLENGE_SMS_3_4,         CHALLENGE_SMS_4_3,
      CHALLENGE_SMS_4_4, CHALLENGE_SMS_4_4_PREPEND, CHALLENGE_SMS_4_4_APPEND
  };

  private static final String CHALLENGE_3_3 = "337337";
  private static final String CHALLENGE_3_4 = "3371337";
  private static final String CHALLENGE_4_3 = "1337337";
  private static final String CHALLENGE_4_4 = "13371337";
  private static final String[] CHALLENGES = {
      CHALLENGE_3_3, CHALLENGE_3_4, CHALLENGE_4_3,
      CHALLENGE_4_4, CHALLENGE_4_4, CHALLENGE_4_4,
  };

  @SuppressLint("NewApi")
  private Intent buildSmsReceivedIntent(String encodedPdu) throws Exception {
    final Intent smsIntent = mock(Intent.class);
    final Bundle smsExtras = new Bundle();

    smsExtras.putSerializable("pdus", new Object[]{Hex.decode(encodedPdu)});

    when(smsIntent.getAction()).thenReturn(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
    when(smsIntent.getExtras()).thenReturn(smsExtras);

    return smsIntent;
  }

  public void testReceiveChallenges() throws Exception {
    final SmsListener smsListener = new SmsListener();

    for (int i = 0; i < CHALLENGES.length; i++) {
      final String CHALLENGE     = CHALLENGES[i];
      final String CHALLENGE_SMS = SmsListenerTest.CHALLENGE_SMS[i];

      final Context                mockContext     = mock(Context.class);
      final SharedPreferences      mockPreferences = mock(SharedPreferences.class);
      final ArgumentCaptor<Intent> intentCaptor    = ArgumentCaptor.forClass(Intent.class);

      when(mockContext.getPackageName()).thenReturn(getContext().getPackageName());
      when(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPreferences);
      when(mockPreferences.getBoolean(contains("pref_verifying"), anyBoolean())).thenReturn(true);

      try {
        smsListener.onReceive(mockContext, buildSmsReceivedIntent(CHALLENGE_SMS));
      } catch (IllegalStateException e) {
        Log.d(getClass().getName(), "some api levels are picky with abortBroadcast()");
      }

      verify(mockContext, times(1)).sendBroadcast(intentCaptor.capture());

      final Intent sendIntent = intentCaptor.getValue();
      assertTrue(sendIntent.getAction().equals(RegistrationService.CHALLENGE_EVENT));
      assertTrue(sendIntent.getStringExtra(RegistrationService.CHALLENGE_EXTRA).equals(CHALLENGE));
    }
  }
}
