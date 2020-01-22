package org.thoughtcrime.securesms.database.model;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.megaphone.Megaphones;

public class MegaphoneRecord {

  private final Megaphones.Event event;
  private final int              seenCount;
  private final long             lastSeen;
  private final boolean          finished;

  public MegaphoneRecord(@NonNull Megaphones.Event event, int seenCount, long lastSeen, boolean finished) {
    this.event     = event;
    this.seenCount = seenCount;
    this.lastSeen  = lastSeen;
    this.finished  = finished;
  }

  public @NonNull Megaphones.Event getEvent() {
    return event;
  }

  public int getSeenCount() {
    return seenCount;
  }

  public long getLastSeen() {
    return lastSeen;
  }

  public boolean isFinished() {
    return finished;
  }
}
