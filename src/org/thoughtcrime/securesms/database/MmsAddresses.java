package org.thoughtcrime.securesms.database;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.LinkedList;
import java.util.List;

public class MmsAddresses {

  private final @Nullable Address       from;
  private final @NonNull  List<Address> to;
  private final @NonNull  List<Address> cc;
  private final @NonNull  List<Address> bcc;

  public MmsAddresses(@Nullable Address from, @NonNull List<Address> to,
                      @NonNull List<Address> cc, @NonNull List<Address> bcc)
  {
    this.from = from;
    this.to   = to;
    this.cc   = cc;
    this.bcc  = bcc;
  }

  @NonNull
  public List<Address> getTo() {
    return to;
  }

  @NonNull
  public List<Address> getCc() {
    return cc;
  }

  @NonNull
  public List<Address> getBcc() {
    return bcc;
  }

  @Nullable
  public Address getFrom() {
    return from;
  }

  public static MmsAddresses forTo(@NonNull List<Address> to) {
    return new MmsAddresses(null, to, new LinkedList<Address>(), new LinkedList<Address>());
  }

  public static MmsAddresses forBcc(@NonNull List<Address> bcc) {
    return new MmsAddresses(null, new LinkedList<Address>(), new LinkedList<Address>(), bcc);
  }

  public static MmsAddresses forFrom(@NonNull Address from) {
    return new MmsAddresses(from, new LinkedList<Address>(), new LinkedList<Address>(), new LinkedList<Address>());
  }
}
