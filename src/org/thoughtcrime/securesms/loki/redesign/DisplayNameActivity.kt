package org.thoughtcrime.securesms.loki.redesign

import android.os.Bundle
import android.support.v7.app.ActionBar
import android.view.Gravity
import android.widget.ImageView
import android.widget.RelativeLayout
import kotlinx.android.synthetic.main.activity_display_name_v2.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity

class DisplayNameActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar!!.setDisplayShowHomeEnabled(false)
        supportActionBar!!.setDisplayShowTitleEnabled(false)
        val logoImageView = ImageView(this)
        logoImageView.setImageResource(R.drawable.session_logo)
        val logoImageViewContainer = RelativeLayout(this)
        logoImageViewContainer.addView(logoImageView)
        logoImageViewContainer.gravity = Gravity.CENTER
        val logoImageViewContainerLayoutParams = ActionBar.LayoutParams(ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.WRAP_CONTENT)
        supportActionBar!!.setCustomView(logoImageViewContainer, logoImageViewContainerLayoutParams)
        supportActionBar!!.setDisplayShowCustomEnabled(true)
        setContentView(R.layout.activity_display_name_v2)
    }

    override fun onResume() {
        super.onResume()
        displayNameEditText.requestFocus()
    }
}