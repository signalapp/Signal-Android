package org.thoughtcrime.securesms.notifications

import org.junit.Assert.assertTrue
import org.junit.Test
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty0
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMemberProperties

class NotificationIdsTest {

  /** Ranges whose ids are a base plus a database id. New ranges in [NotificationIds] must be added here. */
  private val dynamicRanges = listOf(
    DynamicRange(NotificationIds::THREAD, size = 10_000_000, getter = NotificationIds::getNotificationIdForThread),
    DynamicRange(NotificationIds::MESSAGE_DELIVERY_FAILURE, size = 10_000_000, getter = NotificationIds::getNotificationIdForMessageDeliveryFailed),
    DynamicRange(NotificationIds::UNREAD_REMINDER, size = 10_000_000, getter = NotificationIds::getNotificationIdForUnreadReminder),
    DynamicRange(NotificationIds::STORY_THREAD, size = 900_000_000, getter = NotificationIds::getNotificationIdForThread),
    DynamicRange(NotificationIds::STORY_MESSAGE_DELIVERY_FAILURE, size = Int.MAX_VALUE.toLong() - NotificationIds.STORY_MESSAGE_DELIVERY_FAILURE, getter = NotificationIds::getNotificationIdForMessageDeliveryFailed)
  )

  private val fixedIds: Map<String, Int> by lazy {
    val rangeNames = dynamicRanges.map { it.name }.toSet()

    NotificationIds::class.declaredMemberProperties
      .filter { it.isConst && it.name !in rangeNames }
      .associate { it.name to it.getter.call() as Int }
  }

  @Test
  fun `fixed ids are unique`() {
    val duplicates = fixedIds.entries.groupBy({ it.value }, { it.key }).filterValues { it.size > 1 }

    assertTrue("Duplicate notification ids: $duplicates", duplicates.isEmpty())
  }

  @Test
  fun `fixed ids are positive`() {
    val nonPositive = fixedIds.filterValues { it <= 0 }

    assertTrue("Non-positive notification ids: $nonPositive", nonPositive.isEmpty())
  }

  @Test
  fun `fixed ids are below every dynamic range`() {
    val lowestRangeStart = dynamicRanges.minOf { it.start }
    val tooHigh = fixedIds.filterValues { it >= lowestRangeStart }

    assertTrue("Notification ids at or above the first dynamic range ($lowestRangeStart): $tooHigh", tooHigh.isEmpty())
  }

  @Test
  fun `dynamic ranges do not overlap`() {
    val overlaps = dynamicRanges
      .sortedBy { it.start }
      .zipWithNext()
      .filter { (lower, upper) -> lower.endExclusive > upper.start }
      .map { (lower, upper) -> "${lower.name} [${lower.start}, ${lower.endExclusive}) overlaps ${upper.name} starting at ${upper.start}" }

    assertTrue(overlaps.joinToString(separator = "\n"), overlaps.isEmpty())
  }

  @Test
  fun `dynamic ranges fit in a positive int`() {
    dynamicRanges.forEach {
      assertTrue("${it.name} starts at a non-positive id", it.start > 0)
      assertTrue("${it.name} ends past Int.MAX_VALUE", it.endExclusive - 1 <= Int.MAX_VALUE)
    }
  }

  @Test
  fun `every id getter belongs to a listed range`() {
    val listedGetters = dynamicRanges.map { it.getter.name }.toSet()
    val unlisted = NotificationIds::class.declaredFunctions
      .filter { it.visibility == KVisibility.PUBLIC && it.returnType.classifier == Int::class }
      .map { it.name }
      .filterNot { it in listedGetters }

    assertTrue("Id getters with no range in dynamicRanges: $unlisted", unlisted.isEmpty())
  }

  @Test
  fun `thread and story ids land in their ranges`() {
    val thread = rangeFor(NotificationIds::THREAD)
    val story = rangeFor(NotificationIds::STORY_THREAD)

    val threadId = NotificationIds.getNotificationIdForThread(ConversationId.forConversation(1))
    val storyId = NotificationIds.getNotificationIdForThread(ConversationId(1, 5_000_000))

    assertTrue("Thread id $threadId is outside ${thread.name}", threadId in thread)
    assertTrue("Story id $storyId is outside ${story.name}", storyId in story)
  }

  private fun rangeFor(base: KProperty0<Int>): DynamicRange = dynamicRanges.first { it.name == base.name }

  private class DynamicRange(base: KProperty0<Int>, val size: Long, val getter: KFunction<Int>) {
    val name: String = base.name
    val start: Int = base.get()
    val endExclusive: Long = start.toLong() + size

    operator fun contains(id: Int): Boolean = id >= start && id < endExclusive
  }
}
