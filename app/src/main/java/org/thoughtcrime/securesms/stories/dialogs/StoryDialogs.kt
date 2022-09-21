package org.thoughtcrime.securesms.stories.dialogs

import android.content.Context
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R

object StoryDialogs {

  fun resendStory(context: Context, onDismiss: () -> Unit = {}, resend: () -> Unit) {
  MaterialAlertDialogBuilder(context)
    .setMessage(R.string.StoryDialogs__story_could_not_be_sent)
    .setNegativeButton(android.R.string.cancel, null)
    .setPositiveButton(R.string.StoryDialogs__send) { _, _ -> resend() }
    .setOnDismissListener { onDismiss() }
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

  fun hideStory(
    context: Context,
    recipientName: String,
    onCancelled: () -> Unit = {},
    onHideStoryConfirmed: () -> Unit,
  ) {
    MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Signal_MaterialAlertDialog)
      .setTitle(R.string.StoriesLandingFragment__hide_story)
      .setMessage(context.getString(R.string.StoriesLandingFragment__new_story_updates, recipientName))
      .setPositiveButton(R.string.StoriesLandingFragment__hide) { _, _ ->
        onHideStoryConfirmed()
      }
      .setNegativeButton(android.R.string.cancel) { _, _ -> onCancelled() }
      .setOnCancelListener { onCancelled() }
      .show()
  }
}
