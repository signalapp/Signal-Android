/** 
 * Copyright (C) 2011 Whisper Systems
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

import android.content.Context;
import android.util.Log;

import org.w3c.dom.smil.SMILDocument;
import org.w3c.dom.smil.SMILMediaElement;
import org.w3c.dom.smil.SMILRegionElement;

import java.io.UnsupportedEncodingException;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.CharacterSets;
import ws.com.google.android.mms.pdu.PduPart;

public class TextSlide extends Slide {

  public TextSlide(Context context, String message) {
    super(context, getPartForMessage(message));
  }

  private static PduPart getPartForMessage(String message) {
    PduPart part = new PduPart();

    try {
      part.setData(message.getBytes(CharacterSets.MIMENAME_UTF_8));

      if (part.getData().length == 0)
        throw new AssertionError("Part data should not be zero!");
            
    } catch (UnsupportedEncodingException e) {
      Log.w("TextSlide", "ISO_8859_1 must be supported!", e);
      part.setData("Unsupported character set!".getBytes());
    }

    part.setCharset(CharacterSets.UTF_8);
    part.setContentType(ContentType.TEXT_PLAIN.getBytes());
    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setName(("Text"+System.currentTimeMillis()).getBytes());
        
    return part;
  }

  @Override
  public SMILRegionElement getSmilRegion(SMILDocument document) {
    return null;
  }

  @Override
  public SMILMediaElement getMediaElement(SMILDocument document) {
    return null;
  }
}
