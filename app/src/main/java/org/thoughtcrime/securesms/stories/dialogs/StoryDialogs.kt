package org.thoughtcrime.securesms.stories.dialogs

import android.content.Context
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R

object StoryDialogs {

  fun resendStory(context: Context, resend: () -> Unit) {
    MaterialAlertDialogBuilder(context)
      .setMessage(R.string.StoryDialogs__story_could_not_be_sent)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.StoryDialogs__send) { _, _ -> resend() }
      .show()
  }

  fun displayStoryOrProfileImage(
    context: Context,
    onViewStory: () -> Unit,
    onViewAvatar: () -> Unit
  ) {
    MaterialAlertDialogBuilder(context)
      .setView(R.layout.display_story_or_profile_image_dialog)
      .setBackground(MaterialShapeDrawable(ShapeAppearanceModel.builder().setAllCornerSizes(DimensionUnit.DP.toPixels(28f)).build()))
      .create()
      .apply {
        setOnShowListener { dialog ->
          findViewById<View>(R.id.view_story)!!.setOnClickListener {
            dialog.dismiss()
            onViewStory()
          }
          findViewById<View>(R.id.view_profile_photo)!!.setOnClickListener {
            dialog.dismiss()
            onViewAvatar()
          }
        }
      }
      .show()
  }
}
