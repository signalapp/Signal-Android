package org.whispersystems.signalservice.api.messages;


import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.util.List;
import java.util.Optional;

public class SignalServiceStoryMessage {
  private final Optional<byte[]>                              profileKey;
  private final Optional<SignalServiceGroupV2>                groupContext;
  private final Optional<SignalServiceAttachment>             fileAttachment;
  private final Optional<SignalServiceTextAttachment>         textAttachment;
  private final Optional<Boolean>                             allowsReplies;
  private final Optional<List<SignalServiceProtos.BodyRange>> bodyRanges;

  private SignalServiceStoryMessage(byte[] profileKey,
                                    SignalServiceGroupV2 groupContext,
                                    SignalServiceAttachment fileAttachment,
                                    SignalServiceTextAttachment textAttachment,
                                    boolean allowsReplies,
                                    List<SignalServiceProtos.BodyRange> bodyRanges)
  {
    this.profileKey     = Optional.ofNullable(profileKey);
    this.groupContext   = Optional.ofNullable(groupContext);
    this.fileAttachment = Optional.ofNullable(fileAttachment);
    this.textAttachment = Optional.ofNullable(textAttachment);
    this.allowsReplies  = Optional.of(allowsReplies);
    this.bodyRanges     = Optional.ofNullable(bodyRanges);
  }

  public static SignalServiceStoryMessage forFileAttachment(byte[] profileKey,
                                                            SignalServiceGroupV2 groupContext,
                                                            SignalServiceAttachment fileAttachment,
                                                            boolean allowsReplies,
                                                            List<SignalServiceProtos.BodyRange> bodyRanges)
  {
    return new SignalServiceStoryMessage(profileKey, groupContext, fileAttachment, null, allowsReplies, bodyRanges);
  }

  public static SignalServiceStoryMessage forTextAttachment(byte[] profileKey,
                                                            SignalServiceGroupV2 groupContext,
                                                            SignalServiceTextAttachment textAttachment,
                                                            boolean allowsReplies,
                                                            List<SignalServiceProtos.BodyRange> bodyRanges)
  {
    return new SignalServiceStoryMessage(profileKey, groupContext, null, textAttachment, allowsReplies, bodyRanges);
  }

  public Optional<byte[]> getProfileKey() {
    return profileKey;
  }

  public Optional<SignalServiceGroupV2> getGroupContext() {
    return groupContext;
  }

  public Optional<SignalServiceAttachment> getFileAttachment() {
    return fileAttachment;
  }

  public Optional<SignalServiceTextAttachment> getTextAttachment() {
    return textAttachment;
  }

  public Optional<Boolean> getAllowsReplies() {
    return allowsReplies;
  }

  public Optional<List<SignalServiceProtos.BodyRange>> getBodyRanges() {
    return bodyRanges;
  }
}
