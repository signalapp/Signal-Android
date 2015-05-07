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
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import org.mockito.ArgumentCaptor;
import org.thoughtcrime.securesms.TextSecureTestCase;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SmsListenerTest extends TextSecureTestCase {

  private static final String CHALLENGE_SMS_3_3         = "Your TextSecure verification code: 337-337";
  private static final String CHALLENGE_SMS_3_4         = "Your TextSecure verification code: 337-1337";
  private static final String CHALLENGE_SMS_4_3         = "Your TextSecure verification code: 1337-337";
  private static final String CHALLENGE_SMS_4_4         = "Your TextSecure verification code: 1337-1337";
  private static final String CHALLENGE_SMS_4_4_PREPEND = "XXXYour TextSecure verification code: 1337-1337";
  private static final String CHALLENGE_SMS_4_4_APPEND  = "Your TextSecure verification code: 1337-1337XXX";
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

  /*
  credit :D
  http://stackoverflow.com/a/12338541
   */
  private static byte[] buildSmsPdu(String sender, String body) throws Exception{
    byte[]   scBytes     = PhoneNumberUtils.networkPortionToCalledPartyBCD("0000000000");
    byte[]   senderBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(sender);
    int      lsmcs       = scBytes.length;
    byte[]   dateBytes   = new byte[7];
    Calendar calendar    = new GregorianCalendar();

    dateBytes[0] = reverseByte((byte) (calendar.get(Calendar.YEAR)));
    dateBytes[1] = reverseByte((byte) (calendar.get(Calendar.MONTH) + 1));
    dateBytes[2] = reverseByte((byte) (calendar.get(Calendar.DAY_OF_MONTH)));
    dateBytes[3] = reverseByte((byte) (calendar.get(Calendar.HOUR_OF_DAY)));
    dateBytes[4] = reverseByte((byte) (calendar.get(Calendar.MINUTE)));
    dateBytes[5] = reverseByte((byte) (calendar.get(Calendar.SECOND)));
    dateBytes[6] = reverseByte((byte) ((calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)) / (60 * 1000 * 15)));

    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    bo.write(lsmcs);
    bo.write(scBytes);
    bo.write(0x04);
    bo.write((byte) sender.length());
    bo.write(senderBytes);
    bo.write(0x00);
    bo.write(0x00);
    bo.write(dateBytes);

    String sReflectedClassName   = "com.android.internal.telephony.GsmAlphabet";
    Class  cReflectedNFCExtras   = Class.forName(sReflectedClassName);
    Method stringToGsm7BitPacked = cReflectedNFCExtras.getMethod("stringToGsm7BitPacked", new Class[] { String.class });

    stringToGsm7BitPacked.setAccessible(true);
    byte[] bodybytes = (byte[]) stringToGsm7BitPacked.invoke(null, body);
    bo.write(bodybytes);

    return bo.toByteArray();
  }

  private static byte reverseByte(byte b) {
    return (byte) ((b & 0xF0) >> 4 | (b & 0x0F) << 4);
  }

  @SuppressLint("NewApi")
  private Intent buildSmsReceivedIntent(String smsBody) throws Exception {
    final Intent smsIntent = mock(Intent.class);
    final Bundle smsExtras = new Bundle();
    final byte[] smsPdu    = buildSmsPdu("15555555555", smsBody);

    smsExtras.putSerializable("pdus", new Object[]{smsPdu});

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
