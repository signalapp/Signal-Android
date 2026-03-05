package org.whispersystems.signalservice.api.messages;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.List;

public class SignalServiceStoryMessageRecipient {

  private final SignalServiceAddress signalServiceAddress;
  private final List<String>         distributionListIds;
  private final boolean              isAllowedToReply;

  public SignalServiceStoryMessageRecipient(SignalServiceAddress signalServiceAddress,
                                            List<String> distributionListIds,
                                            boolean isAllowedToReply)
  {
    this.signalServiceAddress = signalServiceAddress;
    this.distributionListIds  = distributionListIds;
    this.isAllowedToReply     = isAllowedToReply;
  }

  public List<String> getDistributionListIds() {
    return distributionListIds;
  }

  public SignalServiceAddress getSignalServiceAddress() {
    return signalServiceAddress;
  }

  public boolean isAllowedToReply() {
    return isAllowedToReply;
  }
}
