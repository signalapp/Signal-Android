package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.annotation.DimenRes
import com.bumptech.glide.load.engine.DiskCacheStrategy
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewProfilePictureBinding
import org.session.libsession.avatars.ProfileContactPhoto
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.AvatarPlaceholderGenerator

class ProfilePictureView : RelativeLayout {
    private lateinit var binding: ViewProfilePictureBinding
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
        binding = ViewProfilePictureBinding.inflate(LayoutInflater.from(context), this, true)
    }
    // endregion

    // region Updating
    fun update(recipient: Recipient) {
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
        if (!this::glide.isInitialized) return
        val publicKey = publicKey ?: return
        val additionalPublicKey = additionalPublicKey
        if (additionalPublicKey != null) {
            setProfilePictureIfNeeded(binding.doubleModeImageView1, publicKey, displayName, R.dimen.small_profile_picture_size)
            setProfilePictureIfNeeded(binding.doubleModeImageView2, additionalPublicKey, additionalDisplayName, R.dimen.small_profile_picture_size)
            binding.doubleModeImageViewContainer.visibility = View.VISIBLE
        } else {
            glide.clear(binding.doubleModeImageView1)
            glide.clear(binding.doubleModeImageView2)
            binding.doubleModeImageViewContainer.visibility = View.INVISIBLE
        }
        if (additionalPublicKey == null && !isLarge) {
            setProfilePictureIfNeeded(binding.singleModeImageView, publicKey, displayName, R.dimen.medium_profile_picture_size)
            binding.singleModeImageView.visibility = View.VISIBLE
        } else {
            glide.clear(binding.singleModeImageView)
            binding.singleModeImageView.visibility = View.INVISIBLE
        }
        if (additionalPublicKey == null && isLarge) {
            setProfilePictureIfNeeded(binding.largeSingleModeImageView, publicKey, displayName, R.dimen.large_profile_picture_size)
            binding.largeSingleModeImageView.visibility = View.VISIBLE
        } else {
            glide.clear(binding.largeSingleModeImageView)
            binding.largeSingleModeImageView.visibility = View.INVISIBLE
        }
    }

    private fun setProfilePictureIfNeeded(imageView: ImageView, publicKey: String, displayName: String?, @DimenRes sizeResId: Int) {
        if (publicKey.isNotEmpty()) {
            val recipient = Recipient.from(context, Address.fromSerialized(publicKey), false)
            if (profilePicturesCache.containsKey(publicKey) && profilePicturesCache[publicKey] == recipient.profileAvatar) return
            val signalProfilePicture = recipient.contactPhoto
            val avatar = (signalProfilePicture as? ProfileContactPhoto)?.avatarObject
            val sizeInPX = resources.getDimensionPixelSize(sizeResId)
            if (signalProfilePicture != null && avatar != "0" && avatar != "") {
                glide.clear(imageView)
                glide.load(signalProfilePicture)
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .circleCrop()
                        .error(AvatarPlaceholderGenerator.generate(context,sizeInPX, publicKey, displayName))
                        .into(imageView)
                profilePicturesCache[publicKey] = recipient.profileAvatar
            } else {
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
