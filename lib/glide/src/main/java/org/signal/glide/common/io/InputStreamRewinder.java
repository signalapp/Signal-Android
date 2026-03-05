/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * This file is adapted from the Glide image loading library.
 * Original work Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.signal.glide.common.io;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.data.DataRewinder;
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool;
import com.bumptech.glide.load.resource.bitmap.RecyclableBufferedInputStream;
import com.bumptech.glide.util.Synthetic;

import java.io.IOException;
import java.io.InputStream;

/**
 * Implementation for {@link InputStream}s that rewinds streams by wrapping them in a buffered stream. This is a copy of
 * {@link com.bumptech.glide.load.data.InputStreamRewinder that is modified to use GlideStreamConfig.markReadLimitBytes in place of a hardcoded MARK_READ_LIMIT.
 */
public final class InputStreamRewinder implements DataRewinder<InputStream> {
  private final RecyclableBufferedInputStream bufferedStream;

  @Synthetic
  public InputStreamRewinder(InputStream is, ArrayPool byteArrayPool) {
    // We don't check is.markSupported() here because RecyclableBufferedInputStream allows resetting
    // after exceeding GlideStreamConfig.markReadLimitBytes, which other InputStreams don't guarantee.
    bufferedStream = new RecyclableBufferedInputStream(is, byteArrayPool);
    bufferedStream.mark(GlideStreamConfig.getMarkReadLimitBytes());
  }

  @NonNull
  @Override
  public InputStream rewindAndGet() throws IOException {
    bufferedStream.reset();
    return bufferedStream;
  }

  @Override
  public void cleanup() {
    bufferedStream.release();
  }

  public void fixMarkLimits() {
    bufferedStream.fixMarkLimit();
  }

  /**
   * Factory for producing {@link InputStreamRewinder}s from {@link InputStream}s.
   */
  public static final class Factory implements DataRewinder.Factory<InputStream> {
    private final ArrayPool byteArrayPool;

    public Factory(ArrayPool byteArrayPool) {
      this.byteArrayPool = byteArrayPool;
    }

    @NonNull
    @Override
    public DataRewinder<InputStream> build(@NonNull InputStream data) {
      return new InputStreamRewinder(data, byteArrayPool);
    }

    @NonNull
    @Override
    public Class<InputStream> getDataClass() {
      return InputStream.class;
    }
  }
}
