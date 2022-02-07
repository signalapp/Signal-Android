package org.thoughtcrime.securesms

import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Rule

open class BaseCoroutineTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    protected fun runBlockingTest(test: suspend TestCoroutineScope.() -> Unit) =
        coroutinesTestRule.runBlockingTest { test() }

}