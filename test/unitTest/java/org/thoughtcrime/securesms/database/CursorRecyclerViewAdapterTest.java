package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CursorRecyclerViewAdapterTest {
  private CursorRecyclerViewAdapter adapter;
  private Context                   context;
  private Cursor                    cursor;

  @Before
  public void setUp() {
    context = mock(Context.class);
    cursor  = mock(Cursor.class);
    when(cursor.getCount()).thenReturn(100);
    when(cursor.moveToPosition(anyInt())).thenReturn(true);

    adapter = new CursorRecyclerViewAdapter(context, cursor) {
      @Override
      public ViewHolder onCreateItemViewHolder(ViewGroup parent, int viewType) {
        return null;
      }

      @Override
      public void onBindItemViewHolder(ViewHolder viewHolder, @NonNull Cursor cursor) {
      }
    };
  }

  @Test
  public void testSanityCount() throws Exception {
    assertEquals(adapter.getItemCount(), 100);
  }

  @Test
  public void testHeaderCount() throws Exception {
    adapter.setHeaderView(new View(context));
    assertEquals(adapter.getItemCount(), 101);

    assertEquals(adapter.getItemViewType(0), CursorRecyclerViewAdapter.HEADER_TYPE);
    assertNotEquals(adapter.getItemViewType(1), CursorRecyclerViewAdapter.HEADER_TYPE);
    assertNotEquals(adapter.getItemViewType(100), CursorRecyclerViewAdapter.HEADER_TYPE);
  }

  @Test
  public void testFooterCount() throws Exception {
    adapter.setFooterView(new View(context));
    assertEquals(adapter.getItemCount(), 101);
    assertEquals(adapter.getItemViewType(100), CursorRecyclerViewAdapter.FOOTER_TYPE);
    assertNotEquals(adapter.getItemViewType(0), CursorRecyclerViewAdapter.FOOTER_TYPE);
    assertNotEquals(adapter.getItemViewType(99), CursorRecyclerViewAdapter.FOOTER_TYPE);
  }

  @Test
  public void testHeaderFooterCount() throws Exception {
    adapter.setHeaderView(new View(context));
    adapter.setFooterView(new View(context));
    assertEquals(adapter.getItemCount(), 102);
    assertEquals(adapter.getItemViewType(101), CursorRecyclerViewAdapter.FOOTER_TYPE);
    assertEquals(adapter.getItemViewType(0), CursorRecyclerViewAdapter.HEADER_TYPE);
    assertNotEquals(adapter.getItemViewType(1), CursorRecyclerViewAdapter.HEADER_TYPE);
    assertNotEquals(adapter.getItemViewType(100), CursorRecyclerViewAdapter.FOOTER_TYPE);
  }
}

