package org.thoughtcrime.securesms.mms;

import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.transport.UndeliverableMessageException;

import ws.com.google.android.mms.pdu.SendConf;

public interface OutgoingMmsConnection {
  SendConf send(@NonNull byte[] pduBytes) throws UndeliverableMessageException;
}
