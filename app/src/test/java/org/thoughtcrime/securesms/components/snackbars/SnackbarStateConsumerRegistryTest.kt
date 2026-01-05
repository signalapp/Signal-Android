package org.thoughtcrime.securesms.components.snackbars

import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.signal.core.ui.compose.Snackbars

class SnackbarStateConsumerRegistryTest {

  private lateinit var registry: SnackbarStateConsumerRegistry

  private object TestHostKey1 : SnackbarHostKey
  private object TestHostKey2 : SnackbarHostKey
  private object TestHostKey3 : SnackbarHostKey

  @Before
  fun setUp() {
    registry = SnackbarStateConsumerRegistry()
  }

  @Test
  fun `register returns closeable that unregisters consumer`() {
    val consumer: Consumer<SnackbarState> = mockk(relaxed = true)
    val closeable = registry.register(TestHostKey1, consumer)

    val snackbar = createSnackbarState(TestHostKey1)
    registry.emit(snackbar)
    verify(exactly = 1) { consumer.accept(any()) }

    closeable.close()

    registry.emit(snackbar)
    verify(exactly = 1) { consumer.accept(any()) }
  }

  @Test
  fun `register replaces existing registration for same host`() {
    val consumer1: Consumer<SnackbarState> = mockk(relaxed = true)
    val consumer2: Consumer<SnackbarState> = mockk(relaxed = true)

    registry.register(TestHostKey1, consumer1)
    registry.register(TestHostKey1, consumer2)

    val snackbar = createSnackbarState(TestHostKey1)
    registry.emit(snackbar)

    verify(exactly = 0) { consumer1.accept(any()) }
    verify(exactly = 1) { consumer2.accept(snackbar) }
  }

  @Test(expected = IllegalStateException::class)
  fun `register with lifecycle throws when lifecycle not created`() {
    val consumer: Consumer<SnackbarState> = mockk(relaxed = true)
    val lifecycleOwner: LifecycleOwner = mockk()
    val lifecycle: Lifecycle = mockk()

    every { lifecycleOwner.lifecycle } returns lifecycle
    every { lifecycle.currentState } returns Lifecycle.State.DESTROYED

    registry.register(TestHostKey1, lifecycleOwner, consumer)
  }

  @Test
  fun `register with lifecycle starts disabled when not resumed`() {
    val consumer: Consumer<SnackbarState> = mockk(relaxed = true)
    val (lifecycleOwner, _) = createLifecycleOwner(Lifecycle.State.CREATED)

    registry.register(TestHostKey1, lifecycleOwner, consumer)

    val snackbar = createSnackbarState(TestHostKey1)
    registry.emit(snackbar)

    verify(exactly = 0) { consumer.accept(any()) }
  }

  @Test
  fun `register with lifecycle starts enabled when resumed`() {
    val consumer: Consumer<SnackbarState> = mockk(relaxed = true)
    val (lifecycleOwner, _) = createLifecycleOwner(Lifecycle.State.RESUMED)

    registry.register(TestHostKey1, lifecycleOwner, consumer)

    val snackbar = createSnackbarState(TestHostKey1)
    registry.emit(snackbar)

    verify(exactly = 1) { consumer.accept(snackbar) }
  }

  @Test
  fun `lifecycle observer enables consumer on resume`() {
    val consumer: Consumer<SnackbarState> = mockk(relaxed = true)
    val (lifecycleOwner, lifecycleRegistry) = createLifecycleOwner(Lifecycle.State.STARTED)

    registry.register(TestHostKey1, lifecycleOwner, consumer)

    val snackbar1 = createSnackbarState(TestHostKey1)
    registry.emit(snackbar1)
    verify(exactly = 0) { consumer.accept(any()) }

    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

    val snackbar2 = createSnackbarState(TestHostKey1)
    registry.emit(snackbar2)
    verify(exactly = 1) { consumer.accept(snackbar2) }
  }

  @Test
  fun `lifecycle observer disables consumer on pause`() {
    val consumer: Consumer<SnackbarState> = mockk(relaxed = true)
    val (lifecycleOwner, lifecycleRegistry) = createLifecycleOwner(Lifecycle.State.RESUMED)

    registry.register(TestHostKey1, lifecycleOwner, consumer)

    val snackbar = createSnackbarState(TestHostKey1)
    registry.emit(snackbar)
    verify(exactly = 1) { consumer.accept(any()) }

    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)

    registry.emit(snackbar)
    verify(exactly = 1) { consumer.accept(any()) }
  }

  @Test
  fun `lifecycle observer unregisters consumer on destroy`() {
    val consumer: Consumer<SnackbarState> = mockk(relaxed = true)
    val newConsumer: Consumer<SnackbarState> = mockk(relaxed = true)
    val (lifecycleOwner, lifecycleRegistry) = createLifecycleOwner(Lifecycle.State.RESUMED)

    registry.register(TestHostKey1, lifecycleOwner, consumer)

    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

    registry.register(TestHostKey1, newConsumer)
    val snackbar = createSnackbarState(TestHostKey1)
    registry.emit(snackbar)

    verify(exactly = 0) { consumer.accept(any()) }
    verify(exactly = 1) { newConsumer.accept(snackbar) }
  }

  @Test
  fun `emit routes to matching host`() {
    val consumer1: Consumer<SnackbarState> = mockk(relaxed = true)
    val consumer2: Consumer<SnackbarState> = mockk(relaxed = true)

    registry.register(TestHostKey1, consumer1)
    registry.register(TestHostKey2, consumer2)

    val snackbar = createSnackbarState(TestHostKey2)
    registry.emit(snackbar)

    verify(exactly = 0) { consumer1.accept(any()) }
    verify(exactly = 1) { consumer2.accept(snackbar) }
  }

  @Test
  fun `emit routes to fallback when no matching host`() {
    val consumer1: Consumer<SnackbarState> = mockk(relaxed = true)
    val consumer2: Consumer<SnackbarState> = mockk(relaxed = true)

    registry.register(TestHostKey1, consumer1)
    registry.register(TestHostKey2, consumer2)

    val snackbar = createSnackbarState(hostKey = TestHostKey3, fallbackKey = TestHostKey1)
    registry.emit(snackbar)

    verify(exactly = 1) { consumer1.accept(snackbar) }
    verify(exactly = 0) { consumer2.accept(any()) }
  }

  @Test
  fun `emit routes to first enabled when no match and no fallback match`() {
    val consumer1: Consumer<SnackbarState> = mockk(relaxed = true)
    val consumer2: Consumer<SnackbarState> = mockk(relaxed = true)

    registry.register(TestHostKey1, consumer1)
    registry.register(TestHostKey2, consumer2)

    val snackbar = createSnackbarState(hostKey = TestHostKey3, fallbackKey = null)
    registry.emit(snackbar)

    verify(exactly = 1) { consumer1.accept(snackbar) }
    verify(exactly = 0) { consumer2.accept(any()) }
  }

  @Test
  fun `emit does nothing when no enabled consumers`() {
    val consumer: Consumer<SnackbarState> = mockk(relaxed = true)
    val (lifecycleOwner, _) = createLifecycleOwner(Lifecycle.State.CREATED)

    registry.register(TestHostKey1, lifecycleOwner, consumer)

    val snackbar = createSnackbarState(TestHostKey1)
    registry.emit(snackbar)

    verify(exactly = 0) { consumer.accept(any()) }
  }

  @Test
  fun `emit skips disabled consumers and finds enabled fallback`() {
    val consumer1: Consumer<SnackbarState> = mockk(relaxed = true)
    val consumer2: Consumer<SnackbarState> = mockk(relaxed = true)

    val (lifecycleOwner1, _) = createLifecycleOwner(Lifecycle.State.CREATED)
    registry.register(TestHostKey1, lifecycleOwner1, consumer1)
    registry.register(TestHostKey2, consumer2)

    val snackbar = createSnackbarState(hostKey = TestHostKey1, fallbackKey = TestHostKey2)
    registry.emit(snackbar)

    verify(exactly = 0) { consumer1.accept(any()) }
    verify(exactly = 1) { consumer2.accept(snackbar) }
  }

  @Test
  fun `emit skips disabled matching and fallback, finds first enabled`() {
    val consumer1: Consumer<SnackbarState> = mockk(relaxed = true)
    val consumer2: Consumer<SnackbarState> = mockk(relaxed = true)
    val consumer3: Consumer<SnackbarState> = mockk(relaxed = true)

    val (lifecycleOwner1, _) = createLifecycleOwner(Lifecycle.State.CREATED)
    val (lifecycleOwner2, _) = createLifecycleOwner(Lifecycle.State.CREATED)

    registry.register(TestHostKey1, lifecycleOwner1, consumer1)
    registry.register(TestHostKey2, lifecycleOwner2, consumer2)
    registry.register(TestHostKey3, consumer3)

    val snackbar = createSnackbarState(hostKey = TestHostKey1, fallbackKey = TestHostKey2)
    registry.emit(snackbar)

    verify(exactly = 0) { consumer1.accept(any()) }
    verify(exactly = 0) { consumer2.accept(any()) }
    verify(exactly = 1) { consumer3.accept(snackbar) }
  }

  @Test
  fun `emit does nothing when no consumers registered`() {
    val snackbar = createSnackbarState(TestHostKey1)
    registry.emit(snackbar)
  }

  private fun createSnackbarState(
    hostKey: SnackbarHostKey,
    fallbackKey: SnackbarHostKey? = SnackbarHostKey.Global
  ): SnackbarState {
    return SnackbarState(
      message = "Test message",
      hostKey = hostKey,
      fallbackKey = fallbackKey,
      duration = Snackbars.Duration.SHORT
    )
  }

  private fun createLifecycleOwner(initialState: Lifecycle.State): Pair<LifecycleOwner, LifecycleRegistry> {
    val lifecycleOwner: LifecycleOwner = mockk()
    val lifecycleRegistry = LifecycleRegistry.createUnsafe(lifecycleOwner)
    lifecycleRegistry.currentState = initialState
    every { lifecycleOwner.lifecycle } returns lifecycleRegistry
    return Pair(lifecycleOwner, lifecycleRegistry)
  }
}
