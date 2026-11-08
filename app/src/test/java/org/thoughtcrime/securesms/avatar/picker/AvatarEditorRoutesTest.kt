/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.picker

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isIn
import org.junit.Test
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.Avatars

class AvatarEditorRoutesTest {

  @Test
  fun `a saved text avatar survives the route`() {
    val text = Avatar.Text("AB", Avatars.colors[3], Avatar.DatabaseId.Saved(12))

    assertThat(textAvatarEditorRoute(text).toAvatar()).isEqualTo(text)
  }

  @Test
  fun `an unsaved text avatar comes back unsaved`() {
    val text = Avatar.Text("AB", Avatars.colors[3], Avatar.DatabaseId.NotSet)

    assertThat(textAvatarEditorRoute(text).toAvatar()).isEqualTo(text)
  }

  @Test
  fun `a new text avatar starts empty with one of the avatar colors`() {
    val avatar = textAvatarEditorRoute(null).toAvatar()

    assertThat(avatar.text).isEqualTo("")
    assertThat(avatar.color).isIn(*Avatars.colors.toTypedArray())
    assertThat(avatar.databaseId).isEqualTo(Avatar.DatabaseId.NotSet)
  }

  @Test
  fun `a saved vector avatar survives the route`() {
    val vector = Avatar.Vector("avatar_abstract_01", Avatars.colors[5], Avatar.DatabaseId.Saved(7))

    assertThat(vectorAvatarEditorRoute(vector).toAvatar()).isEqualTo(vector)
  }

  @Test
  fun `an unsaved vector avatar comes back unsaved`() {
    val vector = Avatar.Vector("avatar_abstract_01", Avatars.colors[5], Avatar.DatabaseId.NotSet)

    assertThat(vectorAvatarEditorRoute(vector).toAvatar()).isEqualTo(vector)
  }

  @Test
  fun `a new text avatar keeps the color its route picked`() {
    val route = textAvatarEditorRoute(null)

    assertThat(route.toAvatar().color).isEqualTo(route.toAvatar().color)
  }
}
