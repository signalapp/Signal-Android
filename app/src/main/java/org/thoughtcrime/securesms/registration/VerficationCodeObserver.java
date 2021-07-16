package org.thoughtcrime.securesms.registration;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import androidx.core.content.ContextCompat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VerficationCodeObserver extends ContentObserver {
  private Context mContext;
  private Handler mHandler;
  private int mReceivedCode = 1;

  public VerficationCodeObserver(Context context, Handler handler, int received_code) {
    super(handler);
    mContext = context;
    mHandler = handler;
    mReceivedCode = received_code;
  }

  @Override
  public void onChange(boolean selfChange, Uri uri) {
    super.onChange(selfChange, uri);
    if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
      return;
    }
    String code = "";
    if (uri.toString().equals("content://sms/raw")) {
      return;
    }
    Uri inboxUri = Uri.parse("content://sms/inbox");
    Cursor c = mContext.getContentResolver().query(inboxUri, null, null, null, "date desc");
    if (c != null) {
      if (c.moveToFirst()) {
        String body = c.getString(c.getColumnIndex("body"));
        Pattern pattern = Pattern.compile("SIGNAL.+(\\d{3}-?\\d{3})");
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
          code = matcher.group(1);
          String result = code.replaceAll("-","");
          mHandler.obtainMessage(mReceivedCode, result).sendToTarget();
        }
      }
      c.close();
    }
  }
}
