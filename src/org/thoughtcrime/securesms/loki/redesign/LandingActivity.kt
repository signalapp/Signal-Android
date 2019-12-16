package org.thoughtcrime.securesms.loki.redesign

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.ActionBar
import android.view.Gravity
import android.widget.ImageView
import android.widget.RelativeLayout
import kotlinx.android.synthetic.main.activity_landing.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity

class LandingActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landing)
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
        fakeChatView.startAnimating()
        registerButton.setOnClickListener { register() }
        restoreButton.setOnClickListener { restore() }
    }

    private fun register() {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
    }

    private fun restore() {
        val intent = Intent(this, RestoreActivity::class.java)
        startActivity(intent)
    }
}