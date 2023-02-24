package org.thoughtcrime.securesms.components.settings.app.changenumber

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.phone.SmsRetriever
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.PlayServicesUtil
import org.thoughtcrime.securesms.util.PlayServicesUtil.PlayServicesStatus
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class ChangeNumberConfirmFragment : LoggingFragment(R.layout.fragment_change_number_confirm) {
  private lateinit var viewModel: ChangeNumberViewModel

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    viewModel = ChangeNumberUtil.getViewModel(this)

    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    toolbar.setTitle(R.string.ChangeNumberEnterPhoneNumberFragment__change_number)
    toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

    val confirmMessage: TextView = view.findViewById(R.id.change_number_confirm_new_number_message)
    confirmMessage.text = getString(R.string.ChangeNumberConfirmFragment__you_are_about_to_change_your_phone_number_from_s_to_s, viewModel.oldNumberState.fullFormattedNumber, viewModel.number.fullFormattedNumber)

    val newNumber: TextView = view.findViewById(R.id.change_number_confirm_new_number)
    newNumber.text = viewModel.number.fullFormattedNumber

    val editNumber: View = view.findViewById(R.id.change_number_confirm_edit_number)
    editNumber.setOnClickListener { findNavController().navigateUp() }

    val changeNumber: View = view.findViewById(R.id.change_number_confirm_change_number)
    changeNumber.setOnClickListener { onConfirm() }
  }

  private fun onConfirm() {
    val playServicesAvailable = PlayServicesUtil.getPlayServicesStatus(context) == PlayServicesStatus.SUCCESS

    if (playServicesAvailable) {
      val client = SmsRetriever.getClient(requireContext())
      val task = client.startSmsRetriever()

      task.addOnSuccessListener {
        Log.i(TAG, "Successfully registered SMS listener.")
        navigateToVerify(smsListenerEnabled = true)
      }

      task.addOnFailureListener { e ->
        Log.w(TAG, "Failed to register SMS listener.", e)
        navigateToVerify()
      }
    } else {
      navigateToVerify()
    }
  }

  private fun navigateToVerify(smsListenerEnabled: Boolean = false) {
    findNavController().safeNavigate(R.id.action_changePhoneNumberConfirmFragment_to_changePhoneNumberVerifyFragment, ChangeNumberVerifyFragmentArgs.Builder().setSmsListenerEnabled(smsListenerEnabled).build().toBundle())
  }

  companion object {
    private val TAG = Log.tag(ChangeNumberConfirmFragment::class.java)
  }
}
