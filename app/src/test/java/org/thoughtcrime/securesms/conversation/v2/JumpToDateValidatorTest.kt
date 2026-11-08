package org.thoughtcrime.securesms.conversation.v2

import android.app.Application
import android.os.Parcel
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class JumpToDateValidatorTest {

  private fun pickerValueFor(year: Int, month: Int, day: Int): Long {
    return LocalDate.of(year, month, day)
      .atStartOfDay(ZoneOffset.UTC)
      .toInstant()
      .toEpochMilli()
  }

  private fun validatorFor(vararg dates: LocalDate): JumpToDateValidator {
    return JumpToDateValidator.from(dates.map { it.toEpochDay() }.toSet())
  }

  @Test
  fun `when day has messages, returns true`() {
    val validator = validatorFor(LocalDate.of(2024, 6, 15))

    assertThat(validator.isValid(pickerValueFor(2024, 6, 15))).isTrue()
  }

  @Test
  fun `when day has no messages, returns false`() {
    val validator = validatorFor(LocalDate.of(2024, 6, 15))

    assertThat(validator.isValid(pickerValueFor(2024, 6, 14))).isFalse()
    assertThat(validator.isValid(pickerValueFor(2024, 6, 16))).isFalse()
  }

  @Test
  fun `when there are no days at all, everything is invalid`() {
    val validator = validatorFor()

    assertThat(validator.isValid(pickerValueFor(2024, 6, 15))).isFalse()
  }

  @Test
  fun `handles days on either side of the epoch`() {
    val validator = validatorFor(LocalDate.of(1969, 12, 31), LocalDate.of(1970, 1, 1))

    assertThat(validator.isValid(pickerValueFor(1969, 12, 31))).isTrue()
    assertThat(validator.isValid(pickerValueFor(1970, 1, 1))).isTrue()
    assertThat(validator.isValid(pickerValueFor(1969, 12, 30))).isFalse()
  }

  @Test
  fun `survives a parcel round trip`() {
    val validator = validatorFor(LocalDate.of(2024, 6, 15), LocalDate.of(2021, 1, 2))

    val parcel = Parcel.obtain()
    parcel.writeParcelable(validator, 0)
    parcel.setDataPosition(0)
    val restored = parcel.readParcelable<JumpToDateValidator>(JumpToDateValidator::class.java.classLoader)!!
    parcel.recycle()

    assertThat(restored.isValid(pickerValueFor(2024, 6, 15))).isTrue()
    assertThat(restored.isValid(pickerValueFor(2021, 1, 2))).isTrue()
    assertThat(restored.isValid(pickerValueFor(2024, 6, 16))).isFalse()
  }
}
