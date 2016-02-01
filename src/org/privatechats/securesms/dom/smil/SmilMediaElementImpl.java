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

package org.privatechats.securesms.dom.smil;

import org.w3c.dom.DOMException;
import org.w3c.dom.events.DocumentEvent;
import org.w3c.dom.events.Event;
import org.w3c.dom.smil.ElementTime;
import org.w3c.dom.smil.SMILMediaElement;
import org.w3c.dom.smil.TimeList;

import android.util.Log;

import org.privatechats.securesms.dom.events.EventImpl;

public class SmilMediaElementImpl extends SmilElementImpl implements
        SMILMediaElement {
    public final static String SMIL_MEDIA_START_EVENT = "SmilMediaStart";
    public final static String SMIL_MEDIA_END_EVENT = "SmilMediaEnd";
    public final static String SMIL_MEDIA_PAUSE_EVENT = "SmilMediaPause";
    public final static String SMIL_MEDIA_SEEK_EVENT = "SmilMediaSeek";
    private final static String TAG = "Mms:smil";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = false;

    ElementTime mElementTime = new ElementTimeImpl(this) {
            private Event createEvent(String eventType) {
                DocumentEvent doc =
                    (DocumentEvent)SmilMediaElementImpl.this.getOwnerDocument();
                Event event = doc.createEvent("Event");
                event.initEvent(eventType, false, false);
                if (LOCAL_LOGV) {
                    Log.v(TAG, "Dispatching 'begin' event to "
                            + SmilMediaElementImpl.this.getTagName() + " "
                            + SmilMediaElementImpl.this.getSrc() + " at "
                            + System.currentTimeMillis());
                }
                return event;
            }

            private Event createEvent(String eventType, int seekTo) {
                DocumentEvent doc =
                    (DocumentEvent)SmilMediaElementImpl.this.getOwnerDocument();
                EventImpl event = (EventImpl) doc.createEvent("Event");
                event.initEvent(eventType, false, false, seekTo);
                if (LOCAL_LOGV) {
                    Log.v(TAG, "Dispatching 'begin' event to "
                            + SmilMediaElementImpl.this.getTagName() + " "
                            + SmilMediaElementImpl.this.getSrc() + " at "
                            + System.currentTimeMillis());
                }
                return event;
            }

            public boolean beginElement() {
                Event startEvent = createEvent(SMIL_MEDIA_START_EVENT);
                dispatchEvent(startEvent);
                return true;
            }

            public boolean endElement() {
                Event endEvent = createEvent(SMIL_MEDIA_END_EVENT);
                dispatchEvent(endEvent);
                return true;
            }

            public void resumeElement() {
                Event resumeEvent = createEvent(SMIL_MEDIA_START_EVENT);
                dispatchEvent(resumeEvent);
            }

            public void pauseElement() {
                Event pauseEvent = createEvent(SMIL_MEDIA_PAUSE_EVENT);
                dispatchEvent(pauseEvent);
            }

            public void seekElement(float seekTo) {
                Event seekEvent = createEvent(SMIL_MEDIA_SEEK_EVENT, (int) seekTo);
                dispatchEvent(seekEvent);
            }

            @Override
            public float getDur() {
                float dur = super.getDur();
                if (dur == 0) {
                    // Duration is not specified, So get the implicit duration.
                    String tag = getTagName();
                    if (tag.equals("video") || tag.equals("audio") || tag.equals("app")) {
                        // Continuous media
                        // FIXME Should get the duration of the media. "indefinite" instead here.
                        dur = -1.0F;
                    } else if (tag.equals("text") || tag.equals("img")) {
                        // Discrete media
                        dur = 0;
                    } else {
                        Log.w(TAG, "Unknown media type");
                    }
                }
                return dur;
            }

            @Override
            ElementTime getParentElementTime() {
                return ((SmilParElementImpl) mSmilElement.getParentNode()).mParTimeContainer;
            }
    };

    /*
     * Internal Interface
     */

    SmilMediaElementImpl(SmilDocumentImpl owner, String tagName) {
        super(owner, tagName);
    }

    /*
     * SMILMediaElement Interface
     */

    public String getAbstractAttr() {
        return this.getAttribute("abstract");
    }

    public String getAlt() {
        return this.getAttribute("alt");
    }

    public String getAuthor() {
        return this.getAttribute("author");
    }

    public String getClipBegin() {
        return this.getAttribute("clipBegin");
    }

    public String getClipEnd() {
        return this.getAttribute("clipEnd");
    }

    public String getCopyright() {
        return this.getAttribute("copyright");
    }

    public String getLongdesc() {
        return this.getAttribute("longdesc");
    }

    public String getPort() {
        return this.getAttribute("port");
    }

    public String getReadIndex() {
        return this.getAttribute("readIndex");
    }

    public String getRtpformat() {
        return this.getAttribute("rtpformat");
    }

    public String getSrc() {
        return this.getAttribute("src");
    }

    public String getStripRepeat() {
        return this.getAttribute("stripRepeat");
    }

    public String getTitle() {
        return this.getAttribute("title");
    }

    public String getTransport() {
        return this.getAttribute("transport");
    }

    public String getType() {
        return this.getAttribute("type");
    }

    public void setAbstractAttr(String abstractAttr) throws DOMException {
        this.setAttribute("abstract", abstractAttr);
    }

    public void setAlt(String alt) throws DOMException {
        this.setAttribute("alt", alt);
    }

    public void setAuthor(String author) throws DOMException {
        this.setAttribute("author", author);
    }

    public void setClipBegin(String clipBegin) throws DOMException {
        this.setAttribute("clipBegin", clipBegin);
    }

    public void setClipEnd(String clipEnd) throws DOMException {
        this.setAttribute("clipEnd", clipEnd);
    }

    public void setCopyright(String copyright) throws DOMException {
        this.setAttribute("copyright", copyright);
    }

    public void setLongdesc(String longdesc) throws DOMException {
        this.setAttribute("longdesc", longdesc);

    }

    public void setPort(String port) throws DOMException {
        this.setAttribute("port", port);
    }

    public void setReadIndex(String readIndex) throws DOMException {
        this.setAttribute("readIndex", readIndex);
    }

    public void setRtpformat(String rtpformat) throws DOMException {
        this.setAttribute("rtpformat", rtpformat);
    }

    public void setSrc(String src) throws DOMException {
        this.setAttribute("src", src);
    }

    public void setStripRepeat(String stripRepeat) throws DOMException {
        this.setAttribute("stripRepeat", stripRepeat);
    }

    public void setTitle(String title) throws DOMException {
        this.setAttribute("title", title);
    }

    public void setTransport(String transport) throws DOMException {
        this.setAttribute("transport", transport);
    }

    public void setType(String type) throws DOMException {
        this.setAttribute("type", type);
    }

    /*
     * TimeElement Interface
     */

    public boolean beginElement() {
        return mElementTime.beginElement();
    }

    public boolean endElement() {
        return mElementTime.endElement();
    }

    public TimeList getBegin() {
        return mElementTime.getBegin();
    }

    public float getDur() {
        return mElementTime.getDur();
    }

    public TimeList getEnd() {
        return mElementTime.getEnd();
    }

    public short getFill() {
        return mElementTime.getFill();
    }

    public short getFillDefault() {
        return mElementTime.getFillDefault();
    }

    public float getRepeatCount() {
        return mElementTime.getRepeatCount();
    }

    public float getRepeatDur() {
        return mElementTime.getRepeatDur();
    }

    public short getRestart() {
        return mElementTime.getRestart();
    }

    public void pauseElement() {
        mElementTime.pauseElement();
    }

    public void resumeElement() {
        mElementTime.resumeElement();
    }

    public void seekElement(float seekTo) {
        mElementTime.seekElement(seekTo);
    }

    public void setBegin(TimeList begin) throws DOMException {
        mElementTime.setBegin(begin);
    }

    public void setDur(float dur) throws DOMException {
        mElementTime.setDur(dur);
    }

    public void setEnd(TimeList end) throws DOMException {
        mElementTime.setEnd(end);
    }

    public void setFill(short fill) throws DOMException {
        mElementTime.setFill(fill);
    }

    public void setFillDefault(short fillDefault) throws DOMException {
        mElementTime.setFillDefault(fillDefault);
    }

    public void setRepeatCount(float repeatCount) throws DOMException {
        mElementTime.setRepeatCount(repeatCount);
    }

    public void setRepeatDur(float repeatDur) throws DOMException {
        mElementTime.setRepeatDur(repeatDur);
    }

    public void setRestart(short restart) throws DOMException {
        mElementTime.setRestart(restart);
    }
}
