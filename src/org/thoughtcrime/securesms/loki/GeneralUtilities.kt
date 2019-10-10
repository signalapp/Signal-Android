package org.thoughtcrime.securesms.loki

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.support.annotation.ColorRes
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.loki.api.LokiGroupChatAPI
import org.whispersystems.signalservice.loki.messaging.LokiThreadFriendRequestStatus
import kotlin.math.roundToInt

fun Resources.getColorWithID(@ColorRes id: Int, theme: Resources.Theme?): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        getColor(id, theme)
    } else {
        @Suppress("DEPRECATION") getColor(id)
    }
}

fun toPx(dp: Int, resources: Resources): Int {
    val scale = resources.displayMetrics.density
    return (dp * scale).roundToInt()
}

fun isGroupRecipient(context: Context, recipient: String): Boolean {
    return DatabaseFactory.getLokiThreadDatabase(context).getAllGroupChats().values.map { it.server }.contains(recipient)
}

fun getFriendPublicKeys(context: Context, devicePublicKeys: Set<String>): Set<String> {
    val lokiThreadDatabase = DatabaseFactory.getLokiThreadDatabase(context)
    return devicePublicKeys.mapNotNull { device ->
        val address = Address.fromSerialized(device)
        val recipient = Recipient.from(context, address, false)
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdIfExistsFor(recipient)
        if (threadID < 0) { return@mapNotNull null }
        val friendRequestStatus = lokiThreadDatabase.getFriendRequestStatus(threadID)
        if (friendRequestStatus == LokiThreadFriendRequestStatus.FRIENDS) device else null
    }.toSet()
}