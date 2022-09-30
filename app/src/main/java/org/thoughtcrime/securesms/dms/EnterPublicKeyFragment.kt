package org.thoughtcrime.securesms.dms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import network.loki.messenger.R
import network.loki.messenger.databinding.FragmentEnterPublicKeyBinding
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.util.QRCodeUtilities
import org.thoughtcrime.securesms.util.hideKeyboard
import org.thoughtcrime.securesms.util.toPx

class EnterPublicKeyFragment : Fragment() {
    private lateinit var binding: FragmentEnterPublicKeyBinding

    var delegate: EnterPublicKeyDelegate? = null

    private val hexEncodedPublicKey: String
        get() {
            return TextSecurePreferences.getLocalNumber(requireContext())!!
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentEnterPublicKeyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            publicKeyEditText.imeOptions = EditorInfo.IME_ACTION_DONE or 16777216 // Always use incognito keyboard
            publicKeyEditText.setRawInputType(InputType.TYPE_CLASS_TEXT)
            publicKeyEditText.setOnEditorActionListener { v, actionID, _ ->
                if (actionID == EditorInfo.IME_ACTION_DONE) {
                    v.hideKeyboard()
                    handlePublicKeyEntered()
                    true
                } else {
                    false
                }
            }
            publicKeyEditText.addTextChangedListener { text -> createPrivateChatButton.isVisible = !text.isNullOrBlank() }
            publicKeyEditText.setOnFocusChangeListener { _, hasFocus ->  optionalContentContainer.isVisible = !hasFocus }
            mainContainer.setOnTouchListener { _, _ ->
                binding.optionalContentContainer.isVisible = true
                publicKeyEditText.clearFocus()
                publicKeyEditText.hideKeyboard()
                true
            }
            val size = toPx(228, resources)
            val qrCode = QRCodeUtilities.encode(hexEncodedPublicKey, size, isInverted = false, hasTransparentBackground = false)
            qrCodeImageView.setImageBitmap(qrCode)
            publicKeyTextView.text = hexEncodedPublicKey
            publicKeyTextView.setOnCreateContextMenuListener { contextMenu, view, _ ->
                contextMenu.add(0, view.id, 0, R.string.copy).setOnMenuItemClickListener {
                    copyPublicKey()
                    true
                }
            }
            copyButton.setOnClickListener { copyPublicKey() }
            shareButton.setOnClickListener { sharePublicKey() }
            createPrivateChatButton.setOnClickListener { handlePublicKeyEntered(); publicKeyEditText.hideKeyboard() }
        }
    }

    private fun copyPublicKey() {
        val clipboard = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Session ID", hexEncodedPublicKey)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun sharePublicKey() {
        val intent = Intent()
        intent.action = Intent.ACTION_SEND
        intent.putExtra(Intent.EXTRA_TEXT, hexEncodedPublicKey)
        intent.type = "text/plain"
        startActivity(intent)
    }

    private fun handlePublicKeyEntered() {
        val hexEncodedPublicKey = binding.publicKeyEditText.text?.trim()?.toString()
        if (hexEncodedPublicKey.isNullOrEmpty()) return
        delegate?.handlePublicKeyEntered(hexEncodedPublicKey)
    }
}

fun interface EnterPublicKeyDelegate {
    fun handlePublicKeyEntered(publicKey: String)
}
