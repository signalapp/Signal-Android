package org.thoughtcrime.securesms.contacts.paged

import android.database.Cursor
import android.database.CursorWrapper
import androidx.annotation.IntRange
import java.lang.Integer.max
import java.lang.Integer.min

/**
 * Cursor that takes a start offset and will wrap results around to the other side.
 *
 * For example, given a cursor with rows:
 *
 * [A, B, C, D, E]
 *
 * When I create a wrap-around cursor with a start offset = 2, and I read out all results,
 * I expect:
 *
 * [C, D, E, A, B]
 */
class WrapAroundCursor(delegate: Cursor, @IntRange(from = 0) private val offset: Int) : CursorWrapper(delegate) {

  init {
    check(offset < delegate.count && offset >= 0)
  }

  override fun moveToPosition(position: Int): Boolean {
    return if (offset == 0) {
      super.moveToPosition(position)
    } else {
      if (position == -1 || position == count) {
        super.moveToPosition(position)
      } else {
        super.moveToPosition((position + offset) % count)
      }
    }
  }

  override fun moveToFirst(): Boolean {
    return super.moveToPosition(offset)
  }

  override fun moveToLast(): Boolean {
    return if (offset == 0) {
      super.moveToLast()
    } else {
      super.moveToPosition(offset - 1)
    }
  }

  override fun move(offset: Int): Boolean {
    return if (offset == 0) {
      super.move(offset)
    } else {
      val position = max(min(offset + position, count), -1)
      moveToPosition(position)
    }
  }

  override fun moveToNext(): Boolean {
    return move(1)
  }

  override fun moveToPrevious(): Boolean {
    return move(-1)
  }

  override fun isLast(): Boolean {
    return if (offset == 0) {
      super.isLast()
    } else {
      return position == count - 1
    }
  }

  override fun isFirst(): Boolean {
    return if (offset == 0) {
      super.isFirst()
    } else {
      return position == 0
    }
  }

  override fun getPosition(): Int {
    return if (offset == 0) {
      super.getPosition()
    } else {
      val position = super.getPosition()
      if (position < 0 || position == count) {
        return position
      }

      val distance = (position - offset) % count
      if (distance >= 0) {
        distance
      } else {
        count + distance
      }
    }
  }
}
