package org.thoughtcrime.securesms.util;

import org.junit.Test;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class BucketingUtilTest {

  @Test
  public void bucket() {
    // GIVEN
    String key     = "research.megaphone.1";
    UUID   uuid    = UuidUtil.parseOrThrow("15b9729c-51ea-4ddb-b516-652befe78062");
    long   partPer = 1_000_000;

    // WHEN
    long countEnabled = BucketingUtil.bucket(key, uuid, partPer);

    // THEN
    assertEquals(243315, countEnabled);
  }
}
