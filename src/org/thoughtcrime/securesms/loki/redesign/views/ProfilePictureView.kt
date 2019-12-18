package org.thoughtcrime.securesms.loki.redesign.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import kotlinx.android.synthetic.main.view_profile_picture.view.*
import network.loki.messenger.R

class ProfilePictureView : RelativeLayout {
    var hexEncodedPublicKey: String? = null
    var additionalHexEncodedPublicKey: String? = null
    var isRSSFeed = false

    // region Lifecycle
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
        val inflater = context.applicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val contentView = inflater.inflate(R.layout.view_profile_picture, null)
        addView(contentView)
    }
    // endregion

    // region Updating
    fun update() {
        val hexEncodedPublicKey = hexEncodedPublicKey ?: return
        val additionalHexEncodedPublicKey = additionalHexEncodedPublicKey
        doubleModeImageViewContainer.visibility = if (additionalHexEncodedPublicKey != null && !isRSSFeed) View.VISIBLE else View.INVISIBLE
        singleModeImageView.visibility = if (additionalHexEncodedPublicKey == null && !isRSSFeed) View.VISIBLE else View.INVISIBLE
        rssTextView.visibility = if (isRSSFeed) View.VISIBLE else View.INVISIBLE
    }
    // endregion
}