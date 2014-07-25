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

import android.util.Log;

import ws.com.google.android.mms.InvalidHeaderValueException;

public class SendReq extends MultimediaMessagePdu {
    private static final String TAG = "SendReq";
    private long databaseMessageId;
    private long messageBox;
    private long timestamp;

    public SendReq() {
        super();

        try {
            setMessageType(PduHeaders.MESSAGE_TYPE_SEND_REQ);
            setMmsVersion(PduHeaders.CURRENT_MMS_VERSION);
            // FIXME: Content-type must be decided according to whether
            // SMIL part present.
            setContentType("application/vnd.wap.multipart.related".getBytes());
            setFrom(new EncodedStringValue(PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR.getBytes()));
            setTransactionId(generateTransactionId());
        } catch (InvalidHeaderValueException e) {
            // Impossible to reach here since all headers we set above are valid.
            Log.e(TAG, "Unexpected InvalidHeaderValueException.", e);
            throw new RuntimeException(e);
        }
    }

    private byte[] generateTransactionId() {
        String transactionId = "T" + Long.toHexString(System.currentTimeMillis());
        return transactionId.getBytes();
    }

    /**
     * Constructor, used when composing a M-Send.req pdu.
     *
     * @param contentType the content type value
     * @param from the from value
     * @param mmsVersion current viersion of mms
     * @param transactionId the transaction-id value
     * @throws InvalidHeaderValueException if parameters are invalid.
     *         NullPointerException if contentType, form or transactionId is null.
     */
    public SendReq(byte[] contentType,
                   EncodedStringValue from,
                   int mmsVersion,
                   byte[] transactionId) throws InvalidHeaderValueException {
        super();
        setMessageType(PduHeaders.MESSAGE_TYPE_SEND_REQ);
        setContentType(contentType);
        setFrom(from);
        setMmsVersion(mmsVersion);
        setTransactionId(transactionId);
    }

    /**
     * Constructor with given headers.
     *
     * @param headers Headers for this PDU.
     */
    SendReq(PduHeaders headers) {
        super(headers);
    }

    /**
     * Constructor with given headers and body
     *
     * @param headers Headers for this PDU.
     * @param body Body of this PDu.
     */
    public SendReq(PduHeaders headers, PduBody body) {
        super(headers, body);
    }

    public SendReq(PduHeaders headers, PduBody body, long messageId, long messageBox, long timestamp)
    {
        super(headers, body);
        this.databaseMessageId    = messageId;
        this.messageBox           = messageBox;
        this.timestamp            = timestamp;
  }
    
    public long getDatabaseMessageBox() {
    	return this.messageBox;
    }
    
    public long getDatabaseMessageId() {
    	return databaseMessageId;
    }

    public long getSentTimestamp() {
      return timestamp;
    }

    /**
     * Get Bcc value.
     *
     * @return the value
     */
    public EncodedStringValue[] getBcc() {
        return mPduHeaders.getEncodedStringValues(PduHeaders.BCC);
    }

    /**
     * Add a "BCC" value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     */
    public void addBcc(EncodedStringValue value) {
        mPduHeaders.appendEncodedStringValue(value, PduHeaders.BCC);
    }

    /**
     * Set "BCC" value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     */
    public void setBcc(EncodedStringValue[] value) {
        mPduHeaders.setEncodedStringValues(value, PduHeaders.BCC);
    }

    /**
     * Get CC value.
     *
     * @return the value
     */
    public EncodedStringValue[] getCc() {
        return mPduHeaders.getEncodedStringValues(PduHeaders.CC);
    }

    /**
     * Add a "CC" value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     */
    public void addCc(EncodedStringValue value) {
        mPduHeaders.appendEncodedStringValue(value, PduHeaders.CC);
    }

    /**
     * Set "CC" value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     */
    public void setCc(EncodedStringValue[] value) {
        mPduHeaders.setEncodedStringValues(value, PduHeaders.CC);
    }

    /**
     * Get Content-type value.
     *
     * @return the value
     */
    public byte[] getContentType() {
        return mPduHeaders.getTextString(PduHeaders.CONTENT_TYPE);
    }

    /**
     * Set Content-type value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     */
    public void setContentType(byte[] value) {
        mPduHeaders.setTextString(value, PduHeaders.CONTENT_TYPE);
    }

    /**
     * Get X-Mms-Delivery-Report value.
     *
     * @return the value
     */
    public int getDeliveryReport() {
        return mPduHeaders.getOctet(PduHeaders.DELIVERY_REPORT);
    }

    /**
     * Set X-Mms-Delivery-Report value.
     *
     * @param value the value
     * @throws InvalidHeaderValueException if the value is invalid.
     */
    public void setDeliveryReport(int value) throws InvalidHeaderValueException {
        mPduHeaders.setOctet(value, PduHeaders.DELIVERY_REPORT);
    }

    /**
     * Get X-Mms-Expiry value.
     *
     * Expiry-value = Value-length
     *      (Absolute-token Date-value | Relative-token Delta-seconds-value)
     *
     * @return the value
     */
    public long getExpiry() {
        return mPduHeaders.getLongInteger(PduHeaders.EXPIRY);
    }

    /**
     * Set X-Mms-Expiry value.
     *
     * @param value the value
     */
    public void setExpiry(long value) {
        mPduHeaders.setLongInteger(value, PduHeaders.EXPIRY);
    }

    /**
     * Get X-Mms-MessageSize value.
     *
     * Expiry-value = size of message
     *
     * @return the value
     */
    public long getMessageSize() {
        return mPduHeaders.getLongInteger(PduHeaders.MESSAGE_SIZE);
    }

    /**
     * Set X-Mms-MessageSize value.
     *
     * @param value the value
     */
    public void setMessageSize(long value) {
        mPduHeaders.setLongInteger(value, PduHeaders.MESSAGE_SIZE);
    }

    /**
     * Get X-Mms-Message-Class value.
     * Message-class-value = Class-identifier | Token-text
     * Class-identifier = Personal | Advertisement | Informational | Auto
     *
     * @return the value
     */
    public byte[] getMessageClass() {
        return mPduHeaders.getTextString(PduHeaders.MESSAGE_CLASS);
    }

    /**
     * Set X-Mms-Message-Class value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     */
    public void setMessageClass(byte[] value) {
        mPduHeaders.setTextString(value, PduHeaders.MESSAGE_CLASS);
    }

    /**
     * Get X-Mms-Read-Report value.
     *
     * @return the value
     */
    public int getReadReport() {
        return mPduHeaders.getOctet(PduHeaders.READ_REPORT);
    }

    /**
     * Set X-Mms-Read-Report value.
     *
     * @param value the value
     * @throws InvalidHeaderValueException if the value is invalid.
     */
    public void setReadReport(int value) throws InvalidHeaderValueException {
        mPduHeaders.setOctet(value, PduHeaders.READ_REPORT);
    }

    /**
     * Set "To" value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     */
    public void setTo(EncodedStringValue[] value) {
        mPduHeaders.setEncodedStringValues(value, PduHeaders.TO);
    }

    /**
     * Get X-Mms-Transaction-Id field value.
     *
     * @return the X-Mms-Report-Allowed value
     */
    public byte[] getTransactionId() {
        return mPduHeaders.getTextString(PduHeaders.TRANSACTION_ID);
    }

    /**
     * Set X-Mms-Transaction-Id field value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     */
    public void setTransactionId(byte[] value) {
        mPduHeaders.setTextString(value, PduHeaders.TRANSACTION_ID);
    }

    /*
     * Optional, not supported header fields:
     *
     *     public byte getAdaptationAllowed() {return 0};
     *     public void setAdaptationAllowed(btye value) {};
     *
     *     public byte[] getApplicId() {return null;}
     *     public void setApplicId(byte[] value) {}
     *
     *     public byte[] getAuxApplicId() {return null;}
     *     public void getAuxApplicId(byte[] value) {}
     *
     *     public byte getContentClass() {return 0x00;}
     *     public void setApplicId(byte value) {}
     *
     *     public long getDeliveryTime() {return 0};
     *     public void setDeliveryTime(long value) {};
     *
     *     public byte getDrmContent() {return 0x00;}
     *     public void setDrmContent(byte value) {}
     *
     *     public MmFlagsValue getMmFlags() {return null;}
     *     public void setMmFlags(MmFlagsValue value) {}
     *
     *     public MmStateValue getMmState() {return null;}
     *     public void getMmState(MmStateValue value) {}
     *
     *     public byte[] getReplyApplicId() {return 0x00;}
     *     public void setReplyApplicId(byte[] value) {}
     *
     *     public byte getReplyCharging() {return 0x00;}
     *     public void setReplyCharging(byte value) {}
     *
     *     public byte getReplyChargingDeadline() {return 0x00;}
     *     public void setReplyChargingDeadline(byte value) {}
     *
     *     public byte[] getReplyChargingId() {return 0x00;}
     *     public void setReplyChargingId(byte[] value) {}
     *
     *     public long getReplyChargingSize() {return 0;}
     *     public void setReplyChargingSize(long value) {}
     *
     *     public byte[] getReplyApplicId() {return 0x00;}
     *     public void setReplyApplicId(byte[] value) {}
     *
     *     public byte getStore() {return 0x00;}
     *     public void setStore(byte value) {}
     */
}
