package org.whispersystems.signalservice.internal.push;

import org.junit.Test;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class GroupMismatchedDevicesTest {

  @Test
  public void testSimpleParse() throws IOException {
    GroupMismatchedDevices[] parsed = JsonUtil.fromJson("[\n" +
        "  {\n" +
        "    \"uuid\": \"12345678-1234-1234-1234-123456789012\",\n" +
        "    \"devices\": {\n" +
        "      \"missingDevices\": [1, 2],\n" +
        "      \"extraDevices\": [3]\n" +
        "    }\n" +
        "  },\n" +
        "  {\n" +
        "    \"uuid\": \"22345678-1234-1234-1234-123456789012\",\n" +
        "    \"devices\": {\n" +
        "      \"missingDevices\": [],\n" +
        "      \"extraDevices\": [2]\n" +
        "    }\n" +
        "  }\n" +
        "]", GroupMismatchedDevices[].class);

    assertEquals(2,  parsed.length);
    assertEquals("12345678-1234-1234-1234-123456789012", parsed[0].getUuid());
    assertEquals(1, (int) parsed[0].getDevices().getMissingDevices().get(0));
    assertEquals(2, (int) parsed[0].getDevices().getMissingDevices().get(1));
    assertEquals(3, (int) parsed[0].getDevices().getExtraDevices().get(0));

    assertEquals("22345678-1234-1234-1234-123456789012", parsed[1].getUuid());
    assertEquals(2, (int) parsed[1].getDevices().getExtraDevices().get(0));
  }
}
