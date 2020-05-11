package org.whispersystems.signalservice.api.messages;

import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;

public final class SignalServiceGroupContext {

  private final Optional<SignalServiceGroup>   groupV1;
  private final Optional<SignalServiceGroupV2> groupV2;

  private SignalServiceGroupContext(SignalServiceGroup groupV1) {
    this.groupV1 = Optional.of(groupV1);
    this.groupV2 = Optional.absent();
  }

  private SignalServiceGroupContext(SignalServiceGroupV2 groupV2) {
    this.groupV1 = Optional.absent();
    this.groupV2 = Optional.of(groupV2);
  }

  public Optional<SignalServiceGroup> getGroupV1() {
    return groupV1;
  }

  public Optional<SignalServiceGroupV2> getGroupV2() {
    return groupV2;
  }

  static Optional<SignalServiceGroupContext> createOptional(SignalServiceGroup groupV1, SignalServiceGroupV2 groupV2)
      throws InvalidMessageException
  {
    return Optional.fromNullable(create(groupV1, groupV2));
  }

  public static SignalServiceGroupContext create(SignalServiceGroup groupV1, SignalServiceGroupV2 groupV2)
      throws InvalidMessageException
  {
    if (groupV1 == null && groupV2 == null) {
      return null;
    }

    if (groupV1 != null && groupV2 != null) {
      throw new InvalidMessageException("Message cannot have both V1 and V2 group contexts.");
    }

    if (groupV1 != null) {
      return new SignalServiceGroupContext(groupV1);
    } else {
      return new SignalServiceGroupContext(groupV2);
    }
  }

  public SignalServiceGroup.Type getGroupV1Type() {
    if (groupV1.isPresent()) {
      return groupV1.get().getType();
    }
    return null;
  }
}
