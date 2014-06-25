package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;
import android.test.mock.MockContext;

import static org.fest.assertions.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CanonicalAddressDatabaseTest extends InstrumentationTestCase {
  private static final String LOCAL_NUMBER = "+15555555555";
  private Context           mockContext;
  private SharedPreferences mockSharedPreferences;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // https://code.google.com/p/dexmaker/issues/detail?id=2
    System.setProperty("dexmaker.dexcache", getInstrumentation().getTargetContext().getCacheDir().toString());

    mockContext           = mock(MockContext.class);
    mockSharedPreferences = mock(SharedPreferences.class);
    when(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences);
    when(mockSharedPreferences.getString(eq("pref_local_number"), anyString())).thenReturn(LOCAL_NUMBER);
  }
}
