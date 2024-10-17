/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.androidx

import androidx.documentfile.provider.DocumentFile

/**
 * Information about a file within the storage. Useful because default [DocumentFile] implementations
 * re-query info on each access.
 */
data class DocumentFileInfo(val documentFile: DocumentFile, val name: String, val size: Long)
