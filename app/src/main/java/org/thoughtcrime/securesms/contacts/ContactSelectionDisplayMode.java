package org.thoughtcrime.securesms.contacts;

import androidx.annotation.NonNull;

public final class ContactSelectionDisplayMode {
  public static final int FLAG_PUSH                  = 1;
  public static final int FLAG_SMS                   = 1 << 1;
  public static final int FLAG_ACTIVE_GROUPS         = 1 << 2;
  public static final int FLAG_INACTIVE_GROUPS       = 1 << 3;
  public static final int FLAG_SELF                  = 1 << 4;
  public static final int FLAG_BLOCK                 = 1 << 5;
  public static final int FLAG_HIDE_GROUPS_V1        = 1 << 5;
  public static final int FLAG_HIDE_NEW              = 1 << 6;
  public static final int FLAG_HIDE_RECENT_HEADER    = 1 << 7;
  public static final int FLAG_GROUPS_AFTER_CONTACTS = 1 << 8;

  public static final int FLAG_GROUP_MEMBERS         = 1 << 9;
  public static final int FLAG_ALL                   = FLAG_PUSH | FLAG_SMS | FLAG_ACTIVE_GROUPS | FLAG_INACTIVE_GROUPS | FLAG_SELF;

  public static Builder all() {
    return new Builder(FLAG_ALL);
  }

  public static Builder none() {
    return new Builder(0);
  }

  public static class Builder {
    int displayMode = 0;

    public Builder(int displayMode) {
      this.displayMode = displayMode;
    }

    public @NonNull Builder withPush() {
      displayMode = setFlag(displayMode, FLAG_PUSH);
      return this;
    }

    public @NonNull Builder withActiveGroups() {
      displayMode = setFlag(displayMode, FLAG_ACTIVE_GROUPS);
      return this;
    }

    public @NonNull Builder withGroupMembers() {
      displayMode = setFlag(displayMode, FLAG_GROUP_MEMBERS);
      return this;
    }

    public int build() {
      return displayMode;
    }

    private static int setFlag(int displayMode, int flag) {
      return displayMode | flag;
    }

    private static int clearFlag(int displayMode, int flag) {
      return displayMode & ~flag;
    }
  }
}
