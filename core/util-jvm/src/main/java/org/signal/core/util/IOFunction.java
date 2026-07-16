/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util;

import java.io.IOException;

/**
 * A function which takes 1 input and returns 1 output, and is capable of throwing an IO Exception.
 */
public interface IOFunction<I, O> {
  O apply(I input) throws IOException;
}
