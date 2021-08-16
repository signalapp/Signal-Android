package org.thoughtcrime.securesms.conversation.mutiselect.forward

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.ContactSelectionListFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ContactFilterView
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sharing.MultiShareArgs
import org.thoughtcrime.securesms.sharing.ShareSelectionAdapter
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog
import org.thoughtcrime.securesms.util.visible
import org.whispersystems.libsignal.util.guava.Optional
import java.util.function.Consumer

private const val ARG_MULTISHARE_ARGS = "multiselect.forward.fragment.arg.multishare.args"
private const val ARG_CAN_SEND_TO_NON_PUSH = "multiselect.forward.fragment.arg.can.send.to.non.push"
private val TAG = Log.tag(MultiselectForwardFragment::class.java)

class MultiselectForwardFragment : FixedRoundedCornerBottomSheetDialogFragment(), ContactSelectionListFragment.OnContactSelectedListener, ContactSelectionListFragment.OnSelectionLimitReachedListener {

  override val peekHeightPercentage: Float = 0.67f

  private val viewModel: MultiselectForwardViewModel by viewModels(factoryProducer = this::createViewModelFactory)

  private lateinit var selectionFragment: ContactSelectionListFragment
  private lateinit var contactFilterView: ContactFilterView

  private var dismissibleDialog: SimpleProgressDialog.DismissibleDialog? = null

  private fun createViewModelFactory(): MultiselectForwardViewModel.Factory {
    return MultiselectForwardViewModel.Factory(getMultiShareArgs(), MultiselectForwardRepository(requireContext()))
  }

  private fun getMultiShareArgs(): ArrayList<MultiShareArgs> = requireNotNull(requireArguments().getParcelableArrayList(ARG_MULTISHARE_ARGS))

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    childFragmentManager.addFragmentOnAttachListener { _, fragment ->
      fragment.arguments = Bundle().apply {
        putInt(ContactSelectionListFragment.DISPLAY_MODE, getDefaultDisplayMode())
        putBoolean(ContactSelectionListFragment.REFRESHABLE, false)
        putBoolean(ContactSelectionListFragment.RECENTS, true)
        putParcelable(ContactSelectionListFragment.SELECTION_LIMITS, FeatureFlags.shareSelectionLimit())
        putBoolean(ContactSelectionListFragment.HIDE_COUNT, true)
        putBoolean(ContactSelectionListFragment.DISPLAY_CHIPS, false)
        putBoolean(ContactSelectionListFragment.CAN_SELECT_SELF, true)
        putBoolean(ContactSelectionListFragment.RV_CLIP, false)
        putInt(ContactSelectionListFragment.RV_PADDING_BOTTOM, ViewUtil.dpToPx(48))
      }
    }

    val view = inflater.inflate(R.layout.multiselect_forward_fragment, container, false)

    view.minimumHeight = resources.displayMetrics.heightPixels

    return view
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    selectionFragment = childFragmentManager.findFragmentById(R.id.contact_selection_list_fragment) as ContactSelectionListFragment

    contactFilterView = view.findViewById(R.id.contact_filter_edit_text)

    contactFilterView.setOnSearchInputFocusChangedListener { _, hasFocus ->
      if (hasFocus) {
        (requireDialog() as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
      }
    }

    contactFilterView.setOnFilterChangedListener {
      if (it.isNullOrEmpty()) {
        selectionFragment.resetQueryFilter()
      } else {
        selectionFragment.setQueryFilter(it)
      }
    }

    val container = view.parent.parent.parent as FrameLayout
    val bottomBar = LayoutInflater.from(requireContext()).inflate(R.layout.multiselect_forward_fragment_bottom_bar, container, false)
    val shareSelectionRecycler: RecyclerView = bottomBar.findViewById(R.id.selected_list)
    val shareSelectionAdapter = ShareSelectionAdapter()
    val sendButton: View = bottomBar.findViewById(R.id.share_confirm)
    val addMessage: EditText = bottomBar.findViewById(R.id.add_message)
    val addMessageWrapper: View = bottomBar.findViewById(R.id.add_message_wrapper)

    addMessageWrapper.visible = FeatureFlags.forwardMultipleMessages()

    sendButton.setOnClickListener {
      it.isEnabled = false
      dismissibleDialog = SimpleProgressDialog.showDelayed(requireContext())

      viewModel.send(addMessage.text.toString())
    }

    shareSelectionRecycler.adapter = shareSelectionAdapter

    bottomBar.visible = false

    container.addView(bottomBar)

    viewModel.shareContactMappingModels.observe(viewLifecycleOwner) {
      shareSelectionAdapter.submitList(it)

      if (it.isNotEmpty() && !bottomBar.isVisible) {
        bottomBar.animation = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_fade_from_bottom)
        bottomBar.visible = true
      } else if (it.isEmpty() && bottomBar.isVisible) {
        bottomBar.animation = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_fade_to_bottom)
        bottomBar.visible = false
      }
    }

    viewModel.state.observe(viewLifecycleOwner) {
      val toastTextResId: Int? = when (it.stage) {
        MultiselectForwardState.Stage.SELECTION -> null
        MultiselectForwardState.Stage.SOME_FAILED -> R.plurals.MultiselectForwardFragment_messages_sent
        MultiselectForwardState.Stage.ALL_FAILED -> R.plurals.MultiselectForwardFragment_messages_failed_to_send
        MultiselectForwardState.Stage.SUCCESS -> R.plurals.MultiselectForwardFragment_messages_sent
      }

      if (toastTextResId != null) {
        val argCount = getMultiShareArgs().size

        dismissibleDialog?.dismiss()
        Toast.makeText(requireContext(), requireContext().resources.getQuantityString(toastTextResId, argCount), Toast.LENGTH_SHORT).show()
        dismissAllowingStateLoss()
      }
    }

    bottomBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
      selectionFragment.setRecyclerViewPaddingBottom(bottom - top)
    }
  }

  private fun getDefaultDisplayMode(): Int {
    var mode = ContactsCursorLoader.DisplayMode.FLAG_PUSH or ContactsCursorLoader.DisplayMode.FLAG_ACTIVE_GROUPS or ContactsCursorLoader.DisplayMode.FLAG_SELF or ContactsCursorLoader.DisplayMode.FLAG_HIDE_NEW

    if (Util.isDefaultSmsProvider(requireContext()) && requireArguments().getBoolean(ARG_CAN_SEND_TO_NON_PUSH)) {
      mode = mode or ContactsCursorLoader.DisplayMode.FLAG_SMS
    }

    return mode or ContactsCursorLoader.DisplayMode.FLAG_HIDE_GROUPS_V1
  }

  override fun onBeforeContactSelected(recipientId: Optional<RecipientId>, number: String?, callback: Consumer<Boolean>) {
    if (recipientId.isPresent) {
      viewModel.addSelectedContact(recipientId, null)
      callback.accept(true)
      contactFilterView.clear()
    } else {
      Log.w(TAG, "Rejecting non-present recipient. Can't forward to an unknown contact.")
      callback.accept(false)
    }
  }

  override fun onContactDeselected(recipientId: Optional<RecipientId>, number: String?) {
    viewModel.removeSelectedContact(recipientId, null)
  }

  override fun onSelectionChanged() {
  }

  override fun onSuggestedLimitReached(limit: Int) {
  }

  override fun onHardLimitReached(limit: Int) {
    Toast.makeText(requireContext(), R.string.MultiselectForwardFragment__limit_reached, Toast.LENGTH_SHORT).show()
  }

  companion object {
    @JvmStatic
    fun show(supportFragmentManager: FragmentManager, multiselectForwardFragmentArgs: MultiselectForwardFragmentArgs) {
      val fragment = MultiselectForwardFragment()

      fragment.arguments = Bundle().apply {
        putParcelableArrayList(ARG_MULTISHARE_ARGS, ArrayList(multiselectForwardFragmentArgs.multiShareArgs))
        putBoolean(ARG_CAN_SEND_TO_NON_PUSH, multiselectForwardFragmentArgs.canSendToNonPush)
      }

      fragment.show(supportFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }
}
