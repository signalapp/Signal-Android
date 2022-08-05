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

  private static final String MY_STORY_STRING = "00000000-0000-0000-0000-000000000000";

  public static final DistributionId MY_STORY = DistributionId.from(MY_STORY_STRING);

  private final UUID uuid;

  /**
   * Some devices appear to have a bad UUID.toString() that misrenders an all-zero UUID as "0000-0000".
   * To account for this, we will keep our own string value, to prevent queries from going awry and such.
   */
  private final String stringValue;

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

    if (uuid.getLeastSignificantBits() == 0 && uuid.getMostSignificantBits() == 0) {
      this.stringValue = MY_STORY_STRING;
    } else {
      this.stringValue = this.uuid.toString();
    }
  }

  public UUID asUuid() {
    return uuid;
  }

  @Override
  public String toString() {
    return stringValue;
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
