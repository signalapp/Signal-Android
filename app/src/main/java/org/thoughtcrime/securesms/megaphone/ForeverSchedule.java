package org.thoughtcrime.securesms.megaphone;

final class ForeverSchedule implements MegaphoneSchedule {

  private final boolean enabled;

  ForeverSchedule(boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public boolean shouldDisplay(int seenCount, long lastSeen, long firstVisible, long currentTime) {
    return enabled;
  }
}
