package org.thoughtcrime.securesms.conversation.v2

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.fragment_modal_url_bottom_sheet.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.util.UiModeUtilities

class ModalUrlBottomSheet(private val url: String): BottomSheetDialogFragment(), View.OnClickListener {

    override fun onCreateView(inflater: LayoutInflater,container: ViewGroup?,savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_modal_url_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val explanation = resources.getString(R.string.dialog_open_url_explanation, url)
        val spannable = SpannableStringBuilder(explanation)
        val startIndex = explanation.indexOf(url)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startIndex, startIndex + url.count(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        openURLExplanationTextView.text = spannable
        cancelButton.setOnClickListener(this)
        copyButton.setOnClickListener(this)
        openURLButton.setOnClickListener(this)
    }

    private fun open() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            requireContext().startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.invalid_url, Toast.LENGTH_SHORT).show()
        }
        dismiss()
    }

    private fun copy() {
        val clip = ClipData.newPlainText("URL", url)
        val manager = requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        dismiss()
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        val isLightMode = UiModeUtilities.isDayUiMode(requireContext())
        window.setDimAmount(if (isLightMode) 0.1f else 0.75f)
    }

    override fun onClick(v: View?) {
        when (v) {
            openURLButton -> open()
            copyButton -> copy()
            cancelButton -> dismiss()
        }
    }
}