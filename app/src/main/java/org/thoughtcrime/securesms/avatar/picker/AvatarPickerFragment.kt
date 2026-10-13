package org.thoughtcrime.securesms.avatar.picker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.signal.core.models.media.Media
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.permissions.Permissions
import org.signal.core.util.getParcelableExtraCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.AvatarBundler
import org.thoughtcrime.securesms.avatar.photo.PhotoEditorActivity
import org.thoughtcrime.securesms.avatar.text.TextAvatarCreationFragment
import org.thoughtcrime.securesms.avatar.vector.VectorAvatarCreationFragment
import org.thoughtcrime.securesms.mediasend.AvatarSelectionActivity
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Primary Avatar picker fragment, displays current user avatar and a list of recently used avatars and defaults.
 * Carries out the [AvatarPickerActions] that need an Activity or the nav graph.
 */
class AvatarPickerFragment : ComposeFragment() {

  companion object {
    const val REQUEST_KEY_SELECT_AVATAR = "org.thoughtcrime.securesms.avatar.picker.SELECT_AVATAR"
    const val SELECT_AVATAR_MEDIA = "org.thoughtcrime.securesms.avatar.picker.SELECT_AVATAR_MEDIA"
    const val SELECT_AVATAR_CLEAR = "org.thoughtcrime.securesms.avatar.picker.SELECT_AVATAR_CLEAR"

    private const val REQUEST_CODE_SELECT_IMAGE = 1
  }

  private val viewModel: AvatarPickerViewModel by viewModels(factoryProducer = this::createFactory)

  private val photoEditorLauncher = registerForActivityResult(PhotoEditorActivity.Contract()) { photo ->
    if (photo != null) {
      viewModel.onEvent(AvatarPickerEvents.AvatarEdited(photo))
    }
  }

  private fun createFactory(): AvatarPickerViewModel.Factory {
    val args = AvatarPickerFragmentArgs.fromBundle(requireArguments())

    return AvatarPickerViewModel.Factory(args.groupId, args.isNewGroup, args.groupAvatarMedia)
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saveFailedMessage = stringResource(R.string.AvatarPickerRepository__failed_to_save_avatar)

    CollectActions(viewModel.actions) { action ->
      handleAction(action) {
        scope.launch { snackbarHostState.showSnackbar(saveFailedMessage) }
      }
    }

    AvatarPickerScreen(
      state = state,
      onEvent = viewModel::onEvent,
      snackbarHostState = snackbarHostState
    )
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    setFragmentResultListener(TextAvatarCreationFragment.REQUEST_KEY_TEXT) { _, bundle ->
      val text = AvatarBundler.extractText(bundle)
      viewModel.onEvent(AvatarPickerEvents.AvatarEdited(text))
    }

    setFragmentResultListener(VectorAvatarCreationFragment.REQUEST_KEY_VECTOR) { _, bundle ->
      val vector = AvatarBundler.extractVector(bundle)
      viewModel.onEvent(AvatarPickerEvents.AvatarEdited(vector))
    }
  }

  override fun onResume() {
    super.onResume()
    ViewUtil.hideKeyboard(requireContext(), requireView())
  }

  private fun handleAction(action: AvatarPickerActions, showSaveFailed: () -> Unit) {
    when (action) {
      AvatarPickerActions.Close -> findNavController().popBackStack()
      AvatarPickerActions.ShowSaveFailed -> showSaveFailed()
      AvatarPickerActions.LaunchCameraCapture -> openCameraCapture()
      AvatarPickerActions.LaunchPhotoSelection -> openGallery()
      AvatarPickerActions.LaunchTextAvatarCreation -> openTextEditor(null)
      is AvatarPickerActions.LaunchAvatarEditor -> openEditor(action.avatar)
      is AvatarPickerActions.FinishWithAvatar -> finishWithResult { putParcelable(SELECT_AVATAR_MEDIA, action.media) }
      AvatarPickerActions.FinishWithClearedAvatar -> finishWithResult { putBoolean(SELECT_AVATAR_CLEAR, true) }
    }
  }

  private fun finishWithResult(populateResult: Bundle.() -> Unit) {
    setFragmentResult(REQUEST_KEY_SELECT_AVATAR, Bundle().apply(populateResult))
    findNavController().popBackStack()
  }

  @Deprecated("Deprecated in Java")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == REQUEST_CODE_SELECT_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
      val media: Media = requireNotNull(data.getParcelableExtraCompat(AvatarSelectionActivity.EXTRA_MEDIA, Media::class.java))
      viewModel.onEvent(AvatarPickerEvents.PhotoSelected(media))
    } else {
      super.onActivityResult(requestCode, resultCode, data)
    }
  }

  private fun openEditor(avatar: Avatar) {
    when (avatar) {
      is Avatar.Photo -> openPhotoEditor(avatar)
      is Avatar.Resource -> throw UnsupportedOperationException()
      is Avatar.Text -> openTextEditor(avatar)
      is Avatar.Vector -> openVectorEditor(avatar)
    }
  }

  private fun openPhotoEditor(photo: Avatar.Photo) {
    photoEditorLauncher.launch(photo)
  }

  private fun openVectorEditor(vector: Avatar.Vector) {
    Navigation.findNavController(requireView())
      .safeNavigate(AvatarPickerFragmentDirections.actionAvatarPickerFragmentToVectorAvatarCreationFragment(AvatarBundler.bundleVector(vector)))
  }

  private fun openTextEditor(text: Avatar.Text?) {
    val bundle = if (text != null) AvatarBundler.bundleText(text) else null
    Navigation.findNavController(requireView())
      .safeNavigate(AvatarPickerFragmentDirections.actionAvatarPickerFragmentToTextAvatarCreationFragment(bundle))
  }

  @Suppress("DEPRECATION")
  private fun openCameraCapture() {
    val intent = AvatarSelectionActivity.getIntentForCameraCapture(requireContext())
    startActivityForResult(intent, REQUEST_CODE_SELECT_IMAGE)
  }

  @Suppress("DEPRECATION")
  private fun openGallery() {
    val intent = AvatarSelectionActivity.getIntentForGallery(requireContext())
    startActivityForResult(intent, REQUEST_CODE_SELECT_IMAGE)
  }

  @Deprecated("Deprecated in Java")
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }
}
