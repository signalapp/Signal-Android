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

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessageRecord;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsMessageRecord;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.MultimediaMessagePdu;
import ws.com.google.android.mms.pdu.NotificationInd;
import ws.com.google.android.mms.pdu.PduHeaders;
import android.content.Context;
import android.util.Log;

public class MmsFactory {

  public static MmsMessageRecord getMms(Context context, MasterSecret masterSecret, MessageRecord record, long mmsType, long box) throws MmsException {
    Log.w("MmsFactory", "MMS Type: " + mmsType);
    if (mmsType == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
      return getNotificationMmsRecord(context, record);
    } else {
      return getMediaMmsRecord(context, masterSecret, record, box);
    }
  }
	
  private static MmsMessageRecord getNotificationMmsRecord(Context context, MessageRecord record) throws MmsException {
    MmsDatabase database         = DatabaseFactory.getMmsDatabase(context);
    NotificationInd notification = database.getNotificationMessage(record.getId());
    return new MmsMessageRecord(record, notification.getContentLocation(), notification.getMessageSize(), 
				notification.getExpiry(), notification.getStatus(), notification.getTransactionId());
  }

  private static MmsMessageRecord getMediaMmsRecord(Context context, MasterSecret masterSecret, MessageRecord record, long box) throws MmsException {
    MmsDatabase database     = DatabaseFactory.getEncryptingMmsDatabase(context, masterSecret);
    MultimediaMessagePdu msg = database.getMediaMessage(record.getId());
    SlideDeck slideDeck      = new SlideDeck(context, masterSecret, msg.getBody());

    return new MmsMessageRecord(record, slideDeck, box);
  }
	
  //	private static void setBodyIfText(SlideModel slide, MessageRecord record) {
  //        if ((slide != null) && slide.hasText()) {
  //            TextModel tm = slide.getText();
  //
  //            if (tm.isDrmProtected()) {
  //            	record.setBody("DRM protected");
  //            } else {
  //            	record.setBody(tm.getText());
  //            }
  //        }		
  //	}
  //	
  //	private static SlideshowModel getSlideshowModel(Context context, long messageId) throws MmsException {
  //		LayoutManager.init(context);
  //		MmsDatabase database     = DatabaseFactory.getMmsDatabase(context);
  //		MultimediaMessagePdu msg = database.getMediaMessage(messageId);
  //        return SlideshowModel.createFromPduBody(context, msg.getBody());		
  //	}

	
}
