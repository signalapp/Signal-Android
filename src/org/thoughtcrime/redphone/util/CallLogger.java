//package org.thoughtcrime.redphone.util;
//
//import android.content.ContentValues;
//import android.content.Context;
//import android.net.Uri;
//import android.provider.CallLog.Calls;
//import android.util.Log;
//
//import org.thoughtcrime.redphone.contacts.PersonInfo;
//
//public class CallLogger {
//  private static final String TAG = CallLogger.class.getName();
//  private static ContentValues getCallLogContentValues(Context context, String number, long timestamp) {
//    PersonInfo pi        = PersonInfo.getInstance(context, number);
//    ContentValues values = new ContentValues();
//
//    values.put(Calls.DATE, System.currentTimeMillis());
//    values.put(Calls.NUMBER, number);
//    values.put(Calls.CACHED_NAME, pi.getName() );
//    values.put(Calls.TYPE, pi.getType() );
//
//    return values;
//  }
//
//  private static ContentValues getCallLogContentValues(Context context, String number) {
//    return getCallLogContentValues(context, number, System.currentTimeMillis());
//  }
//
//  public static void logMissedCall(Context context, String number, long timestamp) {
//    ContentValues values = getCallLogContentValues(context, number, timestamp);
//    values.put(Calls.TYPE, Calls.MISSED_TYPE);
//    context.getContentResolver().insert(Calls.CONTENT_URI, values);
//  }
//
//  public static CallRecord logOutgoingCall(Context context, String number) {
//    ContentValues values = getCallLogContentValues(context, number);
//    values.put(Calls.TYPE, Calls.OUTGOING_TYPE);
//    try{
//      Uri uri = context.getContentResolver().insert(Calls.CONTENT_URI, values);
//      return new CallRecord(context, uri);
//    } catch (IllegalArgumentException e ) {
//      Log.w(TAG, "Failed call log insert", e);
//    }
//    return null;
//  }
//
//  public static CallRecord logIncomingCall(Context context, String number) {
//    ContentValues values = getCallLogContentValues(context, number);
//    values.put(Calls.TYPE, Calls.INCOMING_TYPE);
//    Uri recordUri = context.getContentResolver().insert(Calls.CONTENT_URI, values);
//    return new CallRecord(context, recordUri);
//  }
//
//  public static class CallRecord {
//    private final Context context;
//    private final Uri uri;
//    private final long startTimeMillis;
//
//    private CallRecord(Context context, Uri callRecordUri) {
//      this.context = context;
//      this.uri = callRecordUri;
//      startTimeMillis = System.currentTimeMillis();
//    }
//
//    public void finishCall() {
//      int duration = (int)((System.currentTimeMillis() - startTimeMillis)/1000);
//      ContentValues values = new ContentValues();
//      values.put(Calls.DURATION, duration);
//      if (uri != null) {
//        context.getContentResolver().update(uri, values, null, null);
//      }
//    }
//  }
//}
