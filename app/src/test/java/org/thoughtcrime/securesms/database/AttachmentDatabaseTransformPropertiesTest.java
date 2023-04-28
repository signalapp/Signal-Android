package org.thoughtcrime.securesms.database;

import org.junit.Test;
import org.thoughtcrime.securesms.mms.SentMediaQuality;

import static org.junit.Assert.assertEquals;

public class AttachmentDatabaseTransformPropertiesTest {

  @Test
  public void transformProperties_verifyStructure() {
    AttachmentTable.TransformProperties properties = AttachmentTable.TransformProperties.empty();
    assertEquals("Added transform property, need to confirm default behavior for pre-existing payloads in database",
                 "{\"skipTransform\":false,\"videoTrim\":false,\"videoTrimStartTimeUs\":0,\"videoTrimEndTimeUs\":0,\"sentMediaQuality\":0,\"videoEdited\":false}",
                 properties.serialize());
  }

  @Test
  public void transformProperties_verifyMissingSentMediaQualityDefaultBehavior() {
    String json = "{\"skipTransform\":false,\"videoTrim\":false,\"videoTrimStartTimeUs\":0,\"videoTrimEndTimeUs\":0,\"videoEdited\":false}";

    AttachmentTable.TransformProperties properties = AttachmentTable.TransformProperties.parse(json);

    assertEquals(0, properties.getSentMediaQuality());
    assertEquals(SentMediaQuality.STANDARD, SentMediaQuality.fromCode(properties.getSentMediaQuality()));
  }

}
