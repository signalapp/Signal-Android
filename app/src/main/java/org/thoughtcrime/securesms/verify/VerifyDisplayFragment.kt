package org.thoughtcrime.securesms.verify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.requireParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * Fragment to display a user's identity key.
 */
class VerifyDisplayFragment : ComposeFragment() {
  private val viewModel: VerifyDisplayScreenViewModel by viewModels {
    val isVerified = requireArguments().getBoolean(VERIFIED_STATE, false)
    val recipientId = requireArguments().requireParcelableCompat(RECIPIENT_ID, RecipientId::class.java)
    val localIdentity = requireArguments().requireParcelableCompat(LOCAL_IDENTITY, IdentityKeyParcelable::class.java).get()!!
    val remoteIdentity = requireArguments().requireParcelableCompat(REMOTE_IDENTITY, IdentityKeyParcelable::class.java).get()!!

    VerifyDisplayScreenViewModel.Factory(
      isSafetyNumberVerified = isVerified,
      recipientId = recipientId,
      localIdentity = localIdentity,
      remoteIdentity = remoteIdentity
    )
  }

  private var callback: Callback? = null

  override fun onAttach(context: Context) {
    super.onAttach(context)
    callback = if (context is Callback) {
      context
    } else if (parentFragment is Callback) {
      parentFragment as Callback?
    } else {
      throw ClassCastException("Cannot find ScanListener in parent component")
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    VerifyDisplayScreen(
      state = state,
      emitter = this::onEvent,
      onQrViewInitialized = this::onQrViewInitialised
    )
  }

  override fun onCreateContextMenu(
    menu: ContextMenu,
    view: View,
    menuInfo: ContextMenuInfo?
  ) {
    super.onCreateContextMenu(menu, view, menuInfo)
    val fingerprint = viewModel.fingerprintSnapshot
    if (fingerprint != null) {
      val inflater = requireActivity().menuInflater
      inflater.inflate(R.menu.verify_display_fragment_context_menu, menu)
    }
  }

  override fun onContextItemSelected(item: MenuItem): Boolean {
    if (viewModel.fingerprintSnapshot == null) return super.onContextItemSelected(item)
    return when (item.itemId) {
      R.id.menu_copy -> {
        viewModel.copyFingerprintToClipboard()
        true
      }
      R.id.menu_compare -> {
        viewModel.compareClipboardToFingerprint()
        true
      }
      else -> {
        super.onContextItemSelected(item)
      }
    }
  }

  fun setScannedFingerprint(scanned: String) {
    viewModel.setScannedFingerprint(scanned)
  }

  private fun handleShare() {
    try {
      startActivity(Intent.createChooser(viewModel.createShareIntent(requireActivity()), getString(R.string.VerifyIdentityActivity_share_safety_number_via)))
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(activity, R.string.VerifyIdentityActivity_no_app_to_share_to, Toast.LENGTH_LONG).show()
    }
  }

  private fun onQrViewInitialised(view: View) {
    registerForContextMenu(view)
  }

  private fun onEvent(event: VerifyDisplayScreenEvent) {
    when (event) {
      VerifyDisplayScreenEvent.EducationDismiss -> viewModel.setSeenEducationSheet()
      VerifyDisplayScreenEvent.EducationLearnMoreClick -> CommunicationActions.openBrowserLink(requireContext(), getString(R.string.verify_display_fragment__link))
      VerifyDisplayScreenEvent.QrClick -> callback?.onQrCodeContainerClicked()
      VerifyDisplayScreenEvent.ShareClick -> handleShare()
      VerifyDisplayScreenEvent.VerifyAutomaticallyClick -> viewModel.verifyAutomatically()
      is VerifyDisplayScreenEvent.VerifyButtonClick -> viewModel.updateSafetyNumberVerification(event.isVerified)
      VerifyDisplayScreenEvent.YouMustFirstExchangeMessagesDialogDismiss -> requireActivity().finish()
    }
  }

  internal interface Callback {
    fun onQrCodeContainerClicked()
  }

  companion object {
    private const val RECIPIENT_ID = "recipient_id"
    private const val REMOTE_IDENTITY = "remote_identity"
    private const val LOCAL_IDENTITY = "local_identity"
    private const val LOCAL_NUMBER = "local_number"
    private const val VERIFIED_STATE = "verified_state"

    fun create(
      recipientId: RecipientId,
      remoteIdentity: IdentityKeyParcelable,
      localIdentity: IdentityKeyParcelable,
      localNumber: String,
      verifiedState: Boolean
    ): VerifyDisplayFragment {
      val fragment = VerifyDisplayFragment()
      fragment.arguments = Bundle().apply {
        putParcelable(RECIPIENT_ID, recipientId)
        putParcelable(REMOTE_IDENTITY, remoteIdentity)
        putParcelable(LOCAL_IDENTITY, localIdentity)
        putString(LOCAL_NUMBER, localNumber)
        putBoolean(VERIFIED_STATE, verifiedState)
      }
      return fragment
    }
  }
}
