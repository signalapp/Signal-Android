package org.thoughtcrime.securesms.loki.dialogs

import android.graphics.Typeface
import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import kotlinx.android.synthetic.main.fragment_multi_device_removal_bottom_sheet.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.loki.utilities.getColorWithID
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.text.SimpleDateFormat
import java.util.*

class MultiDeviceRemovalBottomSheet : BottomSheetDialogFragment() {
    var onOKTapped: (() -> Unit)? = null
    var onLinkTapped: (() -> Unit)? = null

    private val removalDate by lazy {
        val timeZone = TimeZone.getTimeZone("Australia/Melbourne")
        val calendar = GregorianCalendar.getInstance(timeZone)
        calendar.set(2020, 8 - 1, 6, 17, 0, 0)
        calendar.time
    }

    private val removalDateDescription by lazy {
        val formatter = SimpleDateFormat("MMMM d", Locale.getDefault())
        formatter.format(removalDate)
    }

    private val explanation by lazy {
        if (TextSecurePreferences.getMasterHexEncodedPublicKey(requireContext()) != null) {
            "You’re seeing this because this is a secondary device in a multi-device setup. To improve reliability and stability, we’ve decided to temporarily disable Session’s multi-device functionality. Device linking has been disabled, and existing secondary clients will be erased on $removalDateDescription.\n\nTo read more about this change, visit the Session FAQ at getsession.org/faq."
        } else {
            "You’re seeing this because you have a secondary device linked to your Session ID. To improve reliability and stability, we’ve decided to temporarily disable Session’s multi-device functionality. Device linking has been disabled, and existing secondary clients will be erased on $removalDateDescription.\n\nTo read more about this change, visit the Session FAQ at getsession.org/faq"
        }
    }

    private val decoratedExplanation by lazy {
        val result = SpannableStringBuilder(explanation)
        val removalDateStartIndex = explanation.indexOf(removalDateDescription)
        val removalDateEndIndex = removalDateStartIndex + removalDateDescription.count()
        result.setSpan(StyleSpan(Typeface.BOLD), removalDateStartIndex, removalDateEndIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        result.setSpan(ForegroundColorSpan(resources.getColorWithID(R.color.accent, requireContext().theme)), removalDateStartIndex, removalDateEndIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val link = "getsession.org/faq"
        val linkStartIndex = explanation.indexOf(link)
        val linkEndIndex = linkStartIndex + link.count()
        result.setSpan(object : ClickableSpan() {

            override fun onClick(widget: View) {
                try {
                    onLinkTapped?.invoke()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), R.string.invalid_url, Toast.LENGTH_SHORT).show()
                }
            }
        }, linkStartIndex, linkEndIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        result.setSpan(StyleSpan(Typeface.BOLD), linkStartIndex, linkEndIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        result.setSpan(ForegroundColorSpan(resources.getColorWithID(R.color.accent, requireContext().theme)), linkStartIndex, linkEndIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        result
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_multi_device_removal_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        explanationTextView.movementMethod = LinkMovementMethod.getInstance()
        explanationTextView.text = decoratedExplanation
        okButton.setOnClickListener { onOKTapped?.invoke() }
    }
}