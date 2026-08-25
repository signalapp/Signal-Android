/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.image

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.imageeditor.core.model.EditorModel

/**
 * An editor state is cached per URI and so outlives the pager page drawing it. Removing media shifts the survivors
 * down a slot, which hands a state from one page to another, and the incoming page composes before the outgoing one
 * is disposed. [ImageEditorState.attach] and [ImageEditorState.detach] have to survive that overlap or the model is
 * left unable to redraw the image still on screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
// The editor hierarchy builds an inverse-fill Path for the crop mask, which the legacy graphics shadows cannot do.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageEditorStateAttachTest {

  private val model = EditorModel.create(0)
  private val state = ImageEditorState(model)

  @Test
  fun `Given an attached state, when the model invalidates, then the canvas revision advances`() {
    state.attach()

    assertThat(state.revisionsFrom { model.postEdit(false) }).isGreaterThan(0L)
  }

  @Test
  fun `Given a state handed to a new page, when the old page is disposed, then the model still invalidates`() {
    state.attach()

    state.attach()
    state.detach()

    assertThat(state.revisionsFrom { model.postEdit(false) }).isGreaterThan(0L)
  }

  @Test
  fun `Given an attached state, when its only page is disposed, then the model no longer invalidates`() {
    state.attach()

    state.detach()

    assertThat(state.revisionsFrom { model.postEdit(false) }).isEqualTo(0L)
  }

  @Test
  fun `Given more detaches than attaches, when the state is attached again, then the model invalidates`() {
    state.detach()
    state.detach()

    state.attach()

    assertThat(state.revisionsFrom { model.postEdit(false) }).isGreaterThan(0L)
  }

  private fun ImageEditorState.revisionsFrom(block: () -> Unit): Long {
    val before = revision
    block()
    return revision - before
  }
}
