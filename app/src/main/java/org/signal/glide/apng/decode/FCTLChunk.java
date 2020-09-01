/*
 * Copyright 2019 Zhou Pengfei
 * SPDX-License-Identifier: Apache-2.0
 */

package org.signal.glide.apng.decode;

import org.signal.glide.apng.io.APNGReader;

import java.io.IOException;

/**
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 * @see {link=https://developer.mozilla.org/en-US/docs/Mozilla/Tech/APNG#.27fcTL.27:_The_Frame_Control_Chunk}
 */
class FCTLChunk extends Chunk {
    static final int ID = fourCCToInt("fcTL");
    int sequence_number;
    /**
     * x_offset >= 0
     * y_offset >= 0
     * width > 0
     * height > 0
     * x_offset + width <= 'IHDR' width
     * y_offset + height <= 'IHDR' height
     */
    /**
     * Width of the following frame.
     */
    int width;
    /**
     * Height of the following frame.
     */
    int height;
    /**
     * X position at which to render the following frame.
     */
    int x_offset;
    /**
     * Y position at which to render the following frame.
     */
    int y_offset;

    /**
     * The delay_num and delay_den parameters together specify a fraction indicating the time to
     * display the current frame, in seconds. If the denominator is 0, it is to be treated as if it
     * were 100 (that is, delay_num then specifies 1/100ths of a second).
     * If the the value of the numerator is 0 the decoder should render the next frame as quickly as
     * possible, though viewers may impose a reasonable lower bound.
     * <p>
     * Frame timings should be independent of the time required for decoding and display of each frame,
     * so that animations will run at the same rate regardless of the performance of the decoder implementation.
     */

    /**
     * Frame delay fraction numerator.
     */
    short delay_num;

    /**
     * Frame delay fraction denominator.
     */
    short delay_den;

    /**
     * Type of frame area disposal to be done after rendering this frame.
     * dispose_op specifies how the output buffer should be changed at the end of the delay (before rendering the next frame).
     * If the first 'fcTL' chunk uses a dispose_op of APNG_DISPOSE_OP_PREVIOUS it should be treated as APNG_DISPOSE_OP_BACKGROUND.
     */
    byte dispose_op;

    /**
     * Type of frame area rendering for this frame.
     */
    byte blend_op;

    /**
     * No disposal is done on this frame before rendering the next; the contents of the output buffer are left as is.
     */
    static final int APNG_DISPOSE_OP_NON = 0;

    /**
     * The frame's region of the output buffer is to be cleared to fully transparent black before rendering the next frame.
     */
    static final int APNG_DISPOSE_OP_BACKGROUND = 1;

    /**
     * The frame's region of the output buffer is to be reverted to the previous contents before rendering the next frame.
     */
    static final int APNG_DISPOSE_OP_PREVIOUS = 2;

    /**
     * blend_op<code> specifies whether the frame is to be alpha blended into the current output buffer content,
     * or whether it should completely replace its region in the output buffer.
     */
    /**
     * All color components of the frame, including alpha, overwrite the current contents of the frame's output buffer region.
     */
    static final int APNG_BLEND_OP_SOURCE = 0;

    /**
     * The frame should be composited onto the output buffer based on its alpha,
     * using a simple OVER operation as described in the Alpha Channel Processing section of the Extensions
     * to the PNG Specification, Version 1.2.0. Note that the second variation of the sample code is applicable.
     */
    static final int APNG_BLEND_OP_OVER = 1;

    @Override
    void innerParse(APNGReader reader) throws IOException {
        sequence_number = reader.readInt();
        width = reader.readInt();
        height = reader.readInt();
        x_offset = reader.readInt();
        y_offset = reader.readInt();
        delay_num = reader.readShort();
        delay_den = reader.readShort();
        dispose_op = reader.peek();
        blend_op = reader.peek();
    }
}
