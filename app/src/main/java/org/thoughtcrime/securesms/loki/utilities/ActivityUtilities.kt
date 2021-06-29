package org.thoughtcrime.securesms.loki.utilities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity

fun BaseActionBarActivity.setUpActionBarSessionLogo(hideBackButton: Boolean = false) {
    val actionbar = supportActionBar!!

    actionbar.setDisplayShowHomeEnabled(false)
    actionbar.setDisplayShowTitleEnabled(false)
    actionbar.setDisplayHomeAsUpEnabled(false)
    actionbar.setHomeButtonEnabled(false)

    actionbar.setCustomView(R.layout.session_logo_action_bar_content)
    actionbar.setDisplayShowCustomEnabled(true)

    val rootView: Toolbar = actionbar.customView!!.parent as Toolbar
    rootView.setPadding(0,0,0,0)
    rootView.setContentInsetsAbsolute(0,0);

    val backButton = actionbar.customView!!.findViewById<View>(R.id.back_button)
    if (hideBackButton) {
        backButton.visibility = View.GONE
    } else {
        backButton.visibility = View.VISIBLE
        backButton.setOnClickListener {
            onSupportNavigateUp()
        }
    }
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

interface ActivityDispatcher {
    companion object {
        const val SERVICE = "ActivityDispatcher_SERVICE"
        @SuppressLint("WrongConstant")
        fun get(context: Context) = context.getSystemService(SERVICE) as? ActivityDispatcher
    }
    fun dispatchIntent(body: (Context)->Intent?)
}