package org.whispersystems.signalservice.testutil;

import org.signal.zkgroup.internal.Native;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

public final class ZkGroupLibraryUtil {

  private ZkGroupLibraryUtil() {
  }

  /**
   * Attempts to initialize the ZkGroup Native class, which will load the native binaries.
   * <p>
   * If that fails to link, then on Unix, it will fail as we rely on that for CI.
   * <p>
   * If that fails to link, and it's not Unix, it will skip the test via assumption violation.
   */
  public static void assumeZkGroupSupportedOnOS() {
    try {
      Class.forName(Native.class.getName());
    } catch (ClassNotFoundException e) {
      fail();
    } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
      String osName = System.getProperty("os.name");

      if (isUnix(osName)) {
        fail("Not able to link native ZkGroup on a key OS: " + osName);
      } else {
        assumeNoException("Not able to link native ZkGroup on this operating system: " + osName, e);
      }
    }
  }

  private static boolean isUnix(String osName) {
    assertNotNull(osName);
    osName = osName.toLowerCase();
    return osName.contains("nix") || osName.contains("nux") || osName.contains("aix");
  }
}
