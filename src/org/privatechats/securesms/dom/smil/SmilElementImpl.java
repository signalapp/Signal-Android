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

package org.thoughtcrime.securesms.dom.smil;

import org.w3c.dom.DOMException;
import org.w3c.dom.smil.SMILElement;

import org.thoughtcrime.securesms.dom.ElementImpl;

public class SmilElementImpl extends ElementImpl implements SMILElement {
    /**
     * This constructor is used by the factory methods of the SmilDocument.
     *
     * @param owner The SMIL document to which this element belongs to
     * @param tagName The tag name of the element
     */
    SmilElementImpl(SmilDocumentImpl owner, String tagName)
    {
        super(owner, tagName.toLowerCase());
    }

    public String getId() {
        // TODO Auto-generated method stub
        return null;
    }

    public void setId(String id) throws DOMException {
        // TODO Auto-generated method stub

    }

}
