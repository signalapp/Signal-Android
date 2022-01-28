package org.thoughtcrime.securesms.verify

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import org.signal.core.util.ThreadUtil
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.qr.ScanListener
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.ServiceUtil

/**
 * Fragment to assist user in verifying recipient identity utilizing keys.
 */
class VerifyIdentityFragment : Fragment(R.layout.fragment_container), ScanListener, VerifyDisplayFragment.Callback {

  companion object {
    private const val EXTRA_RECIPIENT = "extra.recipient.id"
    private const val EXTRA_IDENTITY = "extra.recipient.identity"
    private const val EXTRA_VERIFIED = "extra.verified.state"

    @JvmStatic
    fun create(
      recipientId: RecipientId,
      remoteIdentity: IdentityKeyParcelable,
      verified: Boolean
    ): VerifyIdentityFragment {
      return VerifyIdentityFragment().apply {
        arguments = Bundle().apply {
          putParcelable(EXTRA_RECIPIENT, recipientId)
          putParcelable(EXTRA_IDENTITY, remoteIdentity)
          putBoolean(EXTRA_VERIFIED, verified)
        }
      }
    }
  }

  private val displayFragment by lazy {
    VerifyDisplayFragment.create(
      recipientId,
      remoteIdentity,
      IdentityKeyParcelable(SignalStore.account().aciIdentityKey.publicKey),
      Recipient.self().requireE164(),
      isVerified
    )
  }

  private val scanFragment = VerifyScanFragment()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    childFragmentManager.beginTransaction()
      .replace(R.id.fragment_container, displayFragment)
      .commitAllowingStateLoss()
  }

  private val recipientId: RecipientId
    get() = requireArguments().getParcelable(EXTRA_RECIPIENT)!!

  private val remoteIdentity: IdentityKeyParcelable
    get() = requireArguments().getParcelable(EXTRA_IDENTITY)!!

  private val isVerified: Boolean
    get() = requireArguments().getBoolean(EXTRA_VERIFIED)

  override fun onQrDataFound(data: String) {
    ThreadUtil.runOnMain {
      ServiceUtil.getVibrator(context).vibrate(50)
      childFragmentManager.popBackStack()
      displayFragment.setScannedFingerprint(data)
    }
  }

  override fun onQrCodeContainerClicked() {
    Permissions.with(this)
      .request(Manifest.permission.CAMERA)
      .ifNecessary()
      .withPermanentDenialDialog(getString(R.string.VerifyIdentityActivity_signal_needs_the_camera_permission_in_order_to_scan_a_qr_code_but_it_has_been_permanently_denied))
      .onAllGranted {
        childFragmentManager.beginTransaction()
          .setCustomAnimations(R.anim.slide_from_top, R.anim.slide_to_bottom, R.anim.slide_from_bottom, R.anim.slide_to_top)
          .replace(R.id.fragment_container, scanFragment)
          .addToBackStack(null)
          .commitAllowingStateLoss()
      }
      .onAnyDenied { Toast.makeText(requireContext(), R.string.VerifyIdentityActivity_unable_to_scan_qr_code_without_camera_permission, Toast.LENGTH_LONG).show() }
      .execute()
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }
}
