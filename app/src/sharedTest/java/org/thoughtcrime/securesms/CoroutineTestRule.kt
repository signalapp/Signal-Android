package org.thoughtcrime.securesms

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Sets the main coroutines dispatcher to a [TestCoroutineScope] for unit testing. A
 * [TestCoroutineScope] provides control over the execution of coroutines.
 *
 * Declare it as a JUnit Rule:
 *
 * ```
 * @get:Rule
 * var coroutineTestRule = CoroutineTestRule()
 * ```
 *
 * Use it directly as a [TestCoroutineScope]:
 *
 * ```
 * coroutineTestRule.pauseDispatcher()
 * ...
 * coroutineTestRule.resumeDispatcher()
 * ...
 * coroutineTestRule.runBlockingTest { }
 * ...
 *
 */

class CoroutineTestRule(
    val dispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()
) : TestWatcher(), TestCoroutineScope by TestCoroutineScope(dispatcher) {

    override fun starting(description: Description?) {
        super.starting(description)
        Dispatchers.setMain(dispatcher)

    }

    override fun finished(description: Description?) {
        super.finished(description)
        cleanupTestCoroutines()
        Dispatchers.resetMain()
    }

}