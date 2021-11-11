package org.thoughtcrime.securesms.badges

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.ProfileUtil

class BadgeRepository(context: Context) {

  private val context = context.applicationContext

  fun setVisibilityForAllBadges(
    displayBadgesOnProfile: Boolean,
    selfBadges: List<Badge> = Recipient.self().badges
  ): Completable = Completable.fromAction {
    val recipientDatabase: RecipientDatabase = DatabaseFactory.getRecipientDatabase(context)

    SignalStore.donationsValues().setDisplayBadgesOnProfile(displayBadgesOnProfile)

    recipientDatabase.markNeedsSync(Recipient.self().id)
    StorageSyncHelper.scheduleSyncForDataChange()

    val badges = selfBadges.map { it.copy(visible = displayBadgesOnProfile) }
    ProfileUtil.uploadProfileWithBadges(context, badges)

    recipientDatabase.setBadges(Recipient.self().id, badges)
  }.subscribeOn(Schedulers.io())

  fun setFeaturedBadge(featuredBadge: Badge): Completable = Completable.fromAction {
    val badges = Recipient.self().badges
    val reOrderedBadges = listOf(featuredBadge.copy(visible = true)) + (badges.filterNot { it.id == featuredBadge.id })
    ProfileUtil.uploadProfileWithBadges(context, reOrderedBadges)

    val recipientDatabase: RecipientDatabase = DatabaseFactory.getRecipientDatabase(context)
    recipientDatabase.setBadges(Recipient.self().id, reOrderedBadges)
  }.subscribeOn(Schedulers.io())
}
