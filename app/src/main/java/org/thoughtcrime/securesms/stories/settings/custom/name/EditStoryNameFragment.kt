package org.thoughtcrime.securesms.stories.settings.custom.name

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.airbnb.lottie.SimpleColorFilter
import com.google.android.material.textfield.TextInputLayout
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton

class EditStoryNameFragment : Fragment(R.layout.stories_edit_story_name_fragment) {

  private val viewModel: EditStoryNameViewModel by viewModels(
    factoryProducer = {
      EditStoryNameViewModel.Factory(distributionListId, EditStoryNameRepository())
    }
  )

  private val distributionListId: DistributionListId
    get() = EditStoryNameFragmentArgs.fromBundle(requireArguments()).distributionListId

  private val initialName: String
    get() = EditStoryNameFragmentArgs.fromBundle(requireArguments()).name

  private val lifecycleDisposable = LifecycleDisposable()

  private lateinit var saveButton: CircularProgressMaterialButton
  private lateinit var storyName: EditText
  private lateinit var storyNameWrapper: TextInputLayout

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    lifecycleDisposable.bindTo(viewLifecycleOwner)

    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    toolbar.navigationIcon?.colorFilter = SimpleColorFilter(ContextCompat.getColor(requireContext(), R.color.signal_icon_tint_primary))
    toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

    storyNameWrapper = view.findViewById(R.id.story_name_wrapper)
    storyName = view.findViewById(R.id.story_name)
    storyName.setText(initialName)
    storyName.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        onSaveClicked()
        true
      } else {
        false
      }
    }

    storyName.doAfterTextChanged {
      saveButton.isEnabled = !it.isNullOrEmpty()
      saveButton.alpha = if (it.isNullOrEmpty()) 0.5f else 1f
      storyNameWrapper.error = null
    }

    saveButton = view.findViewById(R.id.save)
    saveButton.setOnClickListener {
      onSaveClicked()
    }

    ViewUtil.focusAndShowKeyboard(storyName)
  }

  override fun onPause() {
    super.onPause()
    ViewUtil.hideKeyboard(requireContext(), storyName)
  }

  private fun onSaveClicked() {
    saveButton.isClickable = false
    lifecycleDisposable += viewModel.save(storyName.text).subscribeBy(
      onComplete = { findNavController().popBackStack() },
      onError = {
        saveButton.isClickable = true
        storyNameWrapper.error = getString(R.string.CreateStoryWithViewersFragment__there_is_already_a_story_with_this_name)
      }
    )
  }
}
