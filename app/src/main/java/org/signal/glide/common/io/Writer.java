/*
 * Copyright 2019 Zhou Pengfei
 * SPDX-License-Identifier: Apache-2.0
 */

package org.signal.glide.common.io;

import java.io.IOException;

/**
 * @Description: APNG4Android
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-12
 */
public interface Writer {
    void reset(int size);

    void putByte(byte b);

    void putBytes(byte[] b);

    int position();

    void skip(int length);

    byte[] toByteArray();

    void close() throws IOException;
}
