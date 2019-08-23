package org.thoughtcrime.securesms.loki

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.activity_new_conversation.*
import network.loki.messenger.R

class NewConversationFragment() : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.activity_new_conversation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        qrCodeButton.setOnClickListener {
            val activity = activity as NewConversationActivity
            activity.scanQRCode()
        }
        nextButton.setOnClickListener {
            val activity = activity as NewConversationActivity
            val hexEncodedPublicKey = publicKeyEditText.text.toString().trim()
            activity.startNewConversationIfPossible(hexEncodedPublicKey)
        }
    }

    override fun onResume() {
        super.onResume()
        val activity = activity as NewConversationActivity
        activity.supportActionBar!!.setTitle(R.string.activity_new_conversation_title)
    }
}