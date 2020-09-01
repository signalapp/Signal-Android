/*
 * Copyright 2019 Zhou Pengfei
 * SPDX-License-Identifier: Apache-2.0
 */

package org.signal.glide.common.decode;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import org.signal.glide.common.io.Reader;
import org.signal.glide.common.io.Writer;

/**
 * @Description: One frame in an animation
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-13
 */
public abstract class Frame<R extends Reader, W extends Writer> {
    protected final R reader;
    public int frameWidth;
    public int frameHeight;
    public int frameX;
    public int frameY;
    public int frameDuration;

    public Frame(R reader) {
        this.reader = reader;
    }

    public abstract Bitmap draw(Canvas canvas, Paint paint, int sampleSize, Bitmap reusedBitmap, W writer);
}
