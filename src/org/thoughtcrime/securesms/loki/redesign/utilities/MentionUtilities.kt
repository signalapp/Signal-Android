package org.thoughtcrime.securesms.loki.redesign.utilities

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Range
import network.loki.messenger.R
import nl.komponents.kovenant.combine.Tuple2
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.getColorWithID
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.util.regex.Pattern

object MentionUtilities {

    @JvmStatic
    fun highlightMentions(text: CharSequence, threadID: Long, context: Context): String {
        return highlightMentions(text, false, threadID, context).toString() // isOutgoingMessage is irrelevant
    }

    @JvmStatic
    fun highlightMentions(text: CharSequence, isOutgoingMessage: Boolean, threadID: Long, context: Context): SpannableString {
        var text = text
        val pattern = Pattern.compile("@[0-9a-fA-F]*")
        var matcher = pattern.matcher(text)
        val mentions = mutableListOf<Tuple2<Range<Int>, String>>()
        var startIndex = 0
        val publicChat = DatabaseFactory.getLokiThreadDatabase(context).getPublicChat(threadID)
        val userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context)
        if (matcher.find(startIndex)) {
            while (true) {
                val hexEncodedPublicKey = text.subSequence(matcher.start() + 1, matcher.end()).toString() // +1 to get rid of the @
                val userDisplayName: String? = if (hexEncodedPublicKey.toLowerCase() == userHexEncodedPublicKey.toLowerCase()) {
                    TextSecurePreferences.getProfileName(context)
                } else if (publicChat != null) {
                    DatabaseFactory.getLokiUserDatabase(context).getServerDisplayName(publicChat.id, hexEncodedPublicKey)
                } else {
                    DatabaseFactory.getLokiUserDatabase(context).getDisplayName(hexEncodedPublicKey)
                }
                if (userDisplayName != null) {
                    text = text.subSequence(0, matcher.start()).toString() + "@" + userDisplayName + text.subSequence(matcher.end(), text.length)
                    val endIndex = matcher.start() + 1 + userDisplayName.length
                    startIndex = endIndex
                    mentions.add(Tuple2(Range.create(matcher.start(), endIndex), hexEncodedPublicKey))
                } else {
                    startIndex = matcher.end()
                }
                matcher = pattern.matcher(text)
                if (!matcher.find(startIndex)) { break }
            }
        }
        val result = SpannableString(text)
        val userLinkedDeviceHexEncodedPublicKeys = DatabaseFactory.getLokiAPIDatabase(context).getPairingAuthorisations(userHexEncodedPublicKey).flatMap { listOf( it.primaryDevicePublicKey, it.secondaryDevicePublicKey ) }.toMutableSet()
        userLinkedDeviceHexEncodedPublicKeys.add(userHexEncodedPublicKey)
        for (mention in mentions) {
            if (!userLinkedDeviceHexEncodedPublicKeys.contains(mention.second)) { continue }
            result.setSpan(ForegroundColorSpan(context.resources.getColorWithID(R.color.accent, context.theme)), mention.first.lower, mention.first.upper, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            result.setSpan(StyleSpan(Typeface.BOLD), mention.first.lower, mention.first.upper, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return result
    }
}