package org.thoughtcrime.securesms.database

import android.database.Cursor
import org.signal.core.util.requireInt
import org.signal.spinner.ColumnTransformer
import org.thoughtcrime.securesms.polls.VoteState

object PollTransformer : ColumnTransformer {
  override fun matches(tableName: String?, columnName: String): Boolean {
    return columnName == PollTables.PollVoteTable.VOTE_STATE && (tableName == null || tableName == PollTables.PollVoteTable.TABLE_NAME)
  }

  override fun transform(tableName: String?, columnName: String, cursor: Cursor): String? {
    val voteState = VoteState.fromValue(cursor.requireInt(PollTables.PollVoteTable.VOTE_STATE))
    return "${cursor.requireInt(PollTables.PollVoteTable.VOTE_STATE)}<br><br>$voteState"
  }
}
