package org.whispersystems.signalservice.internal.push;

import org.junit.Test;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class GroupStaleDevicesTest {

  @Test
  public void testSimpleParse() throws IOException {
    GroupStaleDevices[] parsed = JsonUtil.fromJson("[\n" +
        "  {\n" +
        "    \"uuid\": \"12345678-1234-1234-1234-123456789012\",\n" +
        "    \"devices\": {\n" +
        "      \"staleDevices\": [3]\n" +
        "    }\n" +
        "  },\n" +
        "  {\n" +
        "    \"uuid\": \"22345678-1234-1234-1234-123456789012\",\n" +
        "    \"devices\": {\n" +
        "      \"staleDevices\": [2]\n" +
        "    }\n" +
        "  }\n" +
        "]", GroupStaleDevices[].class);

    assertEquals(2,  parsed.length);
    assertEquals("12345678-1234-1234-1234-123456789012", parsed[0].getUuid());
    assertEquals(3, (int) parsed[0].getDevices().getStaleDevices().get(0));

    assertEquals("22345678-1234-1234-1234-123456789012", parsed[1].getUuid());
    assertEquals(2, (int) parsed[1].getDevices().getStaleDevices().get(0));
  }
}
