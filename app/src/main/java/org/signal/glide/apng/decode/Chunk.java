/*
 * Copyright 2019 Zhou Pengfei
 * SPDX-License-Identifier: Apache-2.0
 */

package org.signal.glide.apng.decode;

import android.text.TextUtils;

import org.signal.glide.apng.io.APNGReader;

import java.io.IOException;

/**
 * @Description: Length (长度)	4字节	指定数据块中数据域的长度，其长度不超过(231－1)字节
 * Chunk Type Code (数据块类型码)	4字节	数据块类型码由ASCII字母(A-Z和a-z)组成
 * Chunk Data (数据块数据)	可变长度	存储按照Chunk Type Code指定的数据
 * CRC (循环冗余检测)	4字节	存储用来检测是否有错误的循环冗余码
 * @Link https://www.w3.org/TR/PNG
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
class Chunk {
    int length;
    int fourcc;
    int crc;
    int offset;

    static int fourCCToInt(String fourCC) {
        if (TextUtils.isEmpty(fourCC) || fourCC.length() != 4) {
            return 0xbadeffff;
        }
        return (fourCC.charAt(0) & 0xff)
                | (fourCC.charAt(1) & 0xff) << 8
                | (fourCC.charAt(2) & 0xff) << 16
                | (fourCC.charAt(3) & 0xff) << 24
                ;
    }

    void parse(APNGReader reader) throws IOException {
        int available = reader.available();
        innerParse(reader);
        int offset = available - reader.available();
        if (offset > length) {
            throw new IOException("Out of chunk area");
        } else if (offset < length) {
            reader.skip(length - offset);
        }
    }

    void innerParse(APNGReader reader) throws IOException {
    }
}
