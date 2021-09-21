package org.thoughtcrime.securesms.conversation

import android.app.Application
import androidx.lifecycle.LifecycleOwner
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ConversationUpdateTickTest {

  private val lifecycleOwner = mock(LifecycleOwner::class.java)
  private val listener = mock(ConversationUpdateTick.OnTickListener::class.java)
  private val testSubject = ConversationUpdateTick(listener)

  private val timeoutMillis = ConversationUpdateTick.TIMEOUT

  @Test
  fun `Given onResume not invoked, then I expect zero invocations of onTick`() {
    // THEN
    verify(listener, never()).onTick()
  }

  @Test
  fun `Given no time has passed after onResume is invoked, then I expect one invocations of onTick`() {
    // GIVEN
    ShadowLooper.pauseMainLooper()
    testSubject.onResume(lifecycleOwner)

    // THEN
    verify(listener, times(1)).onTick()
  }

  @Test
  fun `Given onResume is invoked, when half timeout passes, then I expect one invocations of onTick`() {
    // GIVEN
    testSubject.onResume(lifecycleOwner)
    ShadowLooper.idleMainLooper(timeoutMillis / 2, TimeUnit.MILLISECONDS)

    // THEN
    verify(listener, times(1)).onTick()
  }

  @Test
  fun `Given onResume is invoked, when timeout passes, then I expect two invocations of onTick`() {
    // GIVEN
    testSubject.onResume(lifecycleOwner)

    // WHEN
    ShadowLooper.idleMainLooper(timeoutMillis, TimeUnit.MILLISECONDS)

    // THEN
    verify(listener, times(2)).onTick()
  }

  @Test
  fun `Given onResume is invoked, when timeout passes five times, then I expect six invocations of onTick`() {
    // GIVEN
    testSubject.onResume(lifecycleOwner)

    // WHEN
    ShadowLooper.idleMainLooper(timeoutMillis * 5, TimeUnit.MILLISECONDS)

    // THEN
    verify(listener, times(6)).onTick()
  }

  @Test
  fun `Given onResume then onPause is invoked, when timeout passes, then I expect one invocation of onTick`() {
    // GIVEN
    testSubject.onResume(lifecycleOwner)
    testSubject.onPause(lifecycleOwner)

    // WHEN
    ShadowLooper.idleMainLooper(timeoutMillis, TimeUnit.MILLISECONDS)

    // THEN
    verify(listener, times(1)).onTick()
  }
}
