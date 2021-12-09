package org.thoughtcrime.securesms.components.reminder

import android.content.Context
import org.thoughtcrime.securesms.R

class BubbleOptOutReminder(context: Context) : Reminder(null, context.getString(R.string.BubbleOptOutTooltip__description)) {

  init {
    addAction(Action(context.getString(R.string.BubbleOptOutTooltip__turn_off), R.id.reminder_action_turn_off))
    addAction(Action(context.getString(R.string.BubbleOptOutTooltip__not_now), R.id.reminder_action_not_now))
  }

  override fun isDismissable(): Boolean {
    return false
  }
}
