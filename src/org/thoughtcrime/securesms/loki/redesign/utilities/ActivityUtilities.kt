package org.thoughtcrime.securesms.loki.redesign.utilities

import android.content.Intent
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.widget.ImageView
import android.widget.RelativeLayout
import network.loki.messenger.R

fun AppCompatActivity.setUpActionBarSessionLogo() {
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
}

val AppCompatActivity.defaultSessionRequestCode: Int
    get() = 42

fun AppCompatActivity.push(intent: Intent, isForResult: Boolean = false) {
    if (isForResult) {
        startActivityForResult(intent, defaultSessionRequestCode)
    } else {
        startActivity(intent)
    }
    overridePendingTransition(R.anim.slide_from_right, R.anim.fade_scale_out)
}

fun AppCompatActivity.show(intent: Intent, isForResult: Boolean = false) {
    if (isForResult) {
        startActivityForResult(intent, defaultSessionRequestCode)
    } else {
        startActivity(intent)
    }
    overridePendingTransition(R.anim.slide_from_bottom, R.anim.fade_scale_out)
}