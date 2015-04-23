package org.thoughtcrime.securesms.contacts;

import android.database.Cursor;
import android.os.Looper;

import org.thoughtcrime.securesms.TextSecureTestCase;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ContactsCursorLoaderTest extends TextSecureTestCase {

  public void setUp() throws Exception {
    super.setUp();
    Looper.prepare();
  }

  /*
   loadInBackground() may have multiple threads running through it concurrently
   depending on the android device version. Make sure concurrency does not cause
   the code to throw exceptions.
   */
  public void testConcurrentDatabaseAccess() throws InterruptedException {
    final ContactsCursorLoader loader = new ContactsCursorLoader(this.getContext(),
            "", true);

    ExecutorService exec = Executors.newFixedThreadPool(6);
    for (int n = 0; n < 6; ++n) {

      Runnable task = new Runnable() {
        @Override
        public void run() {
          try {
            Cursor csr = loader.loadInBackground();
            assertTrue("thread execution ok", true);
          } catch (Exception e) {
            fail(e.getMessage());
          }
        }
      };
      exec.execute(task);
    }

    exec.shutdown();
    boolean terminatedOk = exec.awaitTermination(4, TimeUnit.SECONDS);
    if (!terminatedOk) {
      fail("expected termination in less than 4 seconds, slow device?");
    }
  }
}
