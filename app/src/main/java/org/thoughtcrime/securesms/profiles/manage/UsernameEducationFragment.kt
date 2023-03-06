package org.thoughtcrime.securesms.profiles.manage

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.UsernameEducationFragmentBinding
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.megaphone.Megaphones
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Displays a Username education screen which displays some basic information
 * about usernames and provides a learn-more link.
 */
class UsernameEducationFragment : Fragment(R.layout.username_education_fragment) {
  private val binding by ViewBinderDelegate(UsernameEducationFragmentBinding::bind)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.toolbar.setNavigationOnClickListener {
      findNavController().popBackStack()
    }

    binding.usernameEducationLearnMore.setOnClickListener {
      CommunicationActions.openBrowserLink(requireContext(), getString(R.string.username_support_url))
    }

    binding.continueButton.setOnClickListener {
      SignalStore.uiHints().markHasSeenUsernameEducation()
      ApplicationDependencies.getMegaphoneRepository().markFinished(Megaphones.Event.SET_UP_YOUR_USERNAME)
      findNavController().safeNavigate(UsernameEducationFragmentDirections.actionUsernameEducationFragmentToUsernameManageFragment())
    }
  }
}
