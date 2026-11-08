/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.database

import android.content.ContentValues
import android.database.Cursor
import org.signal.core.util.BaseSerializer

/**
 * Serializer specifically for working with SQLite
 */
interface DatabaseSerializer<Data> : BaseSerializer<Data, Cursor, ContentValues>
