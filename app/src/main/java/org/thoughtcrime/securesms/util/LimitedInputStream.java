package org.thoughtcrime.securesms.util;


/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;


/**
 * An input stream, which limits its data size. This stream is
 * used, if the content length is unknown.
 */
public class LimitedInputStream extends FilterInputStream {

  /**
   * The maximum size of an item, in bytes.
   */
  private long sizeMax;

  /**
   * The current number of bytes.
   */
  private long count;

  /**
   * Whether this stream is already closed.
   */
  private boolean closed;

  /**
   * Creates a new instance.
   * @param pIn The input stream, which shall be limited.
   * @param pSizeMax The limit; no more than this number of bytes
   *   shall be returned by the source stream.
   */
  public LimitedInputStream(InputStream pIn, long pSizeMax) {
    super(pIn);
    sizeMax = pSizeMax;
  }

  /**
   * Reads the next byte of data from this input stream. The value
   * byte is returned as an <code>int</code> in the range
   * <code>0</code> to <code>255</code>. If no byte is available
   * because the end of the stream has been reached, the value
   * <code>-1</code> is returned. This method blocks until input data
   * is available, the end of the stream is detected, or an exception
   * is thrown.
   *
   * This method
   * simply performs <code>in.read()</code> and returns the result.
   *
   * @return     the next byte of data, or <code>-1</code> if the end of the
   *             stream is reached.
   * @exception  IOException  if an I/O error occurs.
   * @see        java.io.FilterInputStream#in
   */
  public int read() throws IOException {
    if (count >= sizeMax) return -1;

    int res = super.read();
    if (res != -1) {
      count++;
    }
    return res;
  }

  /**
   * Reads up to <code>len</code> bytes of data from this input stream
   * into an array of bytes. If <code>len</code> is not zero, the method
   * blocks until some input is available; otherwise, no
   * bytes are read and <code>0</code> is returned.
   *
   * This method simply performs <code>in.read(b, off, len)</code>
   * and returns the result.
   *
   * @param      b     the buffer into which the data is read.
   * @param      off   The start offset in the destination array
   *                   <code>b</code>.
   * @param      len   the maximum number of bytes read.
   * @return     the total number of bytes read into the buffer, or
   *             <code>-1</code> if there is no more data because the end of
   *             the stream has been reached.
   * @exception  NullPointerException If <code>b</code> is <code>null</code>.
   * @exception  IndexOutOfBoundsException If <code>off</code> is negative,
   * <code>len</code> is negative, or <code>len</code> is greater than
   * <code>b.length - off</code>
   * @exception  IOException  if an I/O error occurs.
   * @see        java.io.FilterInputStream#in
   */
  public int read(byte[] b, int off, int len) throws IOException {
    if (count >= sizeMax) return -1;

    long correctLength = Math.min(len, sizeMax - count);

    int res = super.read(b, off, Util.toIntExact(correctLength));
    if (res > 0) {
      count += res;
    }
    return res;
  }

}