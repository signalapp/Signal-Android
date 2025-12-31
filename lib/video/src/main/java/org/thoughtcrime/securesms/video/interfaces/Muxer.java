/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.interfaces;

import android.media.MediaCodec;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Muxer {

    void start() throws IOException;

    long stop() throws IOException;

    int addTrack(@NonNull MediaFormat format) throws IOException;

    void writeSampleData(int trackIndex, @NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) throws IOException;

    void release();

    boolean supportsAudioRemux();
}
