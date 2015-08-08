package org.thoughtcrime.securesms;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Matchers.anyString;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest( { Log.class, Handler.class, Looper.class })
public abstract class BaseUnitTest {
  @Before
  public void setUp() throws Exception {
    mockStatic(Looper.class);
    mockStatic(Log.class);
    mockStatic(Handler.class);

    PowerMockito.when(Looper.getMainLooper()).thenReturn(null);
    PowerMockito.whenNew(Handler.class).withAnyArguments().thenReturn(null);

    Answer<?> logAnswer = new Answer<Void>() {
      @Override public Void answer(InvocationOnMock invocation) throws Throwable {
        final String tag = (String)invocation.getArguments()[0];
        final String msg = (String)invocation.getArguments()[1];
        System.out.println(invocation.getMethod().getName().toUpperCase() + "/[" + tag + "] " + msg);
        return null;
      }
    };
    PowerMockito.doAnswer(logAnswer).when(Log.class, "d", anyString(), anyString());
    PowerMockito.doAnswer(logAnswer).when(Log.class, "i", anyString(), anyString());
    PowerMockito.doAnswer(logAnswer).when(Log.class, "w", anyString(), anyString());
    PowerMockito.doAnswer(logAnswer).when(Log.class, "e", anyString(), anyString());
    PowerMockito.doAnswer(logAnswer).when(Log.class, "wtf", anyString(), anyString());
  }
}
