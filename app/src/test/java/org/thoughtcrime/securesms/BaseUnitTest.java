package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.signal.core.util.logging.Log;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class BaseUnitTest {

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  @Mock
  private MockedStatic<Looper> looperMockedStatic;

  @Mock
  private MockedStatic<Log> logMockedStatic;

  @Mock
  private MockedStatic<Handler> handlerMockedStatic;

  @Mock
  private MockedStatic<TextUtils> textUtilsMockedStatic;

  @Mock
  private MockedStatic<PreferenceManager> preferenceManagerMockedStatic;

  @Mock
  private MockedConstruction<Handler> handlerMockedConstruction;

  protected Context           context           = mock(Context.class);
  protected SharedPreferences sharedPreferences = mock(SharedPreferences.class);

  @Before
  public void setUp() throws Exception {
    when(PreferenceManager.getDefaultSharedPreferences(any(Context.class))).thenReturn(sharedPreferences);
    when(Looper.getMainLooper()).thenReturn(null);

    Answer<?> logAnswer = (Answer<Void>) invocation -> {
      final String tag = (String)invocation.getArguments()[0];
      final String msg = (String)invocation.getArguments()[1];
      System.out.println(invocation.getMethod().getName().toUpperCase() + "/[" + tag + "] " + msg);
      return null;
    };

    logMockedStatic.when(() -> Log.d(anyString(), anyString())).thenAnswer(logAnswer);
    logMockedStatic.when(() -> Log.i(anyString(), anyString())).thenAnswer(logAnswer);
    logMockedStatic.when(() -> Log.w(anyString(), anyString())).thenAnswer(logAnswer);
    logMockedStatic.when(() -> Log.e(anyString(), anyString())).thenAnswer(logAnswer);

    Answer<Boolean> isEmptyAnswer = invocation -> {
      final String s = (String)invocation.getArguments()[0];
      return s == null || s.length() == 0;
    };

    textUtilsMockedStatic.when(() -> TextUtils.isEmpty(anyString())).thenAnswer(isEmptyAnswer);

    when(sharedPreferences.getString(anyString(), anyString())).thenReturn("");
    when(sharedPreferences.getLong(anyString(), anyLong())).thenReturn(0L);
    when(sharedPreferences.getInt(anyString(), anyInt())).thenReturn(0);
    when(sharedPreferences.getBoolean(anyString(), anyBoolean())).thenReturn(false);
    when(sharedPreferences.getFloat(anyString(), anyFloat())).thenReturn(0f);
    when(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPreferences);
    when(context.getPackageName()).thenReturn("org.thoughtcrime.securesms");
  }
}
