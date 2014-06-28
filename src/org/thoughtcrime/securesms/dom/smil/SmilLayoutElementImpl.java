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

import org.thoughtcrime.securesms.util.SmilUtil;
import org.w3c.dom.NodeList;
import org.w3c.dom.smil.SMILLayoutElement;
import org.w3c.dom.smil.SMILRootLayoutElement;

public class SmilLayoutElementImpl extends SmilElementImpl implements
        SMILLayoutElement {
    SmilLayoutElementImpl(SmilDocumentImpl owner, String tagName) {
        super(owner, tagName);
    }

    public boolean getResolved() {
        // TODO Auto-generated method stub
        return false;
    }

    public String getType() {
        return this.getAttribute("type");
    }

    public NodeList getRegions() {
        return this.getElementsByTagName("region");
    }

    public SMILRootLayoutElement getRootLayout() {
        NodeList childNodes = this.getChildNodes();
        SMILRootLayoutElement rootLayoutNode = null;
        int childrenCount = childNodes.getLength();
        for (int i = 0; i < childrenCount; i++) {
            if (childNodes.item(i).getNodeName().equals("root-layout")) {
                rootLayoutNode = (SMILRootLayoutElement)childNodes.item(i);
            }
        }
        if (null == rootLayoutNode) {
            // root-layout node is not set. Create a default one.
            rootLayoutNode = (SMILRootLayoutElement) getOwnerDocument().createElement("root-layout");
            rootLayoutNode.setWidth(SmilUtil.ROOT_WIDTH);
            rootLayoutNode.setHeight(SmilUtil.ROOT_HEIGHT);
            appendChild(rootLayoutNode);
        }
        return rootLayoutNode;
    }

}
