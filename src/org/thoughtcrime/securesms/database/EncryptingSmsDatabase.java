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
package org.thoughtcrime.securesms.database;

import org.thoughtcrime.securesms.crypto.AsymmetricMasterCipher;
import org.thoughtcrime.securesms.crypto.AsymmetricMasterSecret;
import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.protocol.Prefix;

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;
import android.telephony.SmsMessage;

public class EncryptingSmsDatabase extends SmsDatabase {

  public EncryptingSmsDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }	

  private String getAsymmetricEncryptedBody(AsymmetricMasterSecret masterSecret, String body) {
    AsymmetricMasterCipher bodyCipher = new AsymmetricMasterCipher(masterSecret);
    return Prefix.ASYMMETRIC_LOCAL_ENCRYPT + bodyCipher.encryptBody(body);
  }
	
  private String getEncryptedBody(MasterSecret masterSecret, String body) {
    MasterCipher bodyCipher = new MasterCipher(masterSecret);
    return Prefix.SYMMETRIC_ENCRYPT + bodyCipher.encryptBody(body);		
  }

  private long insertMessageSent(MasterSecret masterSecret, String address, long threadId, String body, long date, int type) {
    String encryptedBody = getEncryptedBody(masterSecret, body);
    return insertMessageSent(address, threadId, encryptedBody, date, type);		
  }

  public void updateSecureMessageBody(MasterSecret masterSecret, long messageId, String body) {
    String encryptedBody = getEncryptedBody(masterSecret, body);
    updateMessageBodyAndType(messageId, encryptedBody, Types.SECURE_RECEIVED_TYPE);
  }
	
  public void updateMessageBody(MasterSecret masterSecret, long messageId, String body) {
    String encryptedBody = getEncryptedBody(masterSecret, body);
    updateMessageBodyAndType(messageId, encryptedBody, Types.INBOX_TYPE);
  }
	
  public long insertMessageSent(MasterSecret masterSecret, String address, long threadId, String body, long date) {
    return insertMessageSent(masterSecret, address, threadId, body, date, Types.ENCRYPTED_OUTBOX_TYPE);
  }
	
  public long insertSecureMessageSent(MasterSecret masterSecret, String address, long threadId, String body, long date) {
    return insertMessageSent(masterSecret, address, threadId, body, date, Types.ENCRYPTING_TYPE);
  }
	
  public long insertMessageReceived(MasterSecret masterSecret, SmsMessage message, String body) {
    String encryptedBody = getEncryptedBody(masterSecret, body);
    return insertMessageReceived(message, encryptedBody);
  }
	
  public long insertMessageReceived(AsymmetricMasterSecret masterSecret, SmsMessage message, String body) {
    String encryptedBody = getAsymmetricEncryptedBody(masterSecret, body);
    return insertSecureMessageReceived(message, encryptedBody);
  }

}
