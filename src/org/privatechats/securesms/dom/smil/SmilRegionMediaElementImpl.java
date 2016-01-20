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

import org.w3c.dom.NodeList;
import org.w3c.dom.smil.SMILDocument;
import org.w3c.dom.smil.SMILRegionElement;
import org.w3c.dom.smil.SMILRegionMediaElement;

public class SmilRegionMediaElementImpl extends SmilMediaElementImpl implements
        SMILRegionMediaElement {
    private SMILRegionElement mRegion;

    SmilRegionMediaElementImpl(SmilDocumentImpl owner, String tagName) {
        super(owner, tagName);
    }

    public SMILRegionElement getRegion() {
        if (mRegion == null) {
            SMILDocument doc = (SMILDocument)this.getOwnerDocument();
            NodeList regions = doc.getLayout().getElementsByTagName("region");
            SMILRegionElement region = null;
            for (int i = 0; i < regions.getLength(); i++) {
                region = (SMILRegionElement)regions.item(i);
                if (region.getId().equals(this.getAttribute("region"))) {
                    mRegion = region;
                }
            }
        }
        return mRegion;
    }

    public void setRegion(SMILRegionElement region) {
        this.setAttribute("region", region.getId());
        mRegion = region;
    }

}
