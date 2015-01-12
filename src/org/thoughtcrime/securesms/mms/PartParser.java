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

  public static PduBody getSupportedMediaParts(PduBody body) {
    PduBody stripped = new PduBody();

    for (int i=0;i<body.getPartsNum();i++) {
      if (isDisplayableMedia(Util.toIsoString(body.getPart(i).getContentType()))) {
        stripped.addPart(body.getPart(i));
      }
    }

    return stripped;
  }

  public static int getSupportedMediaPartCount(PduBody body) {
    int partCount = 0;

    for (int i=0;i<body.getPartsNum();i++) {
      String contentType = Util.toIsoString(body.getPart(i).getContentType());

      if (isDisplayableMedia(contentType)) {
        partCount++;
      }
    }

    return partCount;
  }

  private static boolean isDisplayableMedia(String contentType) {
    return ContentType.isImageType(contentType) ||
           ContentType.isAudioType(contentType) ||
           ContentType.isVideoType(contentType);
  }
}
