package org.thoughtcrime.securesms.conversation.mutiselect.forward

import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.PluralsRes
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.ContactSelectionListFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ContactFilterView
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader
import org.thoughtcrime.securesms.conversation.ui.error.SafetyNumberChangeDialog
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sharing.MultiShareArgs
import org.thoughtcrime.securesms.sharing.ShareSelectionAdapter
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.fragments.findListener
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog
import org.thoughtcrime.securesms.util.visible
import org.whispersystems.libsignal.util.guava.Optional
import java.util.function.Consumer

private const val ARG_MULTISHARE_ARGS = "multiselect.forward.fragment.arg.multishare.args"
private const val ARG_CAN_SEND_TO_NON_PUSH = "multiselect.forward.fragment.arg.can.send.to.non.push"
private const val ARG_TITLE = "multiselect.forward.fragment.title"
private val TAG = Log.tag(MultiselectForwardFragment::class.java)

class MultiselectForwardFragment :
  FixedRoundedCornerBottomSheetDialogFragment(),
  ContactSelectionListFragment.OnContactSelectedListener,
  ContactSelectionListFragment.OnSelectionLimitReachedListener,
  SafetyNumberChangeDialog.Callback {

  override val peekHeightPercentage: Float = 0.67f

  private val viewModel: MultiselectForwardViewModel by viewModels(factoryProducer = this::createViewModelFactory)
  private val disposables = LifecycleDisposable()

  private lateinit var selectionFragment: ContactSelectionListFragment
  private lateinit var contactFilterView: ContactFilterView
  private lateinit var addMessage: EditText

  private var callback: Callback? = null

  private var dismissibleDialog: SimpleProgressDialog.DismissibleDialog? = null

  private var handler: Handler? = null

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
    callback = findListener()
    disposables.bindTo(viewLifecycleOwner.lifecycle)

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

    val title: TextView = view.findViewById(R.id.title)
    val container = view.parent.parent.parent as FrameLayout
    val bottomBar = LayoutInflater.from(requireContext()).inflate(R.layout.multiselect_forward_fragment_bottom_bar, container, false)
    val shareSelectionRecycler: RecyclerView = bottomBar.findViewById(R.id.selected_list)
    val shareSelectionAdapter = ShareSelectionAdapter()
    val sendButton: View = bottomBar.findViewById(R.id.share_confirm)

    title.setText(requireArguments().getInt(ARG_TITLE))

    addMessage = bottomBar.findViewById(R.id.add_message)

    sendButton.setOnClickListener {
      sendButton.isEnabled = false
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
      when (it.stage) {
        MultiselectForwardState.Stage.Selection -> { }
        MultiselectForwardState.Stage.FirstConfirmation -> displayFirstSendConfirmation()
        is MultiselectForwardState.Stage.SafetyConfirmation -> displaySafetyNumberConfirmation(it.stage.identities)
        MultiselectForwardState.Stage.LoadingIdentities -> {}
        MultiselectForwardState.Stage.SendPending -> {
          handler?.removeCallbacksAndMessages(null)
          dismissibleDialog?.dismiss()
          dismissibleDialog = SimpleProgressDialog.showDelayed(requireContext())
        }
        MultiselectForwardState.Stage.SomeFailed -> dismissAndShowToast(R.plurals.MultiselectForwardFragment_messages_sent)
        MultiselectForwardState.Stage.AllFailed -> dismissAndShowToast(R.plurals.MultiselectForwardFragment_messages_failed_to_send)
        MultiselectForwardState.Stage.Success -> dismissAndShowToast(R.plurals.MultiselectForwardFragment_messages_sent)
        is MultiselectForwardState.Stage.SelectionConfirmed -> dismissWithResult(it.stage.recipients)
      }

      sendButton.isEnabled = it.stage == MultiselectForwardState.Stage.Selection
    }

    bottomBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
      selectionFragment.setRecyclerViewPaddingBottom(bottom - top)
    }

    addMessage.visible = getMultiShareArgs().isNotEmpty()
  }

  override fun onResume() {
    super.onResume()

    val now = System.currentTimeMillis()
    val expiringMessages = getMultiShareArgs().filter { it.expiresAt > 0L }
    val firstToExpire = expiringMessages.minByOrNull { it.expiresAt }
    val earliestExpiration = firstToExpire?.expiresAt ?: -1L

    if (earliestExpiration > 0) {
      if (earliestExpiration <= now) {
        handleMessageExpired()
      } else {
        handler = Handler(Looper.getMainLooper())
        handler?.postDelayed(this::handleMessageExpired, earliestExpiration - now)
      }
    }
  }

  override fun onPause() {
    super.onPause()

    handler?.removeCallbacksAndMessages(null)
  }

  override fun onDismiss(dialog: DialogInterface) {
    dismissibleDialog?.dismissNow()
    super.onDismiss(dialog)
  }

  private fun displayFirstSendConfirmation() {
    SignalStore.tooltips().markMultiForwardDialogSeen()

    val messageCount = getMessageCount()

    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.MultiselectForwardFragment__faster_forwards)
      .setMessage(R.string.MultiselectForwardFragment__forwarded_messages_are_now)
      .setPositiveButton(resources.getQuantityString(R.plurals.MultiselectForwardFragment_send_d_messages, messageCount, messageCount)) { d, _ ->
        d.dismiss()
        viewModel.confirmFirstSend(addMessage.text.toString())
      }
      .setNegativeButton(android.R.string.cancel) { d, _ ->
        d.dismiss()
        viewModel.cancelSend()
      }
      .show()
  }

  private fun displaySafetyNumberConfirmation(identityRecords: List<IdentityRecord>) {
    SafetyNumberChangeDialog.show(childFragmentManager, identityRecords)
  }

  private fun dismissAndShowToast(@PluralsRes toastTextResId: Int) {
    val argCount = getMessageCount()

    callback?.onFinishForwardAction()
    dismissibleDialog?.dismiss()
    Toast.makeText(requireContext(), requireContext().resources.getQuantityString(toastTextResId, argCount), Toast.LENGTH_SHORT).show()
    dismissAllowingStateLoss()
  }

  private fun dismissWithResult(recipientIds: List<RecipientId>) {
    callback?.onFinishForwardAction()
    dismissibleDialog?.dismiss()
    setFragmentResult(
      RESULT_SELECTION,
      Bundle().apply {
        putParcelableArrayList(RESULT_SELECTION_RECIPIENTS, ArrayList(recipientIds))
      }
    )
    dismissAllowingStateLoss()
  }

  private fun getMessageCount(): Int = getMultiShareArgs().size + if (addMessage.text.isNotEmpty()) 1 else 0

  private fun handleMessageExpired() {
    dismissAllowingStateLoss()

    callback?.onFinishForwardAction()
    dismissibleDialog?.dismiss()
    Toast.makeText(requireContext(), resources.getQuantityString(R.plurals.MultiselectForwardFragment__couldnt_forward_messages, getMultiShareArgs().size), Toast.LENGTH_LONG).show()
  }

  private fun getDefaultDisplayMode(): Int {
    var mode = ContactsCursorLoader.DisplayMode.FLAG_PUSH or
      ContactsCursorLoader.DisplayMode.FLAG_ACTIVE_GROUPS or
      ContactsCursorLoader.DisplayMode.FLAG_SELF or
      ContactsCursorLoader.DisplayMode.FLAG_HIDE_NEW or
      ContactsCursorLoader.DisplayMode.FLAG_HIDE_RECENT_HEADER

    if (Util.isDefaultSmsProvider(requireContext()) && requireArguments().getBoolean(ARG_CAN_SEND_TO_NON_PUSH)) {
      mode = mode or ContactsCursorLoader.DisplayMode.FLAG_SMS
    }

    return mode or ContactsCursorLoader.DisplayMode.FLAG_HIDE_GROUPS_V1
  }

  override fun onBeforeContactSelected(recipientId: Optional<RecipientId>, number: String?, callback: Consumer<Boolean>) {
    if (recipientId.isPresent) {
      disposables.add(
        viewModel.addSelectedContact(recipientId, null)
          .subscribeOn(Schedulers.io())
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe { success ->
            if (!success) {
              Toast.makeText(requireContext(), R.string.ShareActivity_you_do_not_have_permission_to_send_to_this_group, Toast.LENGTH_SHORT).show()
            }
            callback.accept(success)
            contactFilterView.clear()
          }
      )
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

  override fun onSendAnywayAfterSafetyNumberChange(changedRecipients: MutableList<RecipientId>) {
    viewModel.confirmSafetySend(addMessage.text.toString())
  }

  override fun onMessageResentAfterSafetyNumberChange() {
    throw UnsupportedOperationException()
  }

  override fun onCanceled() {
    viewModel.cancelSend()
  }

  companion object {

    const val RESULT_SELECTION = "result_selection"
    const val RESULT_SELECTION_RECIPIENTS = "result_selection_recipients"

    @JvmStatic
    fun show(supportFragmentManager: FragmentManager, multiselectForwardFragmentArgs: MultiselectForwardFragmentArgs) {
      val fragment = MultiselectForwardFragment()

      fragment.arguments = Bundle().apply {
        putParcelableArrayList(ARG_MULTISHARE_ARGS, ArrayList(multiselectForwardFragmentArgs.multiShareArgs))
        putBoolean(ARG_CAN_SEND_TO_NON_PUSH, multiselectForwardFragmentArgs.canSendToNonPush)
        putInt(ARG_TITLE, multiselectForwardFragmentArgs.title)
      }

      fragment.show(supportFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  interface Callback {
    fun onFinishForwardAction()
  }
}
