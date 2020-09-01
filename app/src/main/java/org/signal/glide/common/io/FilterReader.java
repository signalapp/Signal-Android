/*
 * Copyright 2019 Zhou Pengfei
 * SPDX-License-Identifier: Apache-2.0
 */

package org.signal.glide.common.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * @Description: FilterReader
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-23
 */
public class FilterReader implements Reader {
    protected Reader reader;

    public FilterReader(Reader in) {
        this.reader = in;
    }

    @Override
    public long skip(long total) throws IOException {
        return reader.skip(total);
    }

    @Override
    public byte peek() throws IOException {
        return reader.peek();
    }

    @Override
    public void reset() throws IOException {
        reader.reset();
    }

    @Override
    public int position() {
        return reader.position();
    }

    @Override
    public int read(byte[] buffer, int start, int byteCount) throws IOException {
        return reader.read(buffer, start, byteCount);
    }

    @Override
    public int available() throws IOException {
        return reader.available();
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    @Override
    public InputStream toInputStream() throws IOException {
        reset();
        return reader.toInputStream();
    }
}
