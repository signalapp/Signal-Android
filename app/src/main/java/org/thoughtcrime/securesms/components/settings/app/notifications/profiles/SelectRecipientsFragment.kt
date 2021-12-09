package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.dd.CircularProgressButton
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.thoughtcrime.securesms.ContactSelectionListFragment
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ContactFilterView
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.CircularProgressButtonUtil
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.ViewUtil
import org.whispersystems.libsignal.util.guava.Optional
import java.util.function.Consumer

/**
 * Contact Selection for adding recipients to a Notification Profile.
 */
class SelectRecipientsFragment : LoggingFragment(), ContactSelectionListFragment.OnContactSelectedListener {

  private val viewModel: SelectRecipientsViewModel by viewModels(factoryProducer = this::createFactory)
  private val lifecycleDisposable = LifecycleDisposable()

  private var addToProfile: CircularProgressButton? = null

  private fun createFactory(): ViewModelProvider.Factory {
    val args = SelectRecipientsFragmentArgs.fromBundle(requireArguments())
    return SelectRecipientsViewModel.Factory(args.profileId, args.currentSelection?.toSet() ?: emptySet())
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    val currentSelection: Array<RecipientId>? = SelectRecipientsFragmentArgs.fromBundle(requireArguments()).currentSelection
    val selectionList = ArrayList<RecipientId>()
    if (currentSelection != null) {
      selectionList.addAll(currentSelection)
    }

    childFragmentManager.addFragmentOnAttachListener { _, fragment ->
      fragment.arguments = Bundle().apply {
        putInt(ContactSelectionListFragment.DISPLAY_MODE, getDefaultDisplayMode())
        putBoolean(ContactSelectionListFragment.REFRESHABLE, false)
        putBoolean(ContactSelectionListFragment.RECENTS, true)
        putParcelable(ContactSelectionListFragment.SELECTION_LIMITS, SelectionLimits.NO_LIMITS)
        putParcelableArrayList(ContactSelectionListFragment.CURRENT_SELECTION, selectionList)
        putBoolean(ContactSelectionListFragment.HIDE_COUNT, true)
        putBoolean(ContactSelectionListFragment.DISPLAY_CHIPS, true)
        putBoolean(ContactSelectionListFragment.CAN_SELECT_SELF, false)
        putBoolean(ContactSelectionListFragment.RV_CLIP, false)
        putInt(ContactSelectionListFragment.RV_PADDING_BOTTOM, ViewUtil.dpToPx(60))
      }
    }

    return inflater.inflate(R.layout.fragment_select_recipients_fragment, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    toolbar.setTitle(R.string.AddAllowedMembers__allowed_notifications)
    toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

    lifecycleDisposable.bindTo(viewLifecycleOwner.lifecycle)

    val contactFilterView: ContactFilterView = view.findViewById(R.id.contact_filter_edit_text)

    val selectionFragment: ContactSelectionListFragment = childFragmentManager.findFragmentById(R.id.contact_selection_list_fragment) as ContactSelectionListFragment

    contactFilterView.setOnFilterChangedListener {
      if (it.isNullOrEmpty()) {
        selectionFragment.resetQueryFilter()
      } else {
        selectionFragment.setQueryFilter(it)
      }
    }

    addToProfile = view.findViewById(R.id.select_recipients_add)
    addToProfile?.setOnClickListener {
      lifecycleDisposable += viewModel.updateAllowedMembers()
        .doOnSubscribe { CircularProgressButtonUtil.setSpinning(addToProfile) }
        .doOnTerminate { CircularProgressButtonUtil.cancelSpinning(addToProfile) }
        .subscribeBy(onSuccess = { findNavController().navigateUp() })
    }

    updateAddToProfile()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    addToProfile = null
  }

  private fun getDefaultDisplayMode(): Int {
    var mode = ContactsCursorLoader.DisplayMode.FLAG_PUSH or
      ContactsCursorLoader.DisplayMode.FLAG_ACTIVE_GROUPS or
      ContactsCursorLoader.DisplayMode.FLAG_HIDE_NEW or
      ContactsCursorLoader.DisplayMode.FLAG_HIDE_RECENT_HEADER or
      ContactsCursorLoader.DisplayMode.FLAG_GROUPS_AFTER_CONTACTS

    if (Util.isDefaultSmsProvider(requireContext())) {
      mode = mode or ContactsCursorLoader.DisplayMode.FLAG_SMS
    }

    return mode or ContactsCursorLoader.DisplayMode.FLAG_HIDE_GROUPS_V1
  }

  override fun onBeforeContactSelected(recipientId: Optional<RecipientId>, number: String?, callback: Consumer<Boolean>) {
    if (recipientId.isPresent) {
      viewModel.select(recipientId.get())
      callback.accept(true)
      updateAddToProfile()
    } else {
      callback.accept(false)
    }
  }

  override fun onContactDeselected(recipientId: Optional<RecipientId>, number: String?) {
    if (recipientId.isPresent) {
      viewModel.deselect(recipientId.get())
      updateAddToProfile()
    }
  }

  override fun onSelectionChanged() = Unit

  private fun updateAddToProfile() {
    val enabled = viewModel.recipients.isNotEmpty()
    addToProfile?.isEnabled = enabled
    addToProfile?.alpha = if (enabled) 1f else 0.5f
  }
}
