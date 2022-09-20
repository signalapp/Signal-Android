package org.thoughtcrime.securesms

import android.database.Cursor

abstract class MockCursor : Cursor {

  private var _position: Int = -1
  private var _count: Int = 0

  override fun getPosition(): Int {
    return _position
  }

  override fun moveToPosition(position: Int): Boolean {
    _position = position
    return true
  }

  override fun getCount(): Int {
    return _count
  }

  override fun moveToNext(): Boolean {
    _position++
    if (_position >= count) {
      return false
    }

    return true
  }

  override fun isLast(): Boolean {
    return _position == count - 1
  }

  override fun isAfterLast(): Boolean {
    return _position >= count
  }
}
