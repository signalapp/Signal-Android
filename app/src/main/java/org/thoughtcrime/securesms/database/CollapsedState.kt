package org.thoughtcrime.securesms.database

import org.signal.core.util.LongSerializer

/**
 * Tracks the collapsed state of a message. Non-update messages are always NONE, while
 * update messages can either be the first update message of a collapsed set (HEAD_*)
 * or part of the collapsed set (COLLAPSED/EXPANDED)
 *
 * eg in the message table:
 * id | msg              | collapsed_state | collapsed_head_id
 * 1  | [Group Update 1] | HEAD_COLLAPSED  | 1
 * 2  | [Group Update 3] | COLLAPSED       | 1
 * 3  | Regular message  | NONE            | null
 * 4  | [Group Update 4] | HEAD_COLLAPSED  | 4
 *
 * and when expanded,
 * id | msg              | collapsed_state | collapsed_head_id
 * 1  | [Group Update 1] | HEAD_EXPANDED   | 1
 * 2  | [Group Update 3] | EXPANDED        | 1
 * 3  | Regular message  | NONE            | null
 * 4  | [Group Update 4] | HEAD_COLLAPSED  | 4
 */
enum class CollapsedState(val id: Long) {
  NONE(0),
  HEAD_COLLAPSED(1),
  HEAD_EXPANDED(2),
  COLLAPSED(3),
  EXPANDED(4),
  PENDING_COLLAPSED(5);

  companion object Serializer : LongSerializer<CollapsedState> {
    override fun serialize(data: CollapsedState): Long {
      return data.id
    }

    override fun deserialize(input: Long): CollapsedState {
      return CollapsedState.entries.firstOrNull { it.id == input } ?: NONE
    }

    @JvmStatic
    fun isHead(state: CollapsedState): Boolean {
      return state == HEAD_COLLAPSED || state == HEAD_EXPANDED
    }

    @JvmStatic
    fun isCollapsed(state: CollapsedState): Boolean {
      return state == HEAD_COLLAPSED || state == COLLAPSED
    }
  }
}
