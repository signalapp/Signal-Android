/*
 * Copyright 2008-2019 JCodecProject
 *
 * Redistribution  and  use  in   source  and   binary   forms,  with  or  without
 * modification, are permitted provided  that the following  conditions  are  met:
 *
 * Redistributions of  source code  must  retain the above  copyright notice, this
 * list of conditions and the following disclaimer. Redistributions in binary form
 * must  reproduce  the above  copyright notice, this  list of conditions  and the
 * following disclaimer in the documentation and/or other  materials provided with
 * the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,  BUT NOT LIMITED TO, THE  IMPLIED
 * WARRANTIES  OF  MERCHANTABILITY  AND  FITNESS  FOR  A  PARTICULAR  PURPOSE  ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,  OR CONSEQUENTIAL DAMAGES
 * (INCLUDING,  BUT NOT LIMITED TO,  PROCUREMENT OF SUBSTITUTE GOODS  OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS;  OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY  THEORY  OF  LIABILITY,  WHETHER  IN  CONTRACT,  STRICT LIABILITY,  OR TORT
 * (INCLUDING  NEGLIGENCE OR OTHERWISE)  ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * https://github.com/jcodec/jcodec/blob/master/src/main/java/org/jcodec/codecs/h264/H264Utils.java
 *
 * This file has been modified by Signal.
 */
package org.thoughtcrime.securesms.video.videoconverter.muxer;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

final class H264Utils {

  private H264Utils() {}

  static @NonNull List<ByteBuffer> getNals(ByteBuffer buffer) {
    final List<ByteBuffer> nals = new ArrayList<>();
    ByteBuffer             nal;
    while ((nal = nextNALUnit(buffer)) != null) {
      nals.add(nal);
    }
    return nals;
  }

  static ByteBuffer nextNALUnit(ByteBuffer buf) {
    skipToNALUnit(buf);
    return gotoNALUnit(buf);
  }

  static void skipToNALUnit(ByteBuffer buf) {
    if (!buf.hasRemaining())
      return;

    int val = 0xffffffff;
    while (buf.hasRemaining()) {
      val <<= 8;
      val |= (buf.get() & 0xff);
      if ((val & 0xffffff) == 1) {
        buf.position(buf.position());
        break;
      }
    }
  }

  /**
   * Finds next Nth H.264 bitstream NAL unit (0x00000001) and returns the data
   * that preceeds it as a ByteBuffer slice
   * <p>
   * Segment byte order is always little endian
   * <p>
   * TODO: emulation prevention
   */
  static ByteBuffer gotoNALUnit(ByteBuffer buf) {

    if (!buf.hasRemaining())
      return null;

    int        from   = buf.position();
    ByteBuffer result = buf.slice();
    result.order(ByteOrder.BIG_ENDIAN);

    int val = 0xffffffff;
    while (buf.hasRemaining()) {
      val <<= 8;
      val |= (buf.get() & 0xff);
      if ((val & 0xffffff) == 1) {
        buf.position(buf.position() - (val == 1 ? 4 : 3));
        result.limit(buf.position() - from);
        break;
      }
    }
    return result;
  }
}
