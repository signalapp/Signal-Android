/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.models.database;

import androidx.annotation.NonNull;

public interface DatabaseId {
  @NonNull String serialize();
}
