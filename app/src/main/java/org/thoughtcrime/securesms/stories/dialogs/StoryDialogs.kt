package org.thoughtcrime.securesms.stories.dialogs

import android.content.Context
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient

object StoryDialogs {

  /**
   * Guards onAddToStory with a dialog
   */
  fun guardWithAddToYourStoryDialog(
    context: Context,
    contacts: Collection<ContactSearchKey>,
    onAddToStory: () -> Unit,
    onEditViewers: () -> Unit,
    onCancel: () -> Unit = {}
  ) {
    if (!isFirstSendToMyStory(contacts)) {
      onAddToStory()
    } else {
      SignalStore.storyValues().userHasBeenNotifiedAboutStories = true
      MaterialAlertDialogBuilder(context, R.style.Signal_ThemeOverlay_Dialog_Rounded)
        .setTitle(R.string.StoryDialogs__add_to_story_q)
        .setMessage(R.string.StoryDialogs__adding_content)
        .setPositiveButton(R.string.StoryDialogs__add_to_story) { _, _ ->
          onAddToStory.invoke()
        }
        .setNeutralButton(R.string.StoryDialogs__edit_viewers) { _, _ -> onEditViewers.invoke() }
        .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel.invoke() }
        .setCancelable(false)
        .show()
    }
  }

  private fun isFirstSendToMyStory(shareContacts: Collection<ContactSearchKey>): Boolean {
    if (SignalStore.storyValues().userHasBeenNotifiedAboutStories) {
      return false
    }

    return shareContacts.any { it is ContactSearchKey.RecipientSearchKey.Story && Recipient.resolved(it.recipientId).isMyStory }
  }

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
