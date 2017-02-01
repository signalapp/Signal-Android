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

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class PduBody {
    private Vector<PduPart> mParts = null;

    private Map<String, PduPart> mPartMapByContentId = null;
    private Map<String, PduPart> mPartMapByContentLocation = null;
    private Map<String, PduPart> mPartMapByName = null;
    private Map<String, PduPart> mPartMapByFileName = null;

    /**
     * Constructor.
     */
    public PduBody() {
        mParts = new Vector<PduPart>();

        mPartMapByContentId = new HashMap<String, PduPart>();
        mPartMapByContentLocation  = new HashMap<String, PduPart>();
        mPartMapByName = new HashMap<String, PduPart>();
        mPartMapByFileName = new HashMap<String, PduPart>();
    }

    private void putPartToMaps(PduPart part) {
        // Put part to mPartMapByContentId.
        byte[] contentId = part.getContentId();
        if(null != contentId) {
            mPartMapByContentId.put(new String(contentId), part);
        }

        // Put part to mPartMapByContentLocation.
        byte[] contentLocation = part.getContentLocation();
        if(null != contentLocation) {
            String clc = new String(contentLocation);
            mPartMapByContentLocation.put(clc, part);
        }

        // Put part to mPartMapByName.
        byte[] name = part.getName();
        if(null != name) {
            String clc = new String(name);
            mPartMapByName.put(clc, part);
        }

        // Put part to mPartMapByFileName.
        byte[] fileName = part.getFilename();
        if(null != fileName) {
            String clc = new String(fileName);
            mPartMapByFileName.put(clc, part);
        }
    }

    /**
     * Appends the specified part to the end of this body.
     *
     * @param part part to be appended
     * @return true when success, false when fail
     * @throws NullPointerException when part is null
     */
    public boolean addPart(PduPart part) {
        if(null == part) {
            throw new NullPointerException();
        }

        putPartToMaps(part);
        return mParts.add(part);
    }

    /**
     * Inserts the specified part at the specified position.
     *
     * @param index index at which the specified part is to be inserted
     * @param part part to be inserted
     * @throws NullPointerException when part is null
     */
    public void addPart(int index, PduPart part) {
        if(null == part) {
            throw new NullPointerException();
        }

        putPartToMaps(part);
        mParts.add(index, part);
    }

    /**
     * Removes the part at the specified position.
     *
     * @param index index of the part to return
     * @return part at the specified index
     */
    public PduPart removePart(int index) {
        return mParts.remove(index);
    }

    /**
     * Remove all of the parts.
     */
    public void removeAll() {
        mParts.clear();
    }

    /**
     * Get the part at the specified position.
     *
     * @param index index of the part to return
     * @return part at the specified index
     */
    public PduPart getPart(int index) {
        return mParts.get(index);
    }

    /**
     * Get the index of the specified part.
     *
     * @param part the part object
     * @return index the index of the first occurrence of the part in this body
     */
    public int getPartIndex(PduPart part) {
        return mParts.indexOf(part);
    }

    /**
     * Get the number of parts.
     *
     * @return the number of parts
     */
    public int getPartsNum() {
        return mParts.size();
    }

    /**
     * Get pdu part by content id.
     *
     * @param cid the value of content id.
     * @return the pdu part.
     */
    public PduPart getPartByContentId(String cid) {
        return mPartMapByContentId.get(cid);
    }

    /**
     * Get pdu part by Content-Location. Content-Location of part is
     * the same as filename and name (param of content-type).
     *
     * @param fileName the value of filename.
     * @return the pdu part.
     */
    public PduPart getPartByContentLocation(String contentLocation) {
        return mPartMapByContentLocation.get(contentLocation);
    }

    /**
     * Get pdu part by name.
     *
     * @param fileName the value of filename.
     * @return the pdu part.
     */
    public PduPart getPartByName(String name) {
        return mPartMapByName.get(name);
    }

    /**
     * Get pdu part by filename.
     *
     * @param fileName the value of filename.
     * @return the pdu part.
     */
    public PduPart getPartByFileName(String filename) {
        return mPartMapByFileName.get(filename);
    }
}
