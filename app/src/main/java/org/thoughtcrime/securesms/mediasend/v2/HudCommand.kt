package org.thoughtcrime.securesms.mediasend.v2

import android.view.KeyEvent

sealed class HudCommand {
  object StartDraw : HudCommand()
  object StartCropAndRotate : HudCommand()
  object SaveMedia : HudCommand()

  object GoToText : HudCommand()
  object GoToCapture : HudCommand()

  object ResumeEntryTransition : HudCommand()

  object OpenEmojiSearch : HudCommand()
  object CloseEmojiSearch : HudCommand()
  data class EmojiInsert(val emoji: String?) : HudCommand()
  data class EmojiKeyEvent(val keyEvent: KeyEvent?) : HudCommand()
}
