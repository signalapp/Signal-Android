package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.thoughtcrime.securesms.transport.UndeliverableMessageException;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.RetrieveConf;
import ws.com.google.android.mms.pdu.SendConf;

public class CompatMmsConnection implements OutgoingMmsConnection, IncomingMmsConnection {
  private static final String TAG = CompatMmsConnection.class.getSimpleName();

  private Context context;

  public CompatMmsConnection(Context context) {
    this.context = context;
  }

  @Nullable @Override public SendConf send(@NonNull byte[] pduBytes)
      throws UndeliverableMessageException
  {
    try {
      Log.w(TAG, "Sending via legacy connection");
      return new OutgoingLegacyMmsConnection(context).send(pduBytes);
    } catch (UndeliverableMessageException | ApnUnavailableException e) {
      if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
        Log.w(TAG, "Falling back to try sending via Lollipop API");
        return new OutgoingLollipopMmsConnection(context).send(pduBytes);
      } else {
        throw new UndeliverableMessageException(e);
      }
    }
  }

  @Nullable @Override public RetrieveConf retrieve(@NonNull String contentLocation,
                                                   byte[] transactionId)
      throws MmsException, MmsRadioException, ApnUnavailableException, IOException
  {
    try {
      Log.w(TAG, "Receiving via legacy connection");
      return new IncomingLegacyMmsConnection(context).retrieve(contentLocation, transactionId);
    } catch (MmsRadioException | IOException | ApnUnavailableException e) {
      if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
        Log.w(TAG, "Falling back to try receiving via Lollipop API");
        return new IncomingLollipopMmsConnection(context).retrieve(contentLocation, transactionId);
      } else {
        throw e;
      }
    }
  }
}
