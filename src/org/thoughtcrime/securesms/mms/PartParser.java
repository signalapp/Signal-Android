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
          partText = new String(body.getPart(i).getData(),
                                CharacterSets.getMimeName(body.getPart(i).getCharset()));
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
}
