package org.thoughtcrime.securesms.loki

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_new_conversation.*
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.ConversationActivity
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.DynamicTheme
import org.whispersystems.signalservice.loki.utilities.PublicKeyValidation

class NewConversationActivity : PassphraseRequiredActionBarActivity() {
    private val dynamicTheme = DynamicTheme()

    override fun onPreCreate() {
        dynamicTheme.onCreate(this)
    }

    override fun onCreate(bundle: Bundle?, isReady: Boolean) {
        setContentView(R.layout.activity_new_conversation)
        supportActionBar!!.setTitle(R.string.activity_new_conversation_title)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        nextButton.setOnClickListener { startNewConversationIfPossible() }
    }

    public override fun onResume() {
        super.onResume()
        dynamicTheme.onResume(this)
    }

    private fun startNewConversationIfPossible() {
        val hexEncodedPublicKey = publicKeyEditText.text.toString().trim()
        if (PublicKeyValidation.isValid(hexEncodedPublicKey)) {
            val contact = Recipient.from(this, Address.fromSerialized(hexEncodedPublicKey), true)
            val intent = Intent(this, ConversationActivity::class.java)
            intent.putExtra(ConversationActivity.ADDRESS_EXTRA, contact.address)
            intent.putExtra(ConversationActivity.TEXT_EXTRA, getIntent().getStringExtra(ConversationActivity.TEXT_EXTRA))
            intent.setDataAndType(getIntent().data, getIntent().type)
            val existingThread = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(contact)
            intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, existingThread)
            intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT)
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, R.string.activity_new_conversation_invalid_public_key_message, Toast.LENGTH_SHORT).show()
        }
    }
}