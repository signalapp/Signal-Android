/*
 * Copyright 2019 Zhou Pengfei
 * SPDX-License-Identifier: Apache-2.0
 */

package org.signal.glide.apng.decode;

import org.signal.glide.apng.io.APNGReader;

import java.io.IOException;

/**
 * The IHDR chunk shall be the first chunk in the PNG datastream. It contains:
 * <p>
 * Width	4 bytes
 * Height	4 bytes
 * Bit depth	1 byte
 * Colour type	1 byte
 * Compression method	1 byte
 * Filter method	1 byte
 * Interlace method	1 byte
 *
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
class IHDRChunk extends Chunk {
    static final int ID = fourCCToInt("IHDR");
    /**
     * 图像宽度，以像素为单位
     */
    int width;
    /**
     * 图像高度，以像素为单位
     */
    int height;

    byte[] data = new byte[5];

    @Override
    void innerParse(APNGReader reader) throws IOException {
        width = reader.readInt();
        height = reader.readInt();
        reader.read(data, 0, data.length);
    }
}
