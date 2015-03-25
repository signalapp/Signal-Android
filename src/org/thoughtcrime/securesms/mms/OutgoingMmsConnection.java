package org.thoughtcrime.securesms.mms;

import org.thoughtcrime.securesms.transport.UndeliverableMessageException;

import ws.com.google.android.mms.pdu.SendConf;

public interface OutgoingMmsConnection {
  SendConf send(byte[] pduBytes) throws UndeliverableMessageException;
}
