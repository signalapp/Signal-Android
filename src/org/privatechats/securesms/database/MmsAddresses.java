package org.privatechats.securesms.database;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.LinkedList;
import java.util.List;

public class MmsAddresses {

  private final @Nullable String       from;
  private final @NonNull  List<String> to;
  private final @NonNull  List<String> cc;
  private final @NonNull  List<String> bcc;

  public MmsAddresses(@Nullable String from, @NonNull List<String> to,
                      @NonNull List<String> cc, @NonNull List<String> bcc)
  {
    this.from = from;
    this.to   = to;
    this.cc   = cc;
    this.bcc  = bcc;
  }

  @NonNull
  public List<String> getTo() {
    return to;
  }

  @NonNull
  public List<String> getCc() {
    return cc;
  }

  @NonNull
  public List<String> getBcc() {
    return bcc;
  }

  @Nullable
  public String getFrom() {
    return from;
  }

  public static MmsAddresses forTo(@NonNull List<String> to) {
    return new MmsAddresses(null, to, new LinkedList<String>(), new LinkedList<String>());
  }

  public static MmsAddresses forBcc(@NonNull List<String> bcc) {
    return new MmsAddresses(null, new LinkedList<String>(), new LinkedList<String>(), bcc);
  }

  public static MmsAddresses forFrom(@NonNull String from) {
    return new MmsAddresses(from, new LinkedList<String>(), new LinkedList<String>(), new LinkedList<String>());
  }
}
