package org.thoughtcrime.securesms.util;

import android.util.Log;

import org.thoughtcrime.securesms.dom.smil.SmilDocumentImpl;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.w3c.dom.smil.SMILDocument;
import org.w3c.dom.smil.SMILElement;
import org.w3c.dom.smil.SMILLayoutElement;
import org.w3c.dom.smil.SMILMediaElement;
import org.w3c.dom.smil.SMILParElement;
import org.w3c.dom.smil.SMILRegionElement;
import org.w3c.dom.smil.SMILRegionMediaElement;
import org.w3c.dom.smil.SMILRootLayoutElement;

public class SmilUtil {
  private static final String TAG = SmilUtil.class.getSimpleName();

  public static final int ROOT_HEIGHT = 1024;
  public static final int ROOT_WIDTH  = 1024;

  public static SMILDocument createSmilDocument(SlideDeck deck) {
    Log.w(TAG, "Creating SMIL document from SlideDeck.");

    SMILDocument document = new SmilDocumentImpl();

    SMILElement smilElement = (SMILElement) document.createElement("smil");
    document.appendChild(smilElement);

    SMILElement headElement = (SMILElement) document.createElement("head");
    smilElement.appendChild(headElement);

    SMILLayoutElement layoutElement = (SMILLayoutElement) document.createElement("layout");
    headElement.appendChild(layoutElement);

    SMILRootLayoutElement rootLayoutElement = (SMILRootLayoutElement) document.createElement("root-layout");
    rootLayoutElement.setWidth(ROOT_WIDTH);
    rootLayoutElement.setHeight(ROOT_HEIGHT);
    layoutElement.appendChild(rootLayoutElement);

    SMILElement bodyElement = (SMILElement) document.createElement("body");
    smilElement.appendChild(bodyElement);

    SMILParElement par = (SMILParElement) document.createElement("par");
    bodyElement.appendChild(par);

    for (Slide slide : deck.getSlides()) {
      SMILRegionElement regionElement = slide.getSmilRegion(document);
      SMILMediaElement  mediaElement  = slide.getMediaElement(document);

      if (regionElement != null) {
        ((SMILRegionMediaElement)mediaElement).setRegion(regionElement);
        layoutElement.appendChild(regionElement);
      }
      par.appendChild(mediaElement);
    }

    return document;
  }

  public static SMILMediaElement createMediaElement(String tag, SMILDocument document, String src) {
    SMILMediaElement mediaElement = (SMILMediaElement) document.createElement(tag);
    mediaElement.setSrc(escapeXML(src));
    return mediaElement;
  }

  private static String escapeXML(String str) {
    return str.replaceAll("&","&amp;")
              .replaceAll("<", "&lt;")
              .replaceAll(">", "&gt;")
              .replaceAll("\"", "&quot;")
              .replaceAll("'", "&apos;");
  }
}
