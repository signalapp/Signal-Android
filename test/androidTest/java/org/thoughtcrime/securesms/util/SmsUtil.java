/*
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
package org.thoughtcrime.securesms.util;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SmsUtil {

  private static byte reverseByte(byte b) {
    return (byte) ((b & 0xF0) >> 4 | (b & 0x0F) << 4);
  }

  /*
  credit :D
  http://stackoverflow.com/a/12338541
   */
  @SuppressWarnings("unchecked")
  public static byte[] buildSmsPdu(String senderPstnNumber, String body) throws Exception{
    byte[]   scBytes     = PhoneNumberUtils.networkPortionToCalledPartyBCD("0000000000");
    byte[]   senderBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(senderPstnNumber);
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
    bo.write((byte) senderPstnNumber.length());
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

  @SuppressLint("NewApi")
  public static Intent buildSmsReceivedIntent(String senderPstnNumber, String smsBody) throws Exception {
    final Intent smsIntent = mock(Intent.class);
    final Bundle smsExtras = new Bundle();
    final byte[] smsPdu    = SmsUtil.buildSmsPdu(senderPstnNumber, smsBody);

    smsExtras.putSerializable("pdus", new Object[]{smsPdu});

    when(smsIntent.getAction()).thenReturn(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
    when(smsIntent.getExtras()).thenReturn(smsExtras);

    return smsIntent;
  }

}
