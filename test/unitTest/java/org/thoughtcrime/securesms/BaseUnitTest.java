package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.thoughtcrime.securesms.crypto.MasterSecret;

import javax.crypto.spec.SecretKeySpec;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyFloat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ Log.class, Handler.class, Looper.class, TextUtils.class, PreferenceManager.class })
public abstract class BaseUnitTest {
  protected MasterSecret masterSecret;

  protected Context           context           = mock(Context.class);
  protected SharedPreferences sharedPreferences = mock(SharedPreferences.class);

  @Before
  public void setUp() throws Exception {
    masterSecret = new MasterSecret(new SecretKeySpec(new byte[16], "AES"),
                                    new SecretKeySpec(new byte[16], "HmacSHA1"));
    mockStatic(Looper.class);
    mockStatic(Log.class);
    mockStatic(Handler.class);
    mockStatic(TextUtils.class);
    mockStatic(PreferenceManager.class);

    when(PreferenceManager.getDefaultSharedPreferences(any(Context.class))).thenReturn(sharedPreferences);
    when(Looper.getMainLooper()).thenReturn(null);
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

    PowerMockito.doAnswer(new Answer<Boolean>() {
      @Override
      public Boolean answer(InvocationOnMock invocation) throws Throwable {
        final String s = (String)invocation.getArguments()[0];
        return s == null || s.length() == 0;
      }
    }).when(TextUtils.class, "isEmpty", anyString());

    when(sharedPreferences.getString(anyString(), anyString())).thenReturn("");
    when(sharedPreferences.getLong(anyString(), anyLong())).thenReturn(0L);
    when(sharedPreferences.getInt(anyString(), anyInt())).thenReturn(0);
    when(sharedPreferences.getBoolean(anyString(), anyBoolean())).thenReturn(false);
    when(sharedPreferences.getFloat(anyString(), anyFloat())).thenReturn(0f);
    when(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPreferences);
    when(context.getPackageName()).thenReturn("org.thoughtcrime.securesms");
  }
}
