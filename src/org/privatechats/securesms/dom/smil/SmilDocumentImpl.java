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

import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.DocumentEvent;
import org.w3c.dom.events.Event;
import org.w3c.dom.smil.ElementSequentialTimeContainer;
import org.w3c.dom.smil.ElementTime;
import org.w3c.dom.smil.SMILDocument;
import org.w3c.dom.smil.SMILElement;
import org.w3c.dom.smil.SMILLayoutElement;
import org.w3c.dom.smil.TimeList;

import org.privatechats.securesms.dom.DocumentImpl;
import org.privatechats.securesms.dom.events.EventImpl;

public class SmilDocumentImpl extends DocumentImpl implements SMILDocument, DocumentEvent {
    /*
     * The sequential time container cannot be initialized here because the real container
     * is body, which hasn't been created yet. It will be initialized when the body has
     * already been created. Please see getBody().
     */
    ElementSequentialTimeContainer mSeqTimeContainer;

    public final static String SMIL_DOCUMENT_START_EVENT = "SmilDocumentStart";
    public final static String SMIL_DOCUMENT_END_EVENT = "SimlDocumentEnd";

    /*
     * Internal methods
     */
    public SmilDocumentImpl() {
        super();
    }

    /*
     * ElementSequentialTimeContainer stuff
     */

    public NodeList getActiveChildrenAt(float instant) {
        return mSeqTimeContainer.getActiveChildrenAt(instant);
    }

    public NodeList getTimeChildren() {
        return mSeqTimeContainer.getTimeChildren();
    }

    public boolean beginElement() {
        return mSeqTimeContainer.beginElement();
    }

    public boolean endElement() {
        return mSeqTimeContainer.endElement();
    }

    public TimeList getBegin() {
        return mSeqTimeContainer.getBegin();
    }

    public float getDur() {
        return mSeqTimeContainer.getDur();
    }

    public TimeList getEnd() {
        return mSeqTimeContainer.getEnd();
    }

    public short getFill() {
        return mSeqTimeContainer.getFill();
    }

    public short getFillDefault() {
        return mSeqTimeContainer.getFillDefault();
    }

    public float getRepeatCount() {
        return mSeqTimeContainer.getRepeatCount();
    }

    public float getRepeatDur() {
        return mSeqTimeContainer.getRepeatDur();
    }

    public short getRestart() {
        return mSeqTimeContainer.getRestart();
    }

    public void pauseElement() {
        mSeqTimeContainer.pauseElement();
    }

    public void resumeElement() {
        mSeqTimeContainer.resumeElement();
    }

    public void seekElement(float seekTo) {
        mSeqTimeContainer.seekElement(seekTo);
    }

    public void setBegin(TimeList begin) throws DOMException {
        mSeqTimeContainer.setBegin(begin);
    }

    public void setDur(float dur) throws DOMException {
        mSeqTimeContainer.setDur(dur);
    }

    public void setEnd(TimeList end) throws DOMException {
        mSeqTimeContainer.setEnd(end);
    }

    public void setFill(short fill) throws DOMException {
        mSeqTimeContainer.setFill(fill);
    }

    public void setFillDefault(short fillDefault) throws DOMException {
        mSeqTimeContainer.setFillDefault(fillDefault);
    }

    public void setRepeatCount(float repeatCount) throws DOMException {
        mSeqTimeContainer.setRepeatCount(repeatCount);
    }

    public void setRepeatDur(float repeatDur) throws DOMException {
        mSeqTimeContainer.setRepeatDur(repeatDur);
    }

    public void setRestart(short restart) throws DOMException {
        mSeqTimeContainer.setRestart(restart);
    }

    /*
     * Document Interface
     */

    @Override
    public Element createElement(String tagName) throws DOMException {
        // Find the appropriate class for this element
        tagName = tagName.toLowerCase();
        if (tagName.equals("text") ||
                tagName.equals("img") ||
                tagName.equals("video")) {
            return new SmilRegionMediaElementImpl(this, tagName);
        } else if (tagName.equals("audio")) {
            return new SmilMediaElementImpl(this, tagName);
        } else if (tagName.equals("layout")) {
            return new SmilLayoutElementImpl(this, tagName);
        } else if (tagName.equals("root-layout")) {
            return new SmilRootLayoutElementImpl(this, tagName);
        } else if (tagName.equals("region")) {
            return new SmilRegionElementImpl(this, tagName);
        } else if (tagName.equals("ref")) {
            return new SmilRefElementImpl(this, tagName);
        } else if (tagName.equals("par")) {
            return new SmilParElementImpl(this, tagName);
        } else {
            // This includes also the structural nodes SMIL,
            // HEAD, BODY, for which no specific types are defined.
            return new SmilElementImpl(this, tagName);
        }
    }

    @Override
    public SMILElement getDocumentElement() {
        Node rootElement = getFirstChild();
        if (rootElement == null || !(rootElement instanceof SMILElement)) {
            // The root doesn't exist. Create a new one.
            rootElement = createElement("smil");
            appendChild(rootElement);
        }

        return (SMILElement) rootElement;
    }

    /*
     * SMILElement Interface
     */

    public SMILElement getHead() {
        Node rootElement = getDocumentElement();
        Node headElement = rootElement.getFirstChild();
        if (headElement == null || !(headElement instanceof SMILElement)) {
            // The head doesn't exist. Create a new one.
            headElement = createElement("head");
            rootElement.appendChild(headElement);
        }

        return (SMILElement) headElement;
    }

    public SMILElement getBody() {
        Node rootElement = getDocumentElement();
        Node headElement = getHead();
        Node bodyElement = headElement.getNextSibling();
        if (bodyElement == null || !(bodyElement instanceof SMILElement)) {
            // The body doesn't exist. Create a new one.
            bodyElement = createElement("body");
            rootElement.appendChild(bodyElement);
        }

        // Initialize the real sequential time container, which is body.
        mSeqTimeContainer = new ElementSequentialTimeContainerImpl((SMILElement) bodyElement) {
            public NodeList getTimeChildren() {
                return getBody().getElementsByTagName("par");
            }

            public boolean beginElement() {
                Event startEvent = createEvent("Event");
                startEvent.initEvent(SMIL_DOCUMENT_START_EVENT, false, false);
                dispatchEvent(startEvent);
                return true;
            }

            public boolean endElement() {
                Event endEvent = createEvent("Event");
                endEvent.initEvent(SMIL_DOCUMENT_END_EVENT, false, false);
                dispatchEvent(endEvent);
                return true;
            }

            public void pauseElement() {
                // TODO Auto-generated method stub

            }

            public void resumeElement() {
                // TODO Auto-generated method stub

            }

            public void seekElement(float seekTo) {
                // TODO Auto-generated method stub

            }

            ElementTime getParentElementTime() {
                return null;
            }
        };

        return (SMILElement) bodyElement;
    }

    public SMILLayoutElement getLayout() {
        Node headElement = getHead();
        Node layoutElement = null;

        // Find the layout element under <code>HEAD</code>
        layoutElement = headElement.getFirstChild();
        while ((layoutElement != null) && !(layoutElement instanceof SMILLayoutElement)) {
            layoutElement = layoutElement.getNextSibling();
        }

        if (layoutElement == null) {
            // The layout doesn't exist. Create a default one.
            layoutElement = new SmilLayoutElementImpl(this, "layout");
            headElement.appendChild(layoutElement);
        }
        return (SMILLayoutElement) layoutElement;
    }

    /*
     * DocumentEvent Interface
     */
    public Event createEvent(String eventType) throws DOMException {
        if ("Event".equals(eventType)) {
            return new EventImpl();
        } else {
            throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                       "Not supported interface");
        }
    }
}
