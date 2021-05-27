package org.thoughtcrime.securesms.loki.activities

import android.os.Bundle
import kotlinx.android.synthetic.main.activity_open_group_guidelines.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity

class OpenGroupGuidelinesActivity : BaseActionBarActivity() {

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open_group_guidelines)
        communityGuidelinesTextView.text = """
        Welcome to Oxen.

        Oxen believes privacy is an important part of our future. People have been safeguarding the right to privacy since the dawn of humanity, but the digital world has turned privacy into a privilege. Enough is enough. We're taking it back. For you. For us. For everyone.
        
        Oxen is a private technology stack including the Oxen blockchain, Oxen service nodes (Session’s servers), the Oxen cryptocurrency, an onion-router called Lokinet, and Session itself.
        
        Oxen is what makes Session possible — Session is running on the Oxen network right now.
        
        Find out more at https://oxen.io, or ask here!
        
        —
        
        In order for our open group to be a fun environment, full of robust and constructive discussion, please follow these four simple rules:
        
        1. Keep conversations on-topic and add value to the discussion.
        
        (No referral links, spamming, or off-topic discussion)
        
        2. You don't have to love everyone, but be civil.
        
        (No baiting, excessively partisan arguments, threats, and so on. Use common sense.)
        
        3. Do not be a shill. Comparison and criticism is reasonable, but blatant shilling is not.
        
        4. Don't post explicit content, be it excessive offensive language, or content which is sexual or violent in nature.
        
        If you break these rules, you’ll be warned by an admin. If your behaviour doesn’t improve, you will be removed from the open group.
        
        If you see or experience any destructive behaviour, please contact an admin.
        
        ——————————
        
        SCAMMER WARNING
        
        Trust only those with an admin crown in chat. No admin will ever DM you first. No admin will ever message you for Oxen coins.
        """.trimIndent()
    }
    // endregion
}