package org.thoughtcrime.securesms.conversation.v2.messages

import android.widget.TextView
import androidx.core.view.isVisible
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.util.DateUtils
import java.util.Locale

private const val maxTimeBetweenBreaks = 5 * 60 * 1000L // 5 minutes

fun TextView.showDateBreak(message: MessageRecord, previous: MessageRecord?) {
    val showDateBreak = (previous == null || message.timestamp - previous.timestamp > maxTimeBetweenBreaks)
    isVisible = showDateBreak
    text = if (showDateBreak) DateUtils.getDisplayFormattedTimeSpanString(context, Locale.getDefault(), message.timestamp) else ""
}
