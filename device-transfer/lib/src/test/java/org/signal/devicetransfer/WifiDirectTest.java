package org.signal.devicetransfer;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public class WifiDirectTest {

  @Test
  public void instanceName_withExtraInfo() {
    String instanceName = WifiDirect.buildServiceInstanceName("knownothing");

    assertEquals("_devicetransfer._knownothing._signal.org", instanceName);

    String extractedExtraInfo = WifiDirect.isInstanceNameMatching(instanceName);
    assertEquals(extractedExtraInfo, "knownothing");
  }

  @Test
  public void instanceName_matchingWithoutExtraInfo() {
    String instanceName = WifiDirect.buildServiceInstanceName("");

    assertEquals("_devicetransfer._signal.org", instanceName);

    String extractedExtraInfo = WifiDirect.isInstanceNameMatching(instanceName);
    assertEquals(extractedExtraInfo, "");
  }

  @Test
  public void instanceName_notMatching() {
    String extractedExtraInfo = WifiDirect.isInstanceNameMatching("_whoknows._what.org");
    assertNull(extractedExtraInfo);
  }
}
