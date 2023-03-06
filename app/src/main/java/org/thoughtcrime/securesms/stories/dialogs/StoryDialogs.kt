package org.thoughtcrime.securesms.stories.dialogs

import android.content.Context
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R

object StoryDialogs {

  fun removeGroupStory(
    context: Context,
    groupName: String,
    onConfirmed: () -> Unit
  ) {
    MaterialAlertDialogBuilder(context)
      .setTitle(R.string.StoryDialogs__remove_group_story)
      .setMessage(context.getString(R.string.StoryDialogs__s_will_be_removed, groupName))
      .setPositiveButton(R.string.StoryDialogs__remove) { _, _ -> onConfirmed() }
      .setNegativeButton(android.R.string.cancel) { _, _ -> }
      .show()
  }

  fun deleteDistributionList(
    context: Context,
    distributionListName: String,
    onDelete: () -> Unit
  ) {
    MaterialAlertDialogBuilder(context)
      .setTitle(R.string.StoryDialogs__delete_custom_story)
      .setMessage(context.getString(R.string.StoryDialogs__s_and_updates_shared, distributionListName))
      .setPositiveButton(R.string.StoryDialogs__delete) { _, _ -> onDelete() }
      .setNegativeButton(android.R.string.cancel) { _, _ -> }
      .show()
  }

  fun disableStories(
    context: Context,
    userHasStories: Boolean,
    onDisable: () -> Unit
  ) {
    val positiveButtonMessage = if (userHasStories) {
      R.string.StoryDialogs__turn_off_and_delete
    } else {
      R.string.StoriesPrivacySettingsFragment__turn_off_stories
    }

    MaterialAlertDialogBuilder(context)
      .setTitle(R.string.StoriesPrivacySettingsFragment__turn_off_stories_question)
      .setMessage(R.string.StoriesPrivacySettingsFragment__you_will_no_longer_be_able_to_share)
      .setPositiveButton(positiveButtonMessage) { _, _ -> onDisable() }
      .setNegativeButton(android.R.string.cancel) { _, _ -> }
      .show()
  }

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
    onHideStoryConfirmed: () -> Unit
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
