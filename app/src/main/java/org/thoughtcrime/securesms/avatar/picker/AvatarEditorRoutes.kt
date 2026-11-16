/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.picker

import org.signal.appsettings.routes.AppSettingsRoute
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.Avatars

/** The route that edits [text], or makes a new text avatar in a random color when it's null. */
fun textAvatarEditorRoute(text: Avatar.Text?): AppSettingsRoute.AccountRoute.TextAvatarEditor {
  return if (text == null) {
    AppSettingsRoute.AccountRoute.TextAvatarEditor(text = "", colorCode = Avatars.colors.random().code)
  } else {
    AppSettingsRoute.AccountRoute.TextAvatarEditor(text = text.text, colorCode = text.color.code, databaseId = text.databaseId.toRouteId())
  }
}

fun vectorAvatarEditorRoute(vector: Avatar.Vector): AppSettingsRoute.AccountRoute.VectorAvatarEditor {
  return AppSettingsRoute.AccountRoute.VectorAvatarEditor(key = vector.key, colorCode = vector.color.code, databaseId = vector.databaseId.toRouteId())
}

fun AppSettingsRoute.AccountRoute.TextAvatarEditor.toAvatar(): Avatar.Text {
  return Avatar.Text(text = text, color = Avatars.colorMap.getValue(colorCode), databaseId = databaseId.toDatabaseId())
}

fun AppSettingsRoute.AccountRoute.VectorAvatarEditor.toAvatar(): Avatar.Vector {
  return Avatar.Vector(key = key, color = Avatars.colorMap.getValue(colorCode), databaseId = databaseId.toDatabaseId())
}

private fun Avatar.DatabaseId.toRouteId(): Long = (this as? Avatar.DatabaseId.Saved)?.id ?: -1L

private fun Long.toDatabaseId(): Avatar.DatabaseId = if (this == -1L) Avatar.DatabaseId.NotSet else Avatar.DatabaseId.Saved(this)
