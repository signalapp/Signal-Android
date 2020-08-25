package org.thoughtcrime.securesms.loki.views

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.view_seed_reminder.view.*
import network.loki.messenger.R

class SeedReminderView : FrameLayout {
    var title: CharSequence
        get() = titleTextView.text
        set(value) { titleTextView.text = value }
    var subtitle: CharSequence
        get() = subtitleTextView.text
        set(value) { subtitleTextView.text = value }
    var delegate: SeedReminderViewDelegate? = null

    constructor(context: Context) : super(context) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        setUpViewHierarchy()
    }

    private fun setUpViewHierarchy() {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val contentView = inflater.inflate(R.layout.view_seed_reminder, null)
        addView(contentView)
        button.setOnClickListener { delegate?.handleSeedReminderViewContinueButtonTapped() }
    }

    fun setProgress(progress: Int, isAnimated: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            progressBar.setProgress(progress, isAnimated)
        } else {
            progressBar.progress = progress
        }
    }

    fun hideContinueButton() {
        button.visibility = View.GONE
    }
}

interface SeedReminderViewDelegate {

    fun handleSeedReminderViewContinueButtonTapped()
}