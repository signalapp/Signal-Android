package org.thoughtcrime.securesms.database;

interface ThreadIdDatabaseReference {
  void remapThread(long fromId, long toId);
}
