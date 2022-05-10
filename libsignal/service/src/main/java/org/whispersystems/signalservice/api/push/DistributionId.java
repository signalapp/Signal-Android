package org.whispersystems.signalservice.api.push;

import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents the distributionId that is used to identify this group's sender key session.
 *
 * This is just a UUID, but we wrap it in order to provide some type safety and limit confusion
 * around the multiple UUIDs we throw around.
 */
public final class DistributionId {

  public static final DistributionId MY_STORY = DistributionId.from("00000000-0000-0000-0000-000000000000");

  private final UUID uuid;

  public static DistributionId from(String id) {
    return new DistributionId(UuidUtil.parseOrThrow(id));
  }

  public static DistributionId from(UUID uuid) {
    return new DistributionId(uuid);
  }

  public static DistributionId create() {
    return new DistributionId(UUID.randomUUID());
  }

  private DistributionId(UUID uuid) {
    this.uuid = uuid;
  }

  public UUID asUuid() {
    return uuid;
  }

  @Override
  public String toString() {
    return uuid.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final DistributionId that = (DistributionId) o;
    return Objects.equals(uuid, that.uuid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uuid);
  }
}
