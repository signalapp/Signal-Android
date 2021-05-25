package org.thoughtcrime.securesms.loki.utilities

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Range
import network.loki.messenger.R
import nl.komponents.kovenant.combine.Tuple2
import org.session.libsession.messaging.contacts.Contact
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.session.libsession.utilities.TextSecurePreferences
import java.util.regex.Pattern

object MentionUtilities {

    @JvmStatic
    fun highlightMentions(text: CharSequence, threadID: Long, context: Context): String {
        return highlightMentions(text, false, threadID, context).toString() // isOutgoingMessage is irrelevant
    }

    @JvmStatic
    fun highlightMentions(text: CharSequence, isOutgoingMessage: Boolean, threadID: Long, context: Context): SpannableString {
        var text = text
        val threadDB = DatabaseFactory.getThreadDatabase(context)
        val isOpenGroup = threadDB.getRecipientForThreadId(threadID)?.isOpenGroupRecipient ?: false
        val pattern = Pattern.compile("@[0-9a-fA-F]*")
        var matcher = pattern.matcher(text)
        val mentions = mutableListOf<Tuple2<Range<Int>, String>>()
        var startIndex = 0
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
        if (matcher.find(startIndex)) {
            while (true) {
                val publicKey = text.subSequence(matcher.start() + 1, matcher.end()).toString() // +1 to get rid of the @
                val userDisplayName: String? = if (publicKey.equals(userPublicKey, ignoreCase = true)) {
                    TextSecurePreferences.getProfileName(context)
                } else {
                    val contact = DatabaseFactory.getSessionContactDatabase(context).getContactWithSessionID(publicKey)
                    val context = if (isOpenGroup) Contact.ContactContext.OPEN_GROUP else Contact.ContactContext.REGULAR
                    contact?.displayName(context)
                }
                if (userDisplayName != null) {
                    text = text.subSequence(0, matcher.start()).toString() + "@" + userDisplayName + text.subSequence(matcher.end(), text.length)
                    val endIndex = matcher.start() + 1 + userDisplayName.length
                    startIndex = endIndex
                    mentions.add(Tuple2(Range.create(matcher.start(), endIndex), publicKey))
                } else {
                    startIndex = matcher.end()
                }
                matcher = pattern.matcher(text)
                if (!matcher.find(startIndex)) { break }
            }
        }
        val result = SpannableString(text)
        for (mention in mentions) {
            val isLightMode = UiModeUtilities.isDayUiMode(context)
            val colorID = if (isLightMode && isOutgoingMessage) R.color.black else R.color.accent
            result.setSpan(ForegroundColorSpan(context.resources.getColorWithID(colorID, context.theme)), mention.first.lower, mention.first.upper, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            result.setSpan(StyleSpan(Typeface.BOLD), mention.first.lower, mention.first.upper, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return result
    }
}