/*
 * Copyright (C) 2007 Esmertec AG.
 * Copyright (C) 2007 The Android Open Source Project
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

import ws.com.google.android.mms.InvalidHeaderValueException;

/**
 * M-NofifyResp.ind PDU.
 */
public class NotifyRespInd extends GenericPdu {
    /**
     * Constructor, used when composing a M-NotifyResp.ind pdu.
     *
     * @param mmsVersion current version of mms
     * @param transactionId the transaction-id value
     * @param status the status value
     * @throws InvalidHeaderValueException if parameters are invalid.
     *         NullPointerException if transactionId is null.
     *         RuntimeException if an undeclared error occurs.
     */
    public NotifyRespInd(int mmsVersion,
                         byte[] transactionId,
                         int status) throws InvalidHeaderValueException {
        super();
        setMessageType(PduHeaders.MESSAGE_TYPE_NOTIFYRESP_IND);
        setMmsVersion(mmsVersion);
        setTransactionId(transactionId);
        setStatus(status);
    }

    /**
     * Constructor with given headers.
     *
     * @param headers Headers for this PDU.
     */
    NotifyRespInd(PduHeaders headers) {
        super(headers);
    }

    /**
     * Get X-Mms-Report-Allowed field value.
     *
     * @return the X-Mms-Report-Allowed value
     */
    public int getReportAllowed() {
        return mPduHeaders.getOctet(PduHeaders.REPORT_ALLOWED);
    }

    /**
     * Set X-Mms-Report-Allowed field value.
     *
     * @param value the value
     * @throws InvalidHeaderValueException if the value is invalid.
     *         RuntimeException if an undeclared error occurs.
     */
    public void setReportAllowed(int value) throws InvalidHeaderValueException {
        mPduHeaders.setOctet(value, PduHeaders.REPORT_ALLOWED);
    }

    /**
     * Set X-Mms-Status field value.
     *
     * @param value the value
     * @throws InvalidHeaderValueException if the value is invalid.
     *         RuntimeException if an undeclared error occurs.
     */
    public void setStatus(int value) throws InvalidHeaderValueException {
        mPduHeaders.setOctet(value, PduHeaders.STATUS);
    }

    /**
     * GetX-Mms-Status field value.
     *
     * @return the X-Mms-Status value
     */
    public int getStatus() {
        return mPduHeaders.getOctet(PduHeaders.STATUS);
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
     *         RuntimeException if an undeclared error occurs.
     */
    public void setTransactionId(byte[] value) {
            mPduHeaders.setTextString(value, PduHeaders.TRANSACTION_ID);
    }
}
