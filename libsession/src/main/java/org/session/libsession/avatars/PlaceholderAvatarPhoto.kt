package org.session.libsession.avatars

import com.bumptech.glide.load.Key
import java.security.MessageDigest

class PlaceholderAvatarPhoto(val hashString: String,
                             val displayName: String): Key {

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(hashString.encodeToByteArray())
        messageDigest.update(displayName.encodeToByteArray())
    }
}