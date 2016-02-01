package org.privatechats.securesms.util;

import android.util.Log;

import org.privatechats.securesms.dom.smil.SmilDocumentImpl;
import org.privatechats.securesms.dom.smil.parser.SmilXmlSerializer;
import org.privatechats.securesms.mms.PartParser;
import org.w3c.dom.smil.SMILDocument;
import org.w3c.dom.smil.SMILElement;
import org.w3c.dom.smil.SMILLayoutElement;
import org.w3c.dom.smil.SMILMediaElement;
import org.w3c.dom.smil.SMILParElement;
import org.w3c.dom.smil.SMILRegionElement;
import org.w3c.dom.smil.SMILRegionMediaElement;
import org.w3c.dom.smil.SMILRootLayoutElement;

import java.io.ByteArrayOutputStream;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.PduPart;

public class SmilUtil {
  private static final String TAG = SmilUtil.class.getSimpleName();

  public static final int ROOT_HEIGHT = 1024;
  public static final int ROOT_WIDTH  = 1024;

  public static PduBody getSmilBody(PduBody body) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    SmilXmlSerializer.serialize(SmilUtil.createSmilDocument(body), out);
    PduPart smilPart = new PduPart();
    smilPart.setContentId("smil".getBytes());
    smilPart.setContentLocation("smil.xml".getBytes());
    smilPart.setContentType(ContentType.APP_SMIL.getBytes());
    smilPart.setData(out.toByteArray());
    body.addPart(0, smilPart);

    return body;
  }

  private static SMILDocument createSmilDocument(PduBody body) {
    Log.w(TAG, "Creating SMIL document from PduBody.");

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

    for (int i=0; i<body.getPartsNum(); i++) {
      PduPart part = body.getPart(i);
      SMILRegionElement regionElement = getRegion(document, part);
      SMILMediaElement  mediaElement  = getMediaElement(document, part);

      if (regionElement != null) {
        ((SMILRegionMediaElement)mediaElement).setRegion(regionElement);
        layoutElement.appendChild(regionElement);
      }
      par.appendChild(mediaElement);
    }

    return document;
  }

  private static SMILRegionElement getRegion(SMILDocument document, PduPart part) {
    if (PartParser.isAudio(part)) return null;

    SMILRegionElement region = (SMILRegionElement) document.createElement("region");
    if (PartParser.isText(part)) {
      region.setId("Text");
      region.setTop(SmilUtil.ROOT_HEIGHT);
      region.setHeight(50);
    } else {
      region.setId("Image");
      region.setTop(0);
      region.setHeight(SmilUtil.ROOT_HEIGHT);
    }
    region.setLeft(0);
    region.setWidth(SmilUtil.ROOT_WIDTH);
    region.setFit("meet");
    return region;
  }

  private static SMILMediaElement getMediaElement(SMILDocument document, PduPart part) {
    final String tag;
    if (PartParser.isImage(part)) {
      tag = "img";
    } else if (PartParser.isAudio(part)) {
      tag = "audio";
    } else if (PartParser.isVideo(part)) {
      tag = "video";
    } else if (PartParser.isApp(part)) {
      tag = "app";
    } else if (PartParser.isText(part)) {
      tag = "text";
    } else {
      tag = "ref";
    }
    return createMediaElement(tag, document, new String(part.getName() == null
                                                        ? new byte[]{}
                                                        : part.getName()));
  }

  private static SMILMediaElement createMediaElement(String tag, SMILDocument document, String src) {
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
