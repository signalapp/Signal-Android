package org.thoughtcrime.securesms.contacts;

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
  public static final int FLAG_ALL                   = FLAG_PUSH | FLAG_SMS | FLAG_ACTIVE_GROUPS | FLAG_INACTIVE_GROUPS | FLAG_SELF;
}
