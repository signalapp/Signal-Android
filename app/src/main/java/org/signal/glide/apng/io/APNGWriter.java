/*
 * Copyright 2019 Zhou Pengfei
 * SPDX-License-Identifier: Apache-2.0
 */

package org.signal.glide.apng.io;

import org.signal.glide.common.io.ByteBufferWriter;

import java.nio.ByteOrder;

/**
 * @Description: APNGWriter
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-13
 */
public class APNGWriter extends ByteBufferWriter {
    public APNGWriter() {
        super();
    }

    public void writeFourCC(int val) {
        putByte((byte) (val & 0xff));
        putByte((byte) ((val >> 8) & 0xff));
        putByte((byte) ((val >> 16) & 0xff));
        putByte((byte) ((val >> 24) & 0xff));
    }

    public void writeInt(int val) {
        putByte((byte) ((val >> 24) & 0xff));
        putByte((byte) ((val >> 16) & 0xff));
        putByte((byte) ((val >> 8) & 0xff));
        putByte((byte) (val & 0xff));
    }

    @Override
    public void reset(int size) {
        super.reset(size);
        this.byteBuffer.order(ByteOrder.BIG_ENDIAN);
    }
}
