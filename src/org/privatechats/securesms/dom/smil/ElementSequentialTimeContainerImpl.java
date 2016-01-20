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

package org.privatechats.securesms.dom.smil;

import java.util.ArrayList;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.smil.ElementSequentialTimeContainer;
import org.w3c.dom.smil.ElementTime;
import org.w3c.dom.smil.SMILElement;

import org.privatechats.securesms.dom.NodeListImpl;

public abstract class ElementSequentialTimeContainerImpl extends
        ElementTimeContainerImpl implements ElementSequentialTimeContainer {

    /*
     * Internal Interface
     */

    ElementSequentialTimeContainerImpl(SMILElement element) {
        super(element);
    }

    /*
     * ElementSequentialTimeContainer Interface
     */

    public NodeList getActiveChildrenAt(float instant) {
        NodeList allChildren = this.getTimeChildren();
        ArrayList<Node> nodes = new ArrayList<Node>();
        for (int i = 0; i < allChildren.getLength(); i++) {
            instant -= ((ElementTime) allChildren.item(i)).getDur();
            if (instant < 0) {
                nodes.add(allChildren.item(i));
                return new NodeListImpl(nodes);
            }
        }
        return new NodeListImpl(nodes);
    }

    public float getDur() {
        float dur = super.getDur();
        if (dur == 0) {
            NodeList children = getTimeChildren();
            for (int i = 0; i < children.getLength(); ++i) {
                ElementTime child = (ElementTime) children.item(i);
                if (child.getDur() < 0) {
                    // Return "indefinite" since containing a child whose duration is indefinite.
                    return -1.0F;
                }
                dur += child.getDur();
            }
        }
        return dur;
    }
}
