package org.thoughtcrime.securesms.mediasend.v2

sealed class HudCommand {
  object StartDraw : HudCommand()
  object StartCropAndRotate : HudCommand()
  object SaveMedia : HudCommand()

  object GoToText : HudCommand()
  object GoToCapture : HudCommand()

  object ResumeEntryTransition : HudCommand()
}
