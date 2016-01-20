package org.privatechats.securesms.mms;

import org.privatechats.securesms.attachments.Attachment;
import org.privatechats.securesms.attachments.PointerAttachment;
import org.privatechats.securesms.crypto.MasterSecretUnion;
import org.privatechats.securesms.database.MmsAddresses;
import org.privatechats.securesms.util.GroupUtil;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureGroup;

import java.util.LinkedList;
import java.util.List;

public class IncomingMediaMessage {

  private final String  from;
  private final String  body;
  private final String  groupId;
  private final boolean push;
  private final long    sentTimeMillis;

  private final List<String>     to          = new LinkedList<>();
  private final List<String>     cc          = new LinkedList<>();
  private final List<Attachment> attachments = new LinkedList<>();

  public IncomingMediaMessage(String from, List<String> to, List<String> cc,
                              String body, long sentTimeMillis,
                              List<Attachment> attachments)
  {
    this.from           = from;
    this.sentTimeMillis = sentTimeMillis;
    this.body           = body;
    this.groupId        = null;
    this.push           = false;

    this.to.addAll(to);
    this.cc.addAll(cc);
    this.attachments.addAll(attachments);
  }

  public IncomingMediaMessage(MasterSecretUnion masterSecret,
                              String from,
                              String to,
                              long sentTimeMillis,
                              Optional<String> relay,
                              Optional<String> body,
                              Optional<TextSecureGroup> group,
                              Optional<List<TextSecureAttachment>> attachments)
  {
    this.push           = true;
    this.from           = from;
    this.sentTimeMillis = sentTimeMillis;
    this.body           = body.orNull();

    if (group.isPresent()) this.groupId = GroupUtil.getEncodedId(group.get().getGroupId());
    else                   this.groupId = null;

    this.to.add(to);
    this.attachments.addAll(PointerAttachment.forPointers(masterSecret, attachments));
  }

  public String getBody() {
    return body;
  }

  public MmsAddresses getAddresses() {
    return new MmsAddresses(from, to, cc, new LinkedList<String>());
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }

  public String getGroupId() {
    return groupId;
  }

  public boolean isPushMessage() {
    return push;
  }

  public long getSentTimeMillis() {
    return sentTimeMillis;
  }

  public boolean isGroupMessage() {
    return groupId != null || to.size() > 1 || cc.size() > 0;
  }
}
