package org.thoughtcrime.securesms.mediasend.v2

import android.view.KeyEvent

sealed class HudCommand {
  data object StartDraw : HudCommand()
  data object StartCropAndRotate : HudCommand()
  data object SaveMedia : HudCommand()

  data object GoToCamera : HudCommand()
  data object GoToVideo : HudCommand()
  data object GoToText : HudCommand()

  data object ResumeEntryTransition : HudCommand()

  data object OpenEmojiSearch : HudCommand()
  data object CloseEmojiSearch : HudCommand()
  data class EmojiInsert(val emoji: String?) : HudCommand()
  data class EmojiKeyEvent(val keyEvent: KeyEvent?) : HudCommand()
}
