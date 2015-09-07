/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ws.com.google.android.mms.pdu;

import android.graphics.Bitmap;
import android.net.Uri;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.PartDatabase;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import ws.com.google.android.mms.ContentType;

/**
 * The pdu part.
 */
public class PduPart {
    /**
     * Well-Known Parameters.
     */
    public static final int P_Q                  = 0x80;
    public static final int P_CHARSET            = 0x81;
    public static final int P_LEVEL              = 0x82;
    public static final int P_TYPE               = 0x83;
    public static final int P_DEP_NAME           = 0x85;
    public static final int P_DEP_FILENAME       = 0x86;
    public static final int P_DIFFERENCES        = 0x87;
    public static final int P_PADDING            = 0x88;
    // This value of "TYPE" s used with Content-Type: multipart/related
    public static final int P_CT_MR_TYPE         = 0x89;
    public static final int P_DEP_START          = 0x8A;
    public static final int P_DEP_START_INFO     = 0x8B;
    public static final int P_DEP_COMMENT        = 0x8C;
    public static final int P_DEP_DOMAIN         = 0x8D;
    public static final int P_MAX_AGE            = 0x8E;
    public static final int P_DEP_PATH           = 0x8F;
    public static final int P_SECURE             = 0x90;
    public static final int P_SEC                = 0x91;
    public static final int P_MAC                = 0x92;
    public static final int P_CREATION_DATE      = 0x93;
    public static final int P_MODIFICATION_DATE  = 0x94;
    public static final int P_READ_DATE          = 0x95;
    public static final int P_SIZE               = 0x96;
    public static final int P_NAME               = 0x97;
    public static final int P_FILENAME           = 0x98;
    public static final int P_START              = 0x99;
    public static final int P_START_INFO         = 0x9A;
    public static final int P_COMMENT            = 0x9B;
    public static final int P_DOMAIN             = 0x9C;
    public static final int P_PATH               = 0x9D;

    /**
     *  Header field names.
     */
     public static final int P_CONTENT_TYPE       = 0x91;
     public static final int P_CONTENT_LOCATION   = 0x8E;
     public static final int P_CONTENT_ID         = 0xC0;
     public static final int P_DEP_CONTENT_DISPOSITION = 0xAE;
     public static final int P_CONTENT_DISPOSITION = 0xC5;
    // The next header is unassigned header, use reserved header(0x48) value.
     public static final int P_CONTENT_TRANSFER_ENCODING = 0xC8;

     /**
      * Content=Transfer-Encoding string.
      */
     public static final String CONTENT_TRANSFER_ENCODING =
             "Content-Transfer-Encoding";

     /**
      * Value of Content-Transfer-Encoding.
      */
     public static final String P_BINARY = "binary";
     public static final String P_7BIT = "7bit";
     public static final String P_8BIT = "8bit";
     public static final String P_BASE64 = "base64";
     public static final String P_QUOTED_PRINTABLE = "quoted-printable";

     /**
      * Value of disposition can be set to PduPart when the value is octet in
      * the PDU.
      * "from-data" instead of Form-data<Octet 128>.
      * "attachment" instead of Attachment<Octet 129>.
      * "inline" instead of Inline<Octet 130>.
      */
     static final byte[] DISPOSITION_FROM_DATA = "from-data".getBytes();
     static final byte[] DISPOSITION_ATTACHMENT = "attachment".getBytes();
     static final byte[] DISPOSITION_INLINE = "inline".getBytes();

     /**
      * Content-Disposition value.
      */
     public static final int P_DISPOSITION_FROM_DATA  = 0x80;
     public static final int P_DISPOSITION_ATTACHMENT = 0x81;
     public static final int P_DISPOSITION_INLINE     = 0x82;

     /**
      * Header of part.
      */
     private Map<Integer, Object> mPartHeader = null;

     /**
      * Data uri.
      */
     private Uri mUri = null;

     /**
      * Part data.
      */
     private byte[] mPartData = null;

     private static final String TAG = "PduPart";

     private long    rowId = -1;
     private long    uniqueId = -1;
     private long    mmsId = -1;
     private boolean isEncrypted;
     private int     transferProgress;
     private long    dataSize;
     private Bitmap  thumbnail;

     /**
      * Empty Constructor.
      */
     public PduPart() {
         mPartHeader = new HashMap<Integer, Object>();
         setUniqueId(System.currentTimeMillis());
     }

     public void setEncrypted(boolean isEncrypted) {
    	 this.isEncrypted = isEncrypted;
     }

     public boolean getEncrypted() {
    	 return isEncrypted;
     }

     public void setDataSize(long dataSize) {
       this.dataSize = dataSize;
     }

     public long getDataSize() {
       return this.dataSize;
     }

     public boolean isInProgress() {
       return transferProgress != PartDatabase.TRANSFER_PROGRESS_DONE &&
              transferProgress != PartDatabase.TRANSFER_PROGRESS_FAILED;
     }

     public void setTransferProgress(int transferProgress) {
       this.transferProgress = transferProgress;
     }

     public int getTransferProgress() {
       return transferProgress;
     }

     /**
      * Set part data. The data are stored as byte array.
      *
      * @param data the data
      */
     public void setData(byte[] data) {
         if(data == null) {
            return;
        }

         mPartData = new byte[data.length];
         System.arraycopy(data, 0, mPartData, 0, data.length);
     }

     /**
      * @return A copy of the part data or null if the data wasn't set or
      *         the data is stored as Uri.
      * @see #getDataUri
      */
     public byte[] getData() {
         if(mPartData == null) {
            return null;
         }

         byte[] byteArray = new byte[mPartData.length];
         System.arraycopy(mPartData, 0, byteArray, 0, mPartData.length);
         return byteArray;
     }

     /**
      * Set data uri. The data are stored as Uri.
      *
      * @param uri the uri
      */
     public void setDataUri(Uri uri) {
         mUri = uri;
     }

     /**
      * @return The Uri of the part data or null if the data wasn't set or
      *         the data is stored as byte array.
      * @see #getData
      */
     public Uri getDataUri() {
         return mUri;
     }

     /**
      * Set Content-id value
      *
      * @param contentId the content-id value
      * @throws NullPointerException if the value is null.
      */
     public void setContentId(byte[] contentId) {
         if((contentId == null) || (contentId.length == 0)) {
             throw new IllegalArgumentException(
                     "Content-Id may not be null or empty.");
         }

         if ((contentId.length > 1)
                 && ((char) contentId[0] == '<')
                 && ((char) contentId[contentId.length - 1] == '>')) {
             mPartHeader.put(P_CONTENT_ID, contentId);
             return;
         }

         // Insert beginning '<' and trailing '>' for Content-Id.
         byte[] buffer = new byte[contentId.length + 2];
         buffer[0] = (byte) (0xff & '<');
         buffer[buffer.length - 1] = (byte) (0xff & '>');
         System.arraycopy(contentId, 0, buffer, 1, contentId.length);
         mPartHeader.put(P_CONTENT_ID, buffer);
     }

     /**
      * Get Content-id value.
      *
      * @return the value
      */
     public byte[] getContentId() {
         return (byte[]) mPartHeader.get(P_CONTENT_ID);
     }

     /**
      * Set Char-set value.
      *
      * @param charset the value
      */
     public void setCharset(int charset) {
         mPartHeader.put(P_CHARSET, charset);
     }

     /**
      * Get Char-set value
      *
      * @return the charset value. Return 0 if charset was not set.
      */
     public int getCharset() {
         Integer charset = (Integer) mPartHeader.get(P_CHARSET);
         if(charset == null) {
             return 0;
         } else {
             return charset.intValue();
         }
     }

     /**
      * Set Content-Location value.
      *
      * @param contentLocation the value
      * @throws NullPointerException if the value is null.
      */
     public void setContentLocation(byte[] contentLocation) {
         if(contentLocation == null) {
             throw new NullPointerException("null content-location");
         }

         mPartHeader.put(P_CONTENT_LOCATION, contentLocation);
     }

     /**
      * Get Content-Location value.
      *
      * @return the value
      *     return PduPart.disposition[0] instead of <Octet 128> (Form-data).
      *     return PduPart.disposition[1] instead of <Octet 129> (Attachment).
      *     return PduPart.disposition[2] instead of <Octet 130> (Inline).
      */
     public byte[] getContentLocation() {
         return (byte[]) mPartHeader.get(P_CONTENT_LOCATION);
     }

     /**
      * Set Content-Disposition value.
      * Use PduPart.disposition[0] instead of <Octet 128> (Form-data).
      * Use PduPart.disposition[1] instead of <Octet 129> (Attachment).
      * Use PduPart.disposition[2] instead of <Octet 130> (Inline).
      *
      * @param contentDisposition the value
      * @throws NullPointerException if the value is null.
      */
     public void setContentDisposition(byte[] contentDisposition) {
         if(contentDisposition == null) {
             throw new NullPointerException("null content-disposition");
         }

         mPartHeader.put(P_CONTENT_DISPOSITION, contentDisposition);
     }

     /**
      * Get Content-Disposition value.
      *
      * @return the value
      */
     public byte[] getContentDisposition() {
         return (byte[]) mPartHeader.get(P_CONTENT_DISPOSITION);
     }

     /**
      *  Set Content-Type value.
      *
      *  @param contentType the value
      *  @throws NullPointerException if the value is null.
      */
     public void setContentType(byte[] contentType) {
         if(contentType == null) {
             throw new NullPointerException("null content-type");
         }

         mPartHeader.put(P_CONTENT_TYPE, contentType);
     }

     /**
      * Get Content-Type value of part.
      *
      * @return the value
      */
     public byte[] getContentType() {
         return (byte[]) mPartHeader.get(P_CONTENT_TYPE);
     }

     /**
      * Set Content-Transfer-Encoding value
      *
      * @param contentTransferEncoding the value
      * @throws NullPointerException if the value is null.
      */
     public void setContentTransferEncoding(byte[] contentTransferEncoding) {
         if(contentTransferEncoding == null) {
             throw new NullPointerException("null content-transfer-encoding");
         }

         mPartHeader.put(P_CONTENT_TRANSFER_ENCODING, contentTransferEncoding);
     }

     /**
      * Get Content-Transfer-Encoding value.
      *
      * @return the value
      */
     public byte[] getContentTransferEncoding() {
         return (byte[]) mPartHeader.get(P_CONTENT_TRANSFER_ENCODING);
     }

     /**
      * Set Content-type parameter: name.
      *
      * @param name the name value
      * @throws NullPointerException if the value is null.
      */
     public void setName(byte[] name) {
         if(null == name) {
             throw new NullPointerException("null content-id");
         }

         mPartHeader.put(P_NAME, name);
     }

     /**
      *  Get content-type parameter: name.
      *
      *  @return the name
      */
     public byte[] getName() {
         return (byte[]) mPartHeader.get(P_NAME);
     }

     /**
      * Get Content-disposition parameter: filename
      *
      * @param fileName the filename value
      * @throws NullPointerException if the value is null.
      */
     public void setFilename(byte[] fileName) {
         if(null == fileName) {
             throw new NullPointerException("null content-id");
         }

         mPartHeader.put(P_FILENAME, fileName);
     }

     /**
      * Set Content-disposition parameter: filename
      *
      * @return the filename
      */
     public byte[] getFilename() {
         return (byte[]) mPartHeader.get(P_FILENAME);
     }

    public String generateLocation() {
        // Assumption: At least one of the content-location / name / filename
        // or content-id should be set. This is guaranteed by the PduParser
        // for incoming messages and by MM composer for outgoing messages.
        byte[] location = (byte[]) mPartHeader.get(P_NAME);
        if(null == location) {
            location = (byte[]) mPartHeader.get(P_FILENAME);

            if (null == location) {
                location = (byte[]) mPartHeader.get(P_CONTENT_LOCATION);
            }
        }

        if (null == location) {
            byte[] contentId = (byte[]) mPartHeader.get(P_CONTENT_ID);
            return "cid:" + new String(contentId);
        } else {
            return new String(location);
        }
    }

    public PartDatabase.PartId getPartId() {
      return new PartDatabase.PartId(rowId, uniqueId);
    }

    public void setPartId(PartDatabase.PartId partId) {
      this.rowId    = partId.getRowId();
      this.uniqueId = partId.getUniqueId();
    }

    public long getRowId() {
      return rowId;
    }

    public void setRowId(long rowId) {
      this.rowId = rowId;
    }

    public Bitmap getThumbnail() {
      return thumbnail;
    }

    public void setThumbnail(Bitmap thumbnail) {
      this.thumbnail = thumbnail;
    }

    public long getUniqueId() {
      return uniqueId;
    }

    public void setUniqueId(long uniqueId) {
      this.uniqueId = uniqueId;
    }

    public long getMmsId() {
      return mmsId;
    }

    public void setMmsId(long mmsId) {
      this.mmsId = mmsId;
    }
}

