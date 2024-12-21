package org.thoughtcrime.securesms.util.livedata

import androidx.lifecycle.MutableLiveData
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@Suppress("ClassName")
class LiveDataUtilTest_skip {
  @get:Rule
  val rule: TestRule = LiveDataRule()

  @Test
  fun skip_no_value() {
    val liveData = MutableLiveData<String>()

    val skipped = LiveDataUtil.skip(liveData, 0)

    LiveDataTestUtil.assertNoValue(skipped)
  }

  @Test
  fun skip_same_value_with_zero_skip() {
    val liveData = MutableLiveData<String>()

    val skipped = LiveDataUtil.skip(liveData, 0)
    liveData.value = "A"

    assertThat(LiveDataTestUtil.observeAndGetOneValue(skipped)).isEqualTo("A")
  }

  @Test
  fun skip_second_value_with_skip_one() {
    val liveData = MutableLiveData<String>()
    val testObserver = TestObserver<String>()

    val skipped = LiveDataUtil.skip(liveData, 1)

    skipped.observeForever(testObserver)
    liveData.value = "A"
    liveData.value = "B"
    skipped.removeObserver(testObserver)

    assertThat(testObserver.values).containsExactlyInAnyOrder("B")
  }

  @Test
  fun skip_no_value_with_skip() {
    val liveData = MutableLiveData<String>()

    val skipped = LiveDataUtil.skip(liveData, 1)
    liveData.value = "A"

    LiveDataTestUtil.assertNoValue(skipped)
  }

  @Test
  fun skip_third_and_fourth_value_with_skip_two() {
    val liveData = MutableLiveData<String>()
    val testObserver = TestObserver<String>()

    val skipped = LiveDataUtil.skip(liveData, 2)

    skipped.observeForever(testObserver)
    liveData.value = "A"
    liveData.value = "B"
    liveData.value = "C"
    liveData.value = "D"
    skipped.removeObserver(testObserver)

    assertThat(testObserver.values).containsExactlyInAnyOrder("C", "D")
  }

  @Test
  fun skip_set_one_before_then_skip() {
    val liveData = MutableLiveData<String>()
    val testObserver = TestObserver<String>()

    liveData.value = "A"

    val skipped = LiveDataUtil.skip(liveData, 2)

    skipped.observeForever(testObserver)
    liveData.value = "B"
    liveData.value = "C"
    liveData.value = "D"
    skipped.removeObserver(testObserver)

    assertThat(testObserver.values).containsExactlyInAnyOrder("C", "D")
  }

  @Test
  fun skip_set_two_before_then_skip() {
    val liveData = MutableLiveData<String>()
    val testObserver = TestObserver<String>()

    liveData.value = "A"
    liveData.value = "B"

    val skipped = LiveDataUtil.skip(liveData, 2)

    skipped.observeForever(testObserver)
    liveData.value = "C"
    liveData.value = "D"
    skipped.removeObserver(testObserver)

    assertThat(testObserver.values).containsExactlyInAnyOrder("D")
  }
}
