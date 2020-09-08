/*
 * Copyright 2019 Zhou Pengfei
 * SPDX-License-Identifier: Apache-2.0
 */

package org.signal.glide.apng.decode;

import android.content.Context;

import org.signal.glide.apng.io.APNGReader;
import org.signal.glide.common.io.Reader;
import org.signal.glide.common.io.StreamReader;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @link {https://www.w3.org/TR/PNG/#5PNG-file-signature}
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-13
 */
public class APNGParser {
    static class FormatException extends IOException {
        FormatException() {
            super("APNG Format error");
        }
    }

    public static boolean isAPNG(String filePath) {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(filePath);
            return isAPNG(new StreamReader(inputStream));
        } catch (Exception e) {
            return false;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static boolean isAPNG(Context context, String assetPath) {
        InputStream inputStream = null;
        try {
            inputStream = context.getAssets().open(assetPath);
            return isAPNG(new StreamReader(inputStream));
        } catch (Exception e) {
            return false;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static boolean isAPNG(Context context, int resId) {
        InputStream inputStream = null;
        try {
            inputStream = context.getResources().openRawResource(resId);
            return isAPNG(new StreamReader(inputStream));
        } catch (Exception e) {
            return false;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static boolean isAPNG(Reader in) {
        APNGReader reader = (in instanceof APNGReader) ? (APNGReader) in : new APNGReader(in);
        try {
            if (!reader.matchFourCC("\u0089PNG") || !reader.matchFourCC("\r\n\u001a\n")) {
                throw new FormatException();
            }
            while (reader.available() > 0) {
                Chunk chunk = parseChunk(reader);
                if (chunk instanceof ACTLChunk) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    public static List<Chunk> parse(APNGReader reader) throws IOException {
        if (!reader.matchFourCC("\u0089PNG") || !reader.matchFourCC("\r\n\u001a\n")) {
            throw new FormatException();
        }

        List<Chunk> chunks = new ArrayList<>();
        while (reader.available() > 0) {
            chunks.add(parseChunk(reader));
        }
        return chunks;
    }

    private static Chunk parseChunk(APNGReader reader) throws IOException {
        int offset = reader.position();
        int size = reader.readInt();
        int fourCC = reader.readFourCC();
        Chunk chunk;
        if (fourCC == ACTLChunk.ID) {
            chunk = new ACTLChunk();
        } else if (fourCC == FCTLChunk.ID) {
            chunk = new FCTLChunk();
        } else if (fourCC == FDATChunk.ID) {
            chunk = new FDATChunk();
        } else if (fourCC == IDATChunk.ID) {
            chunk = new IDATChunk();
        } else if (fourCC == IENDChunk.ID) {
            chunk = new IENDChunk();
        } else if (fourCC == IHDRChunk.ID) {
            chunk = new IHDRChunk();
        } else {
            chunk = new Chunk();
        }
        chunk.offset = offset;
        chunk.fourcc = fourCC;
        chunk.length = size;
        chunk.parse(reader);
        chunk.crc = reader.readInt();
        return chunk;
    }
}
