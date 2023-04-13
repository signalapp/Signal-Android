package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputLayout
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.BreakIteratorCompat
import org.signal.core.util.EditTextUtil
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiUtil
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.EditNotificationProfileViewModel.SaveNotificationProfileResult
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.models.NotificationProfileNamePreset
import org.thoughtcrime.securesms.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.text.AfterTextChanged
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton

/**
 * Dual use Edit/Create notification profile fragment. Use to create in the create profile flow,
 * and then to edit from profile details. Responsible for naming and emoji.
 */
class EditNotificationProfileFragment : DSLSettingsFragment(layoutId = R.layout.fragment_edit_notification_profile), ReactWithAnyEmojiBottomSheetDialogFragment.Callback {

  private val viewModel: EditNotificationProfileViewModel by viewModels(factoryProducer = this::createFactory)
  private val lifecycleDisposable = LifecycleDisposable()

  private var emojiView: ImageView? = null
  private var nameView: EditText? = null

  private fun createFactory(): ViewModelProvider.Factory {
    val profileId = EditNotificationProfileFragmentArgs.fromBundle(requireArguments()).profileId
    return EditNotificationProfileViewModel.Factory(profileId)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    toolbar.setNavigationOnClickListener {
      ViewUtil.hideKeyboard(requireContext(), requireView())
      requireActivity().onBackPressed()
    }

    val title: TextView = view.findViewById(R.id.edit_notification_profile_title)
    val countView: TextView = view.findViewById(R.id.edit_notification_profile_count)
    val saveButton: CircularProgressMaterialButton = view.findViewById(R.id.edit_notification_profile_save)
    val emojiView: ImageView = view.findViewById(R.id.edit_notification_profile_emoji)
    val nameView: EditText = view.findViewById(R.id.edit_notification_profile_name)
    val nameTextWrapper: TextInputLayout = view.findViewById(R.id.edit_notification_profile_name_wrapper)

    EditTextUtil.addGraphemeClusterLimitFilter(nameView, NOTIFICATION_PROFILE_NAME_MAX_GLYPHS)
    nameView.addTextChangedListener(
      AfterTextChanged { editable: Editable ->
        presentCount(countView, editable.toString())
        nameTextWrapper.error = null
      }
    )

    emojiView.setOnClickListener {
      ReactWithAnyEmojiBottomSheetDialogFragment.createForAboutSelection()
        .show(childFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }

    view.findViewById<View>(R.id.edit_notification_profile_clear).setOnClickListener {
      nameView.setText("")
      onEmojiSelectedInternal("")
    }

    lifecycleDisposable.bindTo(viewLifecycleOwner.lifecycle)

    saveButton.setOnClickListener {
      if (TextUtils.isEmpty(nameView.text)) {
        nameTextWrapper.error = getString(R.string.EditNotificationProfileFragment__profile_must_have_a_name)
        return@setOnClickListener
      }

      lifecycleDisposable += viewModel.save(nameView.text.toString())
        .doOnSubscribe { saveButton.setSpinning() }
        .doAfterTerminate { saveButton.cancelSpinning() }
        .subscribeBy(
          onSuccess = { saveResult ->
            when (saveResult) {
              is SaveNotificationProfileResult.Success -> {
                ViewUtil.hideKeyboard(requireContext(), nameView)
                if (saveResult.createMode) {
                  findNavController().safeNavigate(EditNotificationProfileFragmentDirections.actionEditNotificationProfileFragmentToAddAllowedMembersFragment(saveResult.profile.id))
                } else {
                  findNavController().navigateUp()
                }
              }
              SaveNotificationProfileResult.DuplicateNameFailure -> {
                nameTextWrapper.error = getString(R.string.EditNotificationProfileFragment__a_profile_with_this_name_already_exists)
              }
            }
          }
        )
    }

    lifecycleDisposable += viewModel.getInitialState()
      .subscribeBy(
        onSuccess = { initial ->
          if (initial.createMode) {
            saveButton.text = getString(R.string.EditNotificationProfileFragment__create)
            title.setText(R.string.EditNotificationProfileFragment__name_your_profile)
          } else {
            saveButton.text = getString(R.string.EditNotificationProfileFragment__save)
            title.setText(R.string.EditNotificationProfileFragment__edit_this_profile)
          }
          nameView.setText(initial.name)
          onEmojiSelectedInternal(initial.emoji)

          ViewUtil.focusAndMoveCursorToEndAndOpenKeyboard(nameView)
        }
      )

    this.nameView = nameView
    this.emojiView = emojiView
  }

  override fun bindAdapter(adapter: MappingAdapter) {
    NotificationProfileNamePreset.register(adapter)

    val onClick = { preset: NotificationProfileNamePreset.Model ->
      nameView?.apply {
        setText(preset.bodyResource)
        setSelection(length(), length())
      }
      onEmojiSelectedInternal(preset.emoji)
    }

    adapter.submitList(
      listOf(
        NotificationProfileNamePreset.Model("\uD83D\uDCAA", R.string.EditNotificationProfileFragment__work, onClick),
        NotificationProfileNamePreset.Model("\uD83D\uDE34", R.string.EditNotificationProfileFragment__sleep, onClick),
        NotificationProfileNamePreset.Model("\uD83D\uDE97", R.string.EditNotificationProfileFragment__driving, onClick),
        NotificationProfileNamePreset.Model("\uD83D\uDE0A", R.string.EditNotificationProfileFragment__downtime, onClick),
        NotificationProfileNamePreset.Model("\uD83D\uDCA1", R.string.EditNotificationProfileFragment__focus, onClick)
      )
    )
  }

  override fun onReactWithAnyEmojiSelected(emoji: String) {
    onEmojiSelectedInternal(emoji)
  }

  override fun onReactWithAnyEmojiDialogDismissed() = Unit

  private fun presentCount(countView: TextView, profileName: String) {
    val breakIterator = BreakIteratorCompat.getInstance()
    breakIterator.setText(profileName)

    val glyphCount = breakIterator.countBreaks()
    if (glyphCount >= NOTIFICATION_PROFILE_NAME_LIMIT_DISPLAY_THRESHOLD) {
      countView.visibility = View.VISIBLE
      countView.text = resources.getString(R.string.EditNotificationProfileFragment__count, glyphCount, NOTIFICATION_PROFILE_NAME_MAX_GLYPHS)
    } else {
      countView.visibility = View.GONE
    }
  }

  private fun onEmojiSelectedInternal(emoji: String) {
    val drawable = EmojiUtil.convertToDrawable(requireContext(), emoji)
    if (drawable != null) {
      emojiView?.setImageDrawable(drawable)
      viewModel.onEmojiSelected(emoji)
    } else {
      emojiView?.setImageResource(R.drawable.symbol_emoji_plus_24)
      viewModel.onEmojiSelected("")
    }
  }

  companion object {
    private const val NOTIFICATION_PROFILE_NAME_MAX_GLYPHS = 32
    private const val NOTIFICATION_PROFILE_NAME_LIMIT_DISPLAY_THRESHOLD = 22
  }
}
