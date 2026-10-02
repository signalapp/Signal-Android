/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.about

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.reactivex.rxjava3.core.Single
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.testutil.RxPluginsRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AboutSheetViewModelTest {

  @get:Rule
  val recipients = RecipientTestRule()

  @get:Rule
  val rxRule = RxPluginsRule()

  @Test
  fun `a change to the recipient while the sheet is open reaches its state`() {
    val id = recipients.createRecipient("Nora Oldname")

    val repository = mockk<AboutSheetRepository> {
      every { getGroupsInCommonCount(id) } returns Single.just(0)
      every { getVerified(id) } returns Single.just(false)
    }

    val viewModel = AboutSheetViewModel(id, repository = repository)
    rxRule.defaultScheduler.triggerActions()
    assertEquals(ProfileName.fromParts("Nora", "Oldname"), viewModel.state.value.recipient!!.profileName)

    recipients.setProfileName(id, ProfileName.fromParts("Nora", "Newname"))
    rxRule.defaultScheduler.triggerActions()

    assertEquals(ProfileName.fromParts("Nora", "Newname"), viewModel.state.value.recipient!!.profileName)
  }
}
