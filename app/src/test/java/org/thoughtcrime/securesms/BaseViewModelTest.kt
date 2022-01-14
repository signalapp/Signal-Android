package org.thoughtcrime.securesms

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Rule

open class BaseViewModelTest: BaseCoroutineTest() {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

}