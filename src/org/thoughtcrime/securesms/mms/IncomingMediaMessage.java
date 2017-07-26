package org.thoughtcrime.securesms.mms;

import android.content.Context;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.PointerAttachment;
import org.thoughtcrime.securesms.crypto.MasterSecretUnion;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.MmsAddresses;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;

import java.util.LinkedList;
import java.util.List;

public class IncomingMediaMessage {

  private final Address from;
  private final String  body;
  private final Address groupId;
  private final boolean push;
  private final long    sentTimeMillis;
  private final int     subscriptionId;
  private final long    expiresIn;
  private final boolean expirationUpdate;

  private final List<Address>    to          = new LinkedList<>();
  private final List<Address>    cc          = new LinkedList<>();
  private final List<Attachment> attachments = new LinkedList<>();

  public IncomingMediaMessage(Context context, String from, List<String> to, List<String> cc,
                              String body, long sentTimeMillis,
                              List<Attachment> attachments, int subscriptionId,
                              long expiresIn, boolean expirationUpdate)
  {
    this.from             = Address.fromExternal(context, from);
    this.sentTimeMillis   = sentTimeMillis;
    this.body             = body;
    this.groupId          = null;
    this.push             = false;
    this.subscriptionId   = subscriptionId;
    this.expiresIn        = expiresIn;
    this.expirationUpdate = expirationUpdate;

    for (String destination : to) {
      this.to.add(Address.fromExternal(context, destination));
    }

    for (String destination : cc) {
      this.cc.add(Address.fromExternal(context, destination));
    }

    this.attachments.addAll(attachments);
  }

  public IncomingMediaMessage(MasterSecretUnion masterSecret,
                              Address from,
                              Address to,
                              long sentTimeMillis,
                              int subscriptionId,
                              long expiresIn,
                              boolean expirationUpdate,
                              Optional<String> relay,
                              Optional<String> body,
                              Optional<SignalServiceGroup> group,
                              Optional<List<SignalServiceAttachment>> attachments)
  {
    this.push             = true;
    this.from             = from;
    this.sentTimeMillis   = sentTimeMillis;
    this.body             = body.orNull();
    this.subscriptionId   = subscriptionId;
    this.expiresIn        = expiresIn;
    this.expirationUpdate = expirationUpdate;

    if (group.isPresent()) this.groupId = Address.fromSerialized(GroupUtil.getEncodedId(group.get().getGroupId()));
    else                   this.groupId = null;

    this.to.add(to);
    this.attachments.addAll(PointerAttachment.forPointers(masterSecret, attachments));
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public String getBody() {
    return body;
  }

  public MmsAddresses getAddresses() {
    return new MmsAddresses(from, to, cc, new LinkedList<Address>());
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }

  public Address getGroupId() {
    return groupId;
  }

  public boolean isPushMessage() {
    return push;
  }

  public boolean isExpirationUpdate() {
    return expirationUpdate;
  }

  public long getSentTimeMillis() {
    return sentTimeMillis;
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public boolean isGroupMessage() {
    return groupId != null || to.size() > 1 || cc.size() > 0;
  }
}
