package org.thoughtcrime.securesms.database;

/**
 * Indicates that this table references a thread ID. Thread IDs can be remapped at runtime if recipients merge, and therefore this table needs to be able to
 * handle remapping one thread ID to another.
 */
interface ThreadIdDatabaseReference {
  void remapThread(long fromId, long toId);
}
