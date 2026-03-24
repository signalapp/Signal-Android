package org.thoughtcrime.securesms.conversation.v2

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

class JumpToDateValidatorTest {

  private val zoneId = ZoneId.of("UTC")
  private val immediateExecutor = Executor { it.run() }

  private fun timestampForDate(year: Int, month: Int, day: Int): Long {
    return LocalDate.of(year, month, day)
      .atStartOfDay(zoneId)
      .toInstant()
      .toEpochMilli()
  }

  private fun createValidator(
    lookup: (Collection<Long>) -> Map<Long, Boolean> = { emptyMap() },
    executor: Executor = immediateExecutor,
    zone: ZoneId = zoneId
  ): JumpToDateValidator {
    return JumpToDateValidator.createForTesting(lookup, executor, zone)
  }

  @Test
  fun `when date is not cached, returns true`() {
    val neverExecutor = Executor { /* do nothing */ }
    val lookup = { _: Collection<Long> -> emptyMap<Long, Boolean>() }
    val validator = createValidator(lookup, neverExecutor)

    val result = validator.isValid(timestampForDate(2024, 6, 15))

    assertThat(result).isTrue()
  }

  @Test
  fun `when date has message after prefetch, returns true`() {
    val dateWithMessage = timestampForDate(2024, 6, 15)
    val lookup = { dates: Collection<Long> ->
      dates.associateWith { it == dateWithMessage }
    }
    val validator = createValidator(lookup)

    validator.isValid(dateWithMessage)

    assertThat(validator.isValid(dateWithMessage)).isTrue()
  }

  @Test
  fun `when date has no message after prefetch, returns false`() {
    val dateWithoutMessage = timestampForDate(2024, 6, 15)
    val lookup = { dates: Collection<Long> ->
      dates.associateWith { false }
    }
    val validator = createValidator(lookup)

    validator.isValid(dateWithoutMessage)

    assertThat(validator.isValid(dateWithoutMessage)).isFalse()
  }

  @Test
  fun `loads entire month of dates at once`() {
    val queriedDates = mutableListOf<Collection<Long>>()
    val lookup = { dates: Collection<Long> ->
      queriedDates.add(dates)
      dates.associateWith { false }
    }
    val validator = createValidator(lookup)

    validator.isValid(timestampForDate(2024, 6, 15))

    assertThat(queriedDates).isNotEmpty()

    val june1 = timestampForDate(2024, 6, 1)
    val june30 = timestampForDate(2024, 6, 30)
    val allQueriedTimestamps = queriedDates.flatten().toSet()
    assertThat(allQueriedTimestamps).contains(june1)
    assertThat(allQueriedTimestamps).contains(june30)

    val juneQuery = queriedDates.find { it.contains(june1) }!!
    assertThat(juneQuery.size).isGreaterThanOrEqualTo(30)
  }

  @Test
  fun `prefetches adjacent months`() {
    val queriedDates = mutableListOf<Collection<Long>>()
    val lookup = { dates: Collection<Long> ->
      queriedDates.add(dates)
      dates.associateWith { false }
    }
    val validator = createValidator(lookup)

    validator.isValid(timestampForDate(2024, 6, 15))

    assertThat(queriedDates.size).isGreaterThanOrEqualTo(2)

    val allQueriedTimestamps = queriedDates.flatten().toSet()
    val may15 = timestampForDate(2024, 5, 15)
    val july15 = timestampForDate(2024, 7, 15)
    assertThat(allQueriedTimestamps).contains(may15)
    assertThat(allQueriedTimestamps).contains(july15)
  }

  @Test
  fun `does not re-query same month`() {
    val queryCount = AtomicInteger(0)
    val lookup = { dates: Collection<Long> ->
      queryCount.incrementAndGet()
      dates.associateWith { false }
    }
    val validator = createValidator(lookup)

    validator.isValid(timestampForDate(2024, 6, 15))
    val initialCount = queryCount.get()

    validator.isValid(timestampForDate(2024, 6, 1))
    validator.isValid(timestampForDate(2024, 6, 30))

    assertThat(queryCount.get()).isEqualTo(initialCount)
  }

  @Test
  fun `handles timestamp normalization to local midnight`() {
    val june15Midnight = timestampForDate(2024, 6, 15)
    val june15Noon = june15Midnight + (12 * 60 * 60 * 1000)
    val june15Evening = june15Midnight + (20 * 60 * 60 * 1000)

    val lookup = { dates: Collection<Long> ->
      dates.associateWith { it == june15Midnight }
    }
    val validator = createValidator(lookup)

    validator.isValid(june15Midnight)

    assertThat(validator.isValid(june15Midnight)).isTrue()
    assertThat(validator.isValid(june15Noon)).isTrue()
    assertThat(validator.isValid(june15Evening)).isTrue()
  }

  @Test
  fun `returns true when lookup is pending`() {
    var shouldExecute = false
    val delayedExecutor = Executor { runnable ->
      if (shouldExecute) {
        runnable.run()
      }
    }

    val lookup = { dates: Collection<Long> ->
      dates.associateWith { false }
    }
    val validator = createValidator(lookup, delayedExecutor)

    assertThat(validator.isValid(timestampForDate(2024, 6, 15))).isTrue()
    assertThat(validator.isValid(timestampForDate(2024, 6, 15))).isTrue()

    shouldExecute = true
    validator.isValid(timestampForDate(2024, 6, 15))

    val dateInMonth = timestampForDate(2024, 6, 20)
    assertThat(validator.isValid(dateInMonth)).isTrue()
  }

  @Test
  fun `different zones calculate midnight correctly`() {
    val newYorkZone = ZoneId.of("America/New_York")
    val june15Utc = timestampForDate(2024, 6, 15)

    val lookup = { dates: Collection<Long> ->
      dates.associateWith { true }
    }
    val validator = createValidator(lookup, zone = newYorkZone)

    validator.isValid(june15Utc)

    assertThat(validator.isValid(june15Utc)).isTrue()
  }
}
