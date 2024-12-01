package org.thoughtcrime.securesms.database

import android.content.Context
import android.database.Cursor
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter.FOOTER_TYPE
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter.HEADER_TYPE

class CursorRecyclerViewAdapterTest {
  private val context: Context = mockk<Context>()
  private val cursor: Cursor = mockk<Cursor>(relaxUnitFun = true) {
    every { count } returns 100
    every { moveToPosition(any()) } returns true
  }
  private val adapter = object : CursorRecyclerViewAdapter<RecyclerView.ViewHolder?>(context, cursor) {
    override fun onCreateItemViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder? = null
    override fun onBindItemViewHolder(viewHolder: RecyclerView.ViewHolder?, cursor: Cursor) = Unit
  }

  @Test
  fun testSanityCount() {
    assertEquals(100, adapter.itemCount)
  }

  @Test
  fun testHeaderCount() {
    adapter.headerView = View(context)
    assertEquals(101, adapter.itemCount)

    assertEquals(HEADER_TYPE, adapter.getItemViewType(0))
    assertNotEquals(HEADER_TYPE, adapter.getItemViewType(1))
    assertNotEquals(HEADER_TYPE, adapter.getItemViewType(100))
  }

  @Test
  fun testFooterCount() {
    adapter.setFooterView(View(context))
    assertEquals(101, adapter.itemCount)
    assertEquals(FOOTER_TYPE, adapter.getItemViewType(100))
    assertNotEquals(FOOTER_TYPE, adapter.getItemViewType(0))
    assertNotEquals(FOOTER_TYPE, adapter.getItemViewType(99))
  }

  @Test
  fun testHeaderFooterCount() {
    adapter.headerView = View(context)
    adapter.setFooterView(View(context))
    assertEquals(102, adapter.itemCount)
    assertEquals(FOOTER_TYPE, adapter.getItemViewType(101))
    assertEquals(HEADER_TYPE, adapter.getItemViewType(0))
    assertNotEquals(HEADER_TYPE, adapter.getItemViewType(1))
    assertNotEquals(FOOTER_TYPE, adapter.getItemViewType(100))
  }
}
