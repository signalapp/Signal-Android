/*
 * Copyright 2019 Zhou Pengfei
 * SPDX-License-Identifier: Apache-2.0
 */

package org.signal.glide.load.resource.apng.decode;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;

import org.signal.core.util.logging.Log;
import org.signal.glide.common.decode.Frame;
import org.signal.glide.common.decode.FrameSeqDecoder;
import org.signal.glide.common.io.Reader;
import org.signal.glide.common.loader.Loader;
import org.signal.glide.load.resource.apng.io.APNGReader;
import org.signal.glide.load.resource.apng.io.APNGWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: APNG4Android
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-13
 */
public class APNGDecoder extends FrameSeqDecoder<APNGReader, APNGWriter> {

    private static final String TAG = Log.tag(APNGDecoder.class);

    private APNGWriter apngWriter;
    private int mLoopCount;
    private final Paint paint = new Paint();

    public static final int  MAX_DIMENSION    = 4096;
    public static final long MAX_TOTAL_PIXELS = 64_000_000L;

    private class SnapShot {
        byte dispose_op;
        Rect dstRect = new Rect();
        ByteBuffer byteBuffer;
    }

    private SnapShot snapShot = new SnapShot();

    /**
     * @param loader         webp的reader
     * @param renderListener 渲染的回调
     */
    public APNGDecoder(Loader loader, FrameSeqDecoder.RenderListener renderListener) {
        super(loader, renderListener);
        paint.setAntiAlias(true);
    }

    @Override
    protected APNGWriter getWriter() {
        if (apngWriter == null) {
            apngWriter = new APNGWriter();
        }
        return apngWriter;
    }

    @Override
    protected APNGReader getReader(Reader reader) {
        return new APNGReader(reader);
    }

    @Override
    protected int getLoopCount() {
        return mLoopCount;
    }

    @Override
    protected void release() {
        snapShot.byteBuffer = null;
        apngWriter = null;
    }


    @Override
    protected Rect read(APNGReader reader) throws IOException {
        List<Chunk> chunks = APNGParser.parse(reader);
        List<Chunk> otherChunks = new ArrayList<>();

        boolean actl = false;
        APNGFrame lastFrame = null;
        byte[] ihdrData = new byte[0];
        int canvasWidth = 0, canvasHeight = 0;
        for (Chunk chunk : chunks) {
            if (chunk instanceof ACTLChunk) {
                mLoopCount = ((ACTLChunk) chunk).num_plays;
                actl = true;
            } else if (chunk instanceof FCTLChunk) {
                APNGFrame frame = new APNGFrame(reader, (FCTLChunk) chunk);
                frame.prefixChunks = otherChunks;
                frame.ihdrData = ihdrData;
                frames.add(frame);
                lastFrame = frame;
            } else if (chunk instanceof FDATChunk) {
                if (lastFrame != null) {
                    lastFrame.imageChunks.add(chunk);
                }
            } else if (chunk instanceof IDATChunk) {
                if (!actl) {
                    //如果为非APNG图片，则只解码PNG
                    Frame frame = new StillFrame(reader);
                    frame.frameWidth = canvasWidth;
                    frame.frameHeight = canvasHeight;
                    frames.add(frame);
                    mLoopCount = 1;
                    break;
                }
                if (lastFrame != null) {
                    lastFrame.imageChunks.add(chunk);
                }

            } else if (chunk instanceof IHDRChunk) {
                canvasWidth = ((IHDRChunk) chunk).width;
                canvasHeight = ((IHDRChunk) chunk).height;
                ihdrData = ((IHDRChunk) chunk).data;
            } else if (!(chunk instanceof IENDChunk)) {
                otherChunks.add(chunk);
            }
        }

        int capacity = getSafeAllocationSize(canvasWidth, canvasHeight, sampleSize);

        frameBuffer         = ByteBuffer.allocate(capacity);
        snapShot.byteBuffer = ByteBuffer.allocate(capacity);

        return new Rect(0, 0, canvasWidth, canvasHeight);
    }

    @Override
    protected void renderFrame(Frame frame) {
        if (frame == null || fullRect == null) {
            return;
        }
        try {
            Bitmap bitmap = obtainBitmap(fullRect.width() / sampleSize, fullRect.height() / sampleSize);
            Canvas canvas = cachedCanvas.get(bitmap);
            if (canvas == null) {
                canvas = new Canvas(bitmap);
                cachedCanvas.put(bitmap, canvas);
            }
            if (frame instanceof APNGFrame) {
                // 从缓存中恢复当前帧
                frameBuffer.rewind();
                bitmap.copyPixelsFromBuffer(frameBuffer);
                // 开始绘制前，处理快照中的设定
                if (this.frameIndex == 0) {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                } else {
                    canvas.save();
                    canvas.clipRect(snapShot.dstRect);
                    switch (snapShot.dispose_op) {
                        // 从快照中恢复上一帧之前的显示内容
                        case FCTLChunk.APNG_DISPOSE_OP_PREVIOUS:
                            snapShot.byteBuffer.rewind();
                            bitmap.copyPixelsFromBuffer(snapShot.byteBuffer);
                            break;
                        // 清空上一帧所画区域
                        case FCTLChunk.APNG_DISPOSE_OP_BACKGROUND:
                            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                            break;
                        // 什么都不做
                        case FCTLChunk.APNG_DISPOSE_OP_NON:
                        default:
                            break;
                    }
                    canvas.restore();
                }

                // 然后根据dispose设定传递到快照信息中
                if (((APNGFrame) frame).dispose_op == FCTLChunk.APNG_DISPOSE_OP_PREVIOUS) {
                    if (snapShot.dispose_op != FCTLChunk.APNG_DISPOSE_OP_PREVIOUS) {
                        snapShot.byteBuffer.rewind();
                        bitmap.copyPixelsToBuffer(snapShot.byteBuffer);
                    }
                }

                snapShot.dispose_op = ((APNGFrame) frame).dispose_op;
                canvas.save();
                if (((APNGFrame) frame).blend_op == FCTLChunk.APNG_BLEND_OP_SOURCE) {
                    canvas.clipRect(
                            frame.frameX / sampleSize,
                            frame.frameY / sampleSize,
                            (frame.frameX + frame.frameWidth) / sampleSize,
                            (frame.frameY + frame.frameHeight) / sampleSize);
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                }


                snapShot.dstRect.set(frame.frameX / sampleSize,
                        frame.frameY / sampleSize,
                        (frame.frameX + frame.frameWidth) / sampleSize,
                        (frame.frameY + frame.frameHeight) / sampleSize);
                canvas.restore();
            }
            //开始真正绘制当前帧的内容
            Bitmap inBitmap = obtainBitmap(frame.frameWidth, frame.frameHeight);
            recycleBitmap(frame.draw(canvas, paint, sampleSize, inBitmap, getWriter()));
            recycleBitmap(inBitmap);
            frameBuffer.rewind();
            bitmap.copyPixelsToBuffer(frameBuffer);
            recycleBitmap(bitmap);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to render!", t);
        }
    }

    public static int getSafeAllocationSize(int width, int height, int sampleSize) throws IOException {
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new IOException("APNG dimensions exceed safe limits: " + width + "x" + height);
        }

        int capacity;
        try {
            int ss         = Math.multiplyExact(sampleSize, sampleSize);
            int canvasSize = Math.multiplyExact(width, height);

            int pixelCount = canvasSize / ss + 1;
            if (pixelCount <= 0 || pixelCount > MAX_TOTAL_PIXELS) {
                throw new IOException("APNG pixel count exceeds safe limits: " + pixelCount);
            }

            capacity = Math.multiplyExact(pixelCount, 4);
        } catch (ArithmeticException e) {
            throw new IOException("Failed to multiply dimensions and sample size: " + width + "x" + height + " @ sample size " + sampleSize + " (overflow?)", e);
        }

        return capacity;
    }
}
