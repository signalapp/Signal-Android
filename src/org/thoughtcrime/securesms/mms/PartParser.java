/**
 * Copyright (C) 2013-2014 Open WhisperSystems
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
package org.thoughtcrime.securesms.mms;

import android.util.Log;

import org.thoughtcrime.securesms.util.Util;

import java.io.UnsupportedEncodingException;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.CharacterSets;
import ws.com.google.android.mms.pdu.PduBody;

public class PartParser {
  public static String getMessageText(PduBody body) {
    String bodyText = null;

    for (int i=0;i<body.getPartsNum();i++) {
      if (ContentType.isTextType(Util.toIsoString(body.getPart(i).getContentType()))) {
        String partText;

        try {
          String characterSet = CharacterSets.getMimeName(body.getPart(i).getCharset());

          if (characterSet.equals(CharacterSets.MIMENAME_ANY_CHARSET))
            characterSet = CharacterSets.MIMENAME_ISO_8859_1;

          partText = new String(body.getPart(i).getData(), characterSet);
        } catch (UnsupportedEncodingException e) {
          Log.w("PartParser", e);
          partText = "Unsupported Encoding!";
        }

        bodyText = (bodyText == null) ? partText : bodyText + " " + partText;
      }
    }

    return bodyText;
  }

  public static PduBody getNonTextParts(PduBody body) {
    PduBody stripped = new PduBody();

    for (int i=0;i<body.getPartsNum();i++) {
      if (!ContentType.isTextType(Util.toIsoString(body.getPart(i).getContentType()))) {
        stripped.addPart(body.getPart(i));
      }
    }

    return stripped;
  }

  public static int getDisplayablePartCount(PduBody body) {
    int partCount = 0;

    for (int i=0;i<body.getPartsNum();i++) {
      String contentType = Util.toIsoString(body.getPart(i).getContentType());

      if (ContentType.isImageType(contentType) ||
          ContentType.isAudioType(contentType) ||
          ContentType.isVideoType(contentType))
      {
        partCount++;
      }
    }

    return partCount;
  }
}
