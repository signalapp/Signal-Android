package org.thoughtcrime.securesms.loki.redesign.views

import android.content.Context
import android.support.annotation.DimenRes
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.android.synthetic.main.view_profile_picture.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.loki.JazzIdenticonDrawable
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.recipients.Recipient

// TODO: Look into a better way of handling different sizes. Maybe an enum (with associated values) encapsulating the different modes?

class ProfilePictureView : RelativeLayout {
    lateinit var glide: GlideRequests
    var hexEncodedPublicKey: String? = null
    var additionalHexEncodedPublicKey: String? = null
    var isRSSFeed = false
    var isLarge = false

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
        singleModeImageViewContainer.visibility = if (additionalHexEncodedPublicKey == null && !isRSSFeed && !isLarge) View.VISIBLE else View.INVISIBLE
        largeSingleModeImageViewContainer.visibility = if (additionalHexEncodedPublicKey == null && !isRSSFeed && isLarge) View.VISIBLE else View.INVISIBLE
        rssTextView.visibility = if (isRSSFeed) View.VISIBLE else View.INVISIBLE
        fun setProfilePictureIfNeeded(imageView: ImageView, hexEncodedPublicKey: String, @DimenRes sizeID: Int) {
            glide.clear(imageView)
            if (hexEncodedPublicKey.isNotEmpty()) {
                val signalProfilePicture = Recipient.from(context, Address.fromSerialized(hexEncodedPublicKey), false).contactPhoto
                if (signalProfilePicture != null && (signalProfilePicture as? ProfileContactPhoto)?.avatarObject != "0") {
                    glide.load(signalProfilePicture).diskCacheStrategy(DiskCacheStrategy.ALL).circleCrop().into(imageView)
                } else {
                    val size = resources.getDimensionPixelSize(sizeID)
                    val jazzIcon = JazzIdenticonDrawable(size, size, hexEncodedPublicKey)
                    glide.load(jazzIcon).diskCacheStrategy(DiskCacheStrategy.ALL).circleCrop().into(imageView)
                }
            } else {
                imageView.setImageDrawable(null)
            }
        }
        setProfilePictureIfNeeded(doubleModeImageView1, hexEncodedPublicKey, R.dimen.small_profile_picture_size)
        setProfilePictureIfNeeded(doubleModeImageView2, additionalHexEncodedPublicKey ?: "", R.dimen.small_profile_picture_size)
        setProfilePictureIfNeeded(singleModeImageView, hexEncodedPublicKey, R.dimen.medium_profile_picture_size)
        setProfilePictureIfNeeded(largeSingleModeImageView, hexEncodedPublicKey, R.dimen.large_profile_picture_size)
    }
    // endregion
}