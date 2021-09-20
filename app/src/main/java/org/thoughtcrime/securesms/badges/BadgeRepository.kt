package org.thoughtcrime.securesms.badges

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ProfileUtil

class BadgeRepository(context: Context) {

  private val context = context.applicationContext

  fun setVisibilityForAllBadges(displayBadgesOnProfile: Boolean): Completable = Completable.fromAction {
    val badges = Recipient.self().badges.map { it.copy(visible = displayBadgesOnProfile) }
    ProfileUtil.uploadProfileWithBadges(context, badges)

    val recipientDatabase: RecipientDatabase = DatabaseFactory.getRecipientDatabase(context)
    recipientDatabase.setBadges(Recipient.self().id, badges)
  }.subscribeOn(Schedulers.io())
}
