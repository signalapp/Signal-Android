package org.thoughtcrime.securesms.badges.gifts.flow

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.fragment.findNavController
import org.signal.core.util.getParcelableArrayListCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.conversation.mutiselect.forward.SearchConfigurationProvider
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.util.activityViewModel
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Allows the user to select a recipient to send a gift to.
 */
class GiftFlowRecipientSelectionFragment : Fragment(R.layout.multiselect_forward_activity), MultiselectForwardFragment.Callback, SearchConfigurationProvider {

  private val viewModel: GiftFlowViewModel by activityViewModel {
    GiftFlowViewModel()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    if (savedInstanceState == null) {
      childFragmentManager.beginTransaction()
        .replace(
          R.id.fragment_container,
          MultiselectForwardFragment.create(
            MultiselectForwardFragmentArgs(
              multiShareArgs = emptyList(),
              title = R.string.GiftFlowRecipientSelectionFragment__choose_recipient,
              forceDisableAddMessage = true,
              selectSingleRecipient = true
            )
          )
        )
        .commit()
    }
  }

  override fun getSearchConfiguration(fragmentManager: FragmentManager, contactSearchState: ContactSearchState): ContactSearchConfiguration {
    return ContactSearchConfiguration.build {
      query = contactSearchState.query

      if (query.isNullOrEmpty()) {
        addSection(
          ContactSearchConfiguration.Section.Recents(
            includeSelf = false,
            includeHeader = true,
            mode = ContactSearchConfiguration.Section.Recents.Mode.INDIVIDUALS
          )
        )
      }

      addSection(
        ContactSearchConfiguration.Section.Individuals(
          includeSelfMode = RecipientTable.IncludeSelfMode.Exclude,
          transportType = ContactSearchConfiguration.TransportType.PUSH,
          includeHeader = true
        )
      )
    }
  }

  override fun onFinishForwardAction() = Unit

  override fun exitFlow() = Unit

  override fun navigateUp() {
    requireActivity().onBackPressedDispatcher.onBackPressed()
  }

  override fun onSearchInputFocused() = Unit

  override fun setResult(bundle: Bundle) {
    val contacts: List<ContactSearchKey.RecipientSearchKey> = bundle.getParcelableArrayListCompat(MultiselectForwardFragment.RESULT_SELECTION, ContactSearchKey.RecipientSearchKey::class.java)!!

    if (contacts.isNotEmpty()) {
      viewModel.setSelectedContact(contacts.first())
      findNavController().safeNavigate(R.id.action_giftFlowRecipientSelectionFragment_to_giftFlowConfirmationFragment)
    }
  }

  override fun getContainer(): ViewGroup = requireView() as ViewGroup

  override fun getDialogBackgroundColor(): Int = Color.TRANSPARENT
}
