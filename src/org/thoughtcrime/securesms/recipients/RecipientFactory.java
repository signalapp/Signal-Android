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
package org.thoughtcrime.securesms.recipients;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.util.NumberUtil;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

public class RecipientFactory {

  private static final Map<String,Recipient> recipientCache    = Collections.synchronizedMap(new LRUHashMap<String,Recipient>());
  private static final Map<String,Recipient> recipientIdCache  = Collections.synchronizedMap(new LRUHashMap<String,Recipient>());
  private static final Map<Uri,Recipient>    recipientUriCache = Collections.synchronizedMap(new HashMap<Uri,Recipient>());	
	
  private static RecipientProvider provider;
	
  static {
    String className;
        
    int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
        
    if (sdkVersion <= Build.VERSION_CODES.DONUT) className = "OldRecipientProvider";
    else                                         className = "NewRecipientProvider";

    try {
      Class<? extends RecipientProvider> clazz =
	Class.forName("org.thoughtcrime.securesms.recipients." + className)
	.asSubclass(RecipientProvider.class);
      provider = clazz.newInstance();
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }
	
  public static RecipientProvider getRecipientProvider() {
    return provider;
  }
	
  public static Recipient getRecipientForUri(Context context, Uri uri) {
    Recipient recipient = recipientUriCache.get(uri);
		
    if (recipient == null)
      recipient = getRecipientFromProvider(context, uri);
		
    return recipient;
  }
	
  public static Recipients getRecipientsForIds(Context context, String recipientIds) {
    ArrayList<Recipient> results = new ArrayList<Recipient>();
    StringTokenizer tokenizer    = new StringTokenizer(recipientIds.trim(), " ");
		
    while (tokenizer.hasMoreTokens()) {
      String recipientId  = tokenizer.nextToken();
			
      Recipient recipient = recipientIdCache.get(recipientId);
			
      if (recipient == null)
        recipient = getRecipientFromProviderId(context, recipientId);
			
      if (recipient == null)
        recipient = getNullIdRecipient(context, recipientId);
					
      results.add(recipient);
    }
		
    return new Recipients(results);
  }
	
  private static Recipient getRecipientForNumber(Context context, String number) {
    Recipient recipient = recipientCache.get(number);
		
    if (recipient == null)
      recipient = getRecipientFromProvider(context, number);
		
    if (recipient == null)
      recipient = getNullRecipient(context, number);
		
    return recipient;
  }
	
  public static Recipients getRecipientsFromString(Context context, String rawText) throws RecipientFormattingException {
    ArrayList<Recipient> results = new ArrayList<Recipient>();
    StringTokenizer tokenizer    = new StringTokenizer(rawText, ",");

    while (tokenizer.hasMoreTokens()) {
      Recipient recipient = parseRecipient(context, tokenizer.nextToken());
      if( recipient != null ) 					
        results.add(recipient);						
    }

    return new Recipients(results);
  }
	
  private static Recipient getNullIdRecipient(Context context, String recipientId) {
    String address      = DatabaseFactory.getAddressDatabase(context).getAddressFromId(recipientId);
    Recipient recipient = getNullRecipient(context, address);
    recipientIdCache.put(recipientId, recipient);
    return recipient;
  }
	
  private static Recipient getNullRecipient(Context context, String number) {
    Recipient nullRecipient = new Recipient(null, number, BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_contact_picture));
    recipientCache.put(number, nullRecipient);
    return nullRecipient;
  }
		
  private static Recipient getRecipientFromProviderId(Context context, String recipientId) {
    Log.w("RecipientFactory", "Hitting recipient provider [ID].");

    String address      = DatabaseFactory.getAddressDatabase(context).getAddressFromId(recipientId);
    Recipient recipient = getRecipientFromProvider(context, address);

    recipientIdCache.put(recipientId, recipient);
    return recipient;
  }

  private static Recipient getRecipientFromProvider(Context context, Uri uri) {
    Recipient recipient = provider.getRecipient(context, uri);
		
    if (recipient != null) 
      recipientUriCache.put(uri, recipient);
		
    return recipient;
  }

  private static Recipient getRecipientFromProvider(Context context, String number) {
    Recipient recipient = provider.getRecipient(context, number);
		
    if (recipient != null)
      recipientCache.put(number, recipient);

    return recipient;
  }
	
  private static String parseBracketedNumber(String recipient) throws RecipientFormattingException {
    int begin    = recipient.indexOf('<');
    int end      = recipient.indexOf('>');
    String value = recipient.substring(begin + 1, end);
		
    if (PhoneNumberUtils.isWellFormedSmsAddress(value))
      return value;
    else
      throw new RecipientFormattingException("Bracketed value: " + value + " is not valid.");
  }
	
  private static Recipient parseRecipient(Context context, String recipient) throws RecipientFormattingException {
    recipient = recipient.trim();

    if( recipient.length() == 0 ) 
      return null;

    if ((recipient.indexOf('<') != -1) && (recipient.indexOf('>') != -1))
      return getRecipientForNumber(context, parseBracketedNumber(recipient));

    if (NumberUtil.isValidSmsOrEmail(recipient))
      return getRecipientForNumber(context, recipient);
				
    throw new RecipientFormattingException("Recipient: " + recipient + " is badly formatted.");
  }

  public static void clearCache() {
    recipientCache.clear();
    recipientIdCache.clear();
    recipientUriCache.clear();
  }
	
  private static class LRUHashMap<K,V> extends LinkedHashMap<K,V> {
    private static final int MAX_SIZE = 1000;
    @Override 
    protected boolean removeEldestEntry (Map.Entry<K,V> eldest) {
      return size() > MAX_SIZE; 
    }
  }
}
