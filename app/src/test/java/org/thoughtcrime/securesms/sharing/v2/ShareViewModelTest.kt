/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.sharing.v2

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.reactivex.rxjava3.core.Single
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Verifies that [ShareViewModel] maps share resolution failures to the correct [ShareError]
 * in the failed state, and that the share error is only surfaced once.
 */
class ShareViewModelTest {

  private val repository = mockk<ShareRepository>()

  @Test
  fun givenAccessDeniedFailureFromRepository_whenResolve_thenStateIsFailedWithAccessDenied() {
    every { repository.resolve(any()) } returns Single.just<ResolvedShareData>(ResolvedShareData.Failure(ShareError.ACCESS_DENIED))

    val viewModel = viewModel()

    assertThat(awaitFailedState(viewModel)).isEqualTo(ShareState.ShareDataLoadState.Failed(ShareError.ACCESS_DENIED))
  }

  @Test
  fun givenSecurityExceptionFromRepository_whenResolve_thenStateIsFailedWithAccessDenied() {
    every { repository.resolve(any()) } returns Single.error<ResolvedShareData>(SecurityException("No URI grant"))

    val viewModel = viewModel()

    assertThat(awaitFailedState(viewModel)).isEqualTo(ShareState.ShareDataLoadState.Failed(ShareError.ACCESS_DENIED))
  }

  @Test
  fun givenIOExceptionFromRepository_whenResolve_thenStateIsFailedWithUnknown() {
    every { repository.resolve(any()) } returns Single.error<ResolvedShareData>(IOException("Failed to open"))

    val viewModel = viewModel()

    assertThat(awaitFailedState(viewModel)).isEqualTo(ShareState.ShareDataLoadState.Failed(ShareError.UNKNOWN))
  }

  @Test
  fun givenFailedState_whenShareErrorShown_thenRemainsFailedAndOnlyShownOnce() {
    every { repository.resolve(any()) } returns Single.just<ResolvedShareData>(ResolvedShareData.Failure(ShareError.ACCESS_DENIED))

    val viewModel = viewModel()
    awaitFailedState(viewModel)

    assertThat(viewModel.hasShareErrorBeenShown()).isFalse()
    viewModel.markShareErrorShown()
    assertThat(viewModel.hasShareErrorBeenShown()).isTrue()
  }

  private fun viewModel(): ShareViewModel {
    return ShareViewModel(UnresolvedShareData.ExternalPrimitiveShare("hello"), repository)
  }

  private fun awaitFailedState(viewModel: ShareViewModel): ShareState.ShareDataLoadState {
    return viewModel.state
      .filter { it.loadState is ShareState.ShareDataLoadState.Failed }
      .timeout(5, TimeUnit.SECONDS)
      .blockingFirst()
      .loadState
  }
}
