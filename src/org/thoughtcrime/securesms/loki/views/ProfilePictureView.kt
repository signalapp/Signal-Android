package org.thoughtcrime.securesms.loki.views

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
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.utilities.AvatarPlaceholderGenerator
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.protocol.mentions.MentionsManager

// TODO: Look into a better way of handling different sizes. Maybe an enum (with associated values) encapsulating the different modes?

class ProfilePictureView : RelativeLayout {
    lateinit var glide: GlideRequests
    var publicKey: String? = null
    var displayName: String? = null
    var additionalPublicKey: String? = null
    var additionalDisplayName: String? = null
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
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val contentView = inflater.inflate(R.layout.view_profile_picture, null)
        addView(contentView)
    }
    // endregion

    // region Updating
    fun update(recipient: Recipient, threadID: Long) {
        fun getUserDisplayName(publicKey: String?): String? {
            if (publicKey == null || publicKey.isBlank()) {
                return null
            } else {
                var result = DatabaseFactory.getLokiUserDatabase(context).getDisplayName(publicKey)
                val publicChat = DatabaseFactory.getLokiThreadDatabase(context).getPublicChat(threadID)
                if (result == null && publicChat != null) {
                    result = DatabaseFactory.getLokiUserDatabase(context).getServerDisplayName(publicChat.id, publicKey)
                }
                return result ?: publicKey
            }
        }
        fun isOpenGroupWithProfilePicture(recipient: Recipient): Boolean {
            return recipient.isOpenGroupRecipient && recipient.groupAvatarId != null
        }
        if (recipient.isGroupRecipient && !isOpenGroupWithProfilePicture(recipient)) {
            val users = MentionsManager.shared.userPublicKeyCache[threadID]?.toMutableList() ?: mutableListOf()
            users.remove(TextSecurePreferences.getLocalNumber(context))
            val masterPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context)
            if (masterPublicKey != null) {
                users.remove(masterPublicKey)
            }
            val randomUsers = users.sorted().toMutableList() // Sort to provide a level of stability
            if (users.count() == 1) {
                val userPublicKey = TextSecurePreferences.getLocalNumber(context)
                randomUsers.add(0, userPublicKey) // Ensure the current user is at the back visually
            }
            val pk = randomUsers.getOrNull(0) ?: ""
            publicKey = pk
            displayName = getUserDisplayName(pk)
            val apk = randomUsers.getOrNull(1) ?: ""
            additionalPublicKey = apk
            additionalDisplayName = getUserDisplayName(apk)
            isRSSFeed = recipient.name == "Loki News" ||
                    recipient.name == "Session Updates" ||
                    recipient.name == "Session Public Chat"
        } else {
            publicKey = recipient.address.toString()
            displayName = getUserDisplayName(publicKey)
            additionalPublicKey = null
            isRSSFeed = false
        }
        update()
    }

    fun update() {
        val publicKey = publicKey ?: return
        val additionalPublicKey = additionalPublicKey
        doubleModeImageViewContainer.visibility = if (additionalPublicKey != null && !isRSSFeed) View.VISIBLE else View.INVISIBLE
        singleModeImageViewContainer.visibility = if (additionalPublicKey == null && !isRSSFeed && !isLarge) View.VISIBLE else View.INVISIBLE
        largeSingleModeImageViewContainer.visibility = if (additionalPublicKey == null && !isRSSFeed && isLarge) View.VISIBLE else View.INVISIBLE
        rssImageView.visibility = if (isRSSFeed) View.VISIBLE else View.INVISIBLE
        setProfilePictureIfNeeded(
                doubleModeImageView1,
                publicKey,
                displayName,
                R.dimen.small_profile_picture_size)
        setProfilePictureIfNeeded(
                doubleModeImageView2,
                additionalPublicKey ?: "",
                additionalDisplayName,
                R.dimen.small_profile_picture_size)
        setProfilePictureIfNeeded(
                singleModeImageView,
                publicKey,
                displayName,
                R.dimen.medium_profile_picture_size)
        setProfilePictureIfNeeded(
                largeSingleModeImageView,
                publicKey,
                displayName,
                R.dimen.large_profile_picture_size)
    }

    private fun setProfilePictureIfNeeded(imageView: ImageView, publicKey: String, displayName: String?, @DimenRes sizeResId: Int) {
        glide.clear(imageView)
        if (publicKey.isNotEmpty()) {
            val recipient = Recipient.from(context, Address.fromSerialized(publicKey), false);
            val signalProfilePicture = recipient.contactPhoto
            if (signalProfilePicture != null && (signalProfilePicture as? ProfileContactPhoto)?.avatarObject != "0"
                && (signalProfilePicture as? ProfileContactPhoto)?.avatarObject != "") {
                glide.load(signalProfilePicture).diskCacheStrategy(DiskCacheStrategy.ALL).circleCrop().into(imageView)
            } else {
                val sizeInPX = resources.getDimensionPixelSize(sizeResId)
                val masterPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context)
                val hepk = if (recipient.isLocalNumber && masterPublicKey != null) masterPublicKey else publicKey
                glide.load(AvatarPlaceholderGenerator.generate(
                        context,
                        sizeInPX,
                        hepk,
                        displayName
                )).diskCacheStrategy(DiskCacheStrategy.ALL).circleCrop().into(imageView)
            }
        } else {
            imageView.setImageDrawable(null)
        }
    }
    // endregion
}