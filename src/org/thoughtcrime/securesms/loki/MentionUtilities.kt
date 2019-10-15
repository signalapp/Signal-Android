package org.thoughtcrime.securesms.loki

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.util.Range
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.util.regex.Pattern

object MentionUtilities {

    @JvmStatic
    fun highlightMentions(text: CharSequence, threadID: Long, context: Context): String {
        return MentionUtilities.highlightMentions(text, false, threadID, context).toString() // isOutgoingMessage is irrelevant
    }

    @JvmStatic
    fun highlightMentions(text: CharSequence, isOutgoingMessage: Boolean, threadID: Long, context: Context): SpannableString {
        var text = text
        val pattern = Pattern.compile("@[0-9a-fA-F]*")
        var matcher = pattern.matcher(text)
        val mentions = mutableListOf<Range<Int>>()
        var startIndex = 0
        val publicChat = DatabaseFactory.getLokiThreadDatabase(context).getPublicChat(threadID)
        if (matcher.find(startIndex)) {
            while (true) {
                val hexEncodedPublicKey = text.subSequence(matcher.start() + 1, matcher.end()).toString() // +1 to get rid of the @
                val userDisplayName: String? = if (hexEncodedPublicKey.toLowerCase() == TextSecurePreferences.getLocalNumber(context).toLowerCase()) {
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
                    mentions.add(Range.create(matcher.start(), endIndex))
                } else {
                    startIndex = matcher.end()
                }
                matcher = pattern.matcher(text)
                if (!matcher.find(startIndex)) { break }
            }
        }
        val result = SpannableString(text)
        for (range in mentions) {
            val highlightColor = if (isOutgoingMessage) context.resources.getColor(R.color.loki_dark_green) else context.resources.getColor(R.color.loki_green)
            result.setSpan(BackgroundColorSpan(highlightColor), range.lower, range.upper, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return result
    }
}