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

import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.thoughtcrime.securesms.crypto.MasterSecret;

import android.content.Context;
import android.util.Log;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.CharacterSets;
import ws.com.google.android.mms.pdu.PduBody;

public class SlideDeck {

  private final List<Slide> slides = new LinkedList<Slide>();
	
  public SlideDeck(Context context, MasterSecret masterSecret, PduBody body) {
    try {
      for (int i=0;i<body.getPartsNum();i++) {
	String contentType = new String(body.getPart(i).getContentType(), CharacterSets.MIMENAME_ISO_8859_1);
	if (ContentType.isImageType(contentType))
	  slides.add(new ImageSlide(context, masterSecret, body.getPart(i)));
	else if (ContentType.isVideoType(contentType))
	  slides.add(new VideoSlide(context, body.getPart(i)));
	else if (ContentType.isAudioType(contentType))
	  slides.add(new AudioSlide(context, body.getPart(i)));
	else if (ContentType.isTextType(contentType))
	  slides.add(new TextSlide(context, masterSecret, body.getPart(i)));
      }	
    } catch (UnsupportedEncodingException uee) {
      throw new AssertionError(uee);
    }
  }
	
  public SlideDeck() {
  }
	
  public void clear() {
    slides.clear();
  }
	
  public PduBody toPduBody() {
    PduBody body = new PduBody();
    Iterator<Slide> iterator = slides.iterator();
		
    while (iterator.hasNext())
      body.addPart(iterator.next().getPart());
		
    return body;
  }

  public void addSlide(Slide slide) {
    slides.add(slide);
  }
	
  public List<Slide> getSlides() {
    return slides;
  }
	
}
