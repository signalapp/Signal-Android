package org.thoughtcrime.securesms.testutil;

import org.signal.core.util.ThreadUtil;

import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doCallRealMethod;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

public final class MainThreadUtil {

  private MainThreadUtil() {
  }

  /**
   * Makes {@link ThreadUtil}'s Main thread assertions pass or fail during tests.
   * <p>
   * Use with {@link org.powermock.modules.junit4.PowerMockRunner} or robolectric with powermock
   * rule and {@code @PrepareForTest(Util.class)}
   */
  public static void setMainThread(boolean isMainThread) {
    mockStatic(ThreadUtil.class);
    when(ThreadUtil.isMainThread()).thenReturn(isMainThread);
    try {
      doCallRealMethod().when(ThreadUtil.class, "assertMainThread");
      doCallRealMethod().when(ThreadUtil.class, "assertNotMainThread");
    } catch (Exception e) {
      throw new AssertionError();
    }
  }
}
