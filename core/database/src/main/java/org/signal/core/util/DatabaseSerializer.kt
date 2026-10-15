/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.content.ContentValues
import android.database.Cursor

/**
 * Serializer specifically for working with SQLite
 */
interface DatabaseSerializer<Data> : BaseSerializer<Data, Cursor, ContentValues>
