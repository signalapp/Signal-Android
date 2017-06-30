package org.thoughtcrime.securesms;

import android.database.Cursor;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

public class ConversationAdapterTest extends BaseUnitTest {
  private Cursor cursor = mock(Cursor.class);
  private ConversationAdapter adapter;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    adapter = new ConversationAdapter(context, cursor);
    when(cursor.getColumnIndexOrThrow(anyString())).thenReturn(0);
  }

  @Test
  public void testGetItemIdEquals() throws Exception {
    when(cursor.getString(anyInt())).thenReturn(null).thenReturn("SMS::1::1");
    long firstId = adapter.getItemId(cursor);
    when(cursor.getString(anyInt())).thenReturn(null).thenReturn("MMS::1::1");
    long secondId = adapter.getItemId(cursor);
    assertNotEquals(firstId, secondId);
    when(cursor.getString(anyInt())).thenReturn(null).thenReturn("MMS::2::1");
    long thirdId = adapter.getItemId(cursor);
    assertNotEquals(secondId, thirdId);
  }
}