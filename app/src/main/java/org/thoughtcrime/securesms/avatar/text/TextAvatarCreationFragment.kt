package org.thoughtcrime.securesms.avatar.text

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.tabs.TabLayout
import org.signal.core.util.EditTextUtil
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.AvatarBundler
import org.thoughtcrime.securesms.avatar.AvatarColorItem
import org.thoughtcrime.securesms.avatar.Avatars
import org.thoughtcrime.securesms.avatar.picker.AvatarPickerItem
import org.thoughtcrime.securesms.components.BoldSelectionTabItem
import org.thoughtcrime.securesms.components.ControllableTabLayout
import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout
import org.thoughtcrime.securesms.components.recyclerview.GridDividerDecoration
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

/**
 * Fragment to create an avatar based off of a Vector or Text (via a pager)
 */
class TextAvatarCreationFragment : Fragment(R.layout.text_avatar_creation_fragment) {

  private val viewModel: TextAvatarCreationViewModel by viewModels(factoryProducer = this::createFactory)

  private lateinit var textInput: EditText
  private lateinit var recycler: RecyclerView
  private lateinit var content: ConstraintLayout

  private val withRecyclerSet = ConstraintSet()
  private val withoutRecyclerSet = ConstraintSet()

  private var hasBoundFromViewModel: Boolean = false

  private fun createFactory(): TextAvatarCreationViewModel.Factory {
    val args = TextAvatarCreationFragmentArgs.fromBundle(requireArguments())
    val textBundle = args.textAvatar
    val text = if (textBundle != null) {
      AvatarBundler.extractText(textBundle)
    } else {
      Avatar.Text("", Avatars.colors.random(), Avatar.DatabaseId.NotSet)
    }

    return TextAvatarCreationViewModel.Factory(text)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val toolbar: Toolbar = view.findViewById(R.id.text_avatar_creation_toolbar)
    val tabLayout: ControllableTabLayout = view.findViewById(R.id.text_avatar_creation_tabs)
    val doneButton: View = view.findViewById(R.id.text_avatar_creation_done)
    val keyboardAwareLayout: KeyboardAwareLinearLayout = view.findViewById(R.id.keyboard_aware_layout)

    withRecyclerSet.load(requireContext(), R.layout.text_avatar_creation_fragment_content)
    withoutRecyclerSet.load(requireContext(), R.layout.text_avatar_creation_fragment_content_hidden_recycler)

    content = view.findViewById(R.id.content)
    recycler = view.findViewById(R.id.text_avatar_creation_recycler)
    textInput = view.findViewById(R.id.avatar_picker_item_text)

    toolbar.setNavigationOnClickListener { Navigation.findNavController(it).popBackStack() }
    BoldSelectionTabItem.registerListeners(tabLayout)

    val onTabSelectedListener = OnTabSelectedListener()
    tabLayout.addOnTabSelectedListener(onTabSelectedListener)
    onTabSelectedListener.onTabSelected(requireNotNull(tabLayout.getTabAt(tabLayout.selectedTabPosition)))

    val adapter = MappingAdapter()
    recycler.addItemDecoration(GridDividerDecoration(4, ViewUtil.dpToPx(16)))
    AvatarColorItem.registerViewHolder(adapter) {
      viewModel.setColor(it)
    }
    recycler.adapter = adapter

    val viewHolder = AvatarPickerItem.ViewHolder(view)
    viewModel.state.observe(viewLifecycleOwner) { state ->
      EditTextUtil.setCursorColor(textInput, state.currentAvatar.color.foregroundColor)
      viewHolder.bind(AvatarPickerItem.Model(state.currentAvatar, false))

      adapter.submitList(state.colors().map { AvatarColorItem.Model(it) })
      hasBoundFromViewModel = true
    }

    EditTextUtil.addGraphemeClusterLimitFilter(textInput, 3)
    textInput.doAfterTextChanged {
      if (it != null && hasBoundFromViewModel) {
        viewModel.setText(it.toString())
      }
    }

    doneButton.setOnClickListener { v ->
      setFragmentResult(REQUEST_KEY_TEXT, AvatarBundler.bundleText(viewModel.getCurrentAvatar()))
      Navigation.findNavController(v).popBackStack()
    }

    textInput.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_NEXT) {
        tabLayout.getTabAt(1)?.select()
        true
      } else {
        false
      }
    }

    keyboardAwareLayout.addOnKeyboardHiddenListener {
      if (tabLayout.selectedTabPosition == 1) {
        val transition = AutoTransition().setStartDelay(250L)
        TransitionManager.endTransitions(content)
        withRecyclerSet.applyTo(content)
        TransitionManager.beginDelayedTransition(content, transition)
      }
    }
  }

  private inner class OnTabSelectedListener : TabLayout.OnTabSelectedListener {
    override fun onTabSelected(tab: TabLayout.Tab) {
      when (tab.position) {
        0 -> {
          textInput.isEnabled = true
          ViewUtil.focusAndShowKeyboard(textInput)

          withoutRecyclerSet.applyTo(content)
          textInput.setSelection(textInput.length())
        }
        1 -> {
          textInput.isEnabled = false
          ViewUtil.hideKeyboard(requireContext(), textInput)
        }
      }
    }

    override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
    override fun onTabReselected(tab: TabLayout.Tab?) = Unit
  }

  companion object {
    const val REQUEST_KEY_TEXT = "org.thoughtcrime.securesms.avatar.text.TEXT"
  }
}
