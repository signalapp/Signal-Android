package org.thoughtcrime.securesms.loki.redesign.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.android.synthetic.main.view_profile_picture.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.recipients.Recipient

class ProfilePictureView : RelativeLayout {
    lateinit var glide: GlideRequests
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
        fun setProfilePictureIfNeeded(imageView: ImageView, hexEncodedPublicKey: String) {
            glide.clear(imageView)
            if (hexEncodedPublicKey.isNotEmpty()) {
                val signalProfilePicture = Recipient.from(context, Address.fromSerialized(hexEncodedPublicKey), false).contactPhoto
                if (signalProfilePicture != null) {
                    glide.load(signalProfilePicture).diskCacheStrategy(DiskCacheStrategy.ALL).circleCrop().into(imageView)
                } else {
                    imageView.setImageDrawable(null)
                }
            } else {
                imageView.setImageDrawable(null)
            }
        }
        setProfilePictureIfNeeded(singleModeImageView, hexEncodedPublicKey)
        setProfilePictureIfNeeded(doubleModeImageView1, hexEncodedPublicKey)
        setProfilePictureIfNeeded(doubleModeImageView2, additionalHexEncodedPublicKey ?: "")
    }
    // endregion
}