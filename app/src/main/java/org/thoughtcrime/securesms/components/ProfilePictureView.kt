package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.annotation.DimenRes
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.android.synthetic.main.view_profile_picture.view.*
import network.loki.messenger.R
import org.session.libsession.avatars.ProfileContactPhoto
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.AvatarPlaceholderGenerator

class ProfilePictureView : RelativeLayout {
    lateinit var glide: GlideRequests
    var publicKey: String? = null
    var displayName: String? = null
    var additionalPublicKey: String? = null
    var additionalDisplayName: String? = null
    var isLarge = false

    private val profilePicturesCache = mutableMapOf<String, String?>()

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) { initialize() }

    private fun initialize() {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val contentView = inflater.inflate(R.layout.view_profile_picture, null)
        addView(contentView)
    }
    // endregion

    // region Updating
    fun update(recipient: Recipient, threadID: Long) {
        fun getUserDisplayName(publicKey: String): String {
            val contact = DatabaseComponent.get(context).sessionContactDatabase().getContactWithSessionID(publicKey)
            return contact?.displayName(Contact.ContactContext.REGULAR) ?: publicKey
        }
        fun isOpenGroupWithProfilePicture(recipient: Recipient): Boolean {
            return recipient.isOpenGroupRecipient && recipient.groupAvatarId != null
        }
        if (recipient.isGroupRecipient && !isOpenGroupWithProfilePicture(recipient)) {
            val members = DatabaseComponent.get(context).groupDatabase()
                    .getGroupMemberAddresses(recipient.address.toGroupString(), true)
                    .sorted()
                    .take(2)
                    .toMutableList()
            val pk = members.getOrNull(0)?.serialize() ?: ""
            publicKey = pk
            displayName = getUserDisplayName(pk)
            val apk = members.getOrNull(1)?.serialize() ?: ""
            additionalPublicKey = apk
            additionalDisplayName = getUserDisplayName(apk)
        } else {
            val publicKey = recipient.address.toString()
            this.publicKey = publicKey
            displayName = getUserDisplayName(publicKey)
            additionalPublicKey = null
        }
        update()
    }

    fun update() {
        val publicKey = publicKey ?: return
        val additionalPublicKey = additionalPublicKey
        if (additionalPublicKey != null) {
            setProfilePictureIfNeeded(doubleModeImageView1, publicKey, displayName, R.dimen.small_profile_picture_size)
            setProfilePictureIfNeeded(doubleModeImageView2, additionalPublicKey, additionalDisplayName, R.dimen.small_profile_picture_size)
            doubleModeImageViewContainer.visibility = View.VISIBLE
        } else {
            glide.clear(doubleModeImageView1)
            glide.clear(doubleModeImageView2)
            doubleModeImageViewContainer.visibility = View.INVISIBLE
        }
        if (additionalPublicKey == null && !isLarge) {
            setProfilePictureIfNeeded(singleModeImageView, publicKey, displayName, R.dimen.medium_profile_picture_size)
            singleModeImageView.visibility = View.VISIBLE
        } else {
            glide.clear(singleModeImageView)
            singleModeImageView.visibility = View.INVISIBLE
        }
        if (additionalPublicKey == null && isLarge) {
            setProfilePictureIfNeeded(largeSingleModeImageView, publicKey, displayName, R.dimen.large_profile_picture_size)
            largeSingleModeImageView.visibility = View.VISIBLE
        } else {
            glide.clear(largeSingleModeImageView)
            largeSingleModeImageView.visibility = View.INVISIBLE
        }
    }

    private fun setProfilePictureIfNeeded(imageView: ImageView, publicKey: String, displayName: String?, @DimenRes sizeResId: Int) {
        if (publicKey.isNotEmpty()) {
            val recipient = Recipient.from(context, Address.fromSerialized(publicKey), false)
            if (profilePicturesCache.containsKey(publicKey) && profilePicturesCache[publicKey] == recipient.profileAvatar) return
            val signalProfilePicture = recipient.contactPhoto
            val avatar = (signalProfilePicture as? ProfileContactPhoto)?.avatarObject
            if (signalProfilePicture != null && avatar != "0" && avatar != "") {
                glide.clear(imageView)
                glide.load(signalProfilePicture).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).circleCrop().into(imageView)
                profilePicturesCache[publicKey] = recipient.profileAvatar
            } else {
                val sizeInPX = resources.getDimensionPixelSize(sizeResId)
                glide.clear(imageView)
                glide.load(AvatarPlaceholderGenerator.generate(context, sizeInPX, publicKey, displayName))
                    .diskCacheStrategy(DiskCacheStrategy.ALL).circleCrop().into(imageView)
                profilePicturesCache[publicKey] = recipient.profileAvatar
            }
        } else {
            imageView.setImageDrawable(null)
        }
    }

    fun recycle() {
        profilePicturesCache.clear()
    }
    // endregion
}
