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

package org.thoughtcrime.securesms.dom.smil;

import org.w3c.dom.DOMException;
import org.w3c.dom.smil.SMILDocument;
import org.w3c.dom.smil.SMILRegionElement;

import android.util.Log;

public class SmilRegionElementImpl extends SmilElementImpl implements
        SMILRegionElement {

    /*
     * Internal Interface
     */

    private static final String HIDDEN_ATTRIBUTE = "hidden";
    private static final String SLICE_ATTRIBUTE = "slice";
    private static final String SCROLL_ATTRIBUTE = "scroll";
    private static final String MEET_ATTRIBUTE = "meet";
    private static final String FILL_ATTRIBUTE = "fill";
    private static final String ID_ATTRIBUTE_NAME = "id";
    private static final String WIDTH_ATTRIBUTE_NAME = "width";
    private static final String TITLE_ATTRIBUTE_NAME = "title";
    private static final String HEIGHT_ATTRIBUTE_NAME = "height";
    private static final String BACKGROUND_COLOR_ATTRIBUTE_NAME = "backgroundColor";
    private static final String Z_INDEX_ATTRIBUTE_NAME = "z-index";
    private static final String TOP_ATTRIBUTE_NAME = "top";
    private static final String LEFT_ATTRIBUTE_NAME = "left";
    private static final String RIGHT_ATTRIBUTE_NAME = "right";
    private static final String BOTTOM_ATTRIBUTE_NAME = "bottom";
    private static final String FIT_ATTRIBUTE_NAME = "fit";
    private static final String TAG = "SmilRegionElementImpl";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = false;

    SmilRegionElementImpl(SmilDocumentImpl owner, String tagName) {
        super(owner, tagName);
    }

    /*
     * SMILRegionElement Interface
     */

    public String getFit() {
        String fit = getAttribute(FIT_ATTRIBUTE_NAME);
        if (FILL_ATTRIBUTE.equalsIgnoreCase(fit)) {
            return FILL_ATTRIBUTE;
        } else if (MEET_ATTRIBUTE.equalsIgnoreCase(fit)) {
            return MEET_ATTRIBUTE;
        } else if (SCROLL_ATTRIBUTE.equalsIgnoreCase(fit)) {
            return SCROLL_ATTRIBUTE;
        } else if (SLICE_ATTRIBUTE.equalsIgnoreCase(fit)) {
            return SLICE_ATTRIBUTE;
        } else {
            return HIDDEN_ATTRIBUTE;
        }
    }

    public int getLeft() {
        try {
            return parseRegionLength(getAttribute(LEFT_ATTRIBUTE_NAME), true);
        } catch (NumberFormatException e) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Left attribute is not set or incorrect.");
            }
        }
        try {
            int bbw = ((SMILDocument) getOwnerDocument()).getLayout().getRootLayout().getWidth();
            int right = parseRegionLength(getAttribute(RIGHT_ATTRIBUTE_NAME), true);
            int width = parseRegionLength(getAttribute(WIDTH_ATTRIBUTE_NAME), true);
            return bbw - right - width;
        } catch (NumberFormatException e) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Right or width attribute is not set or incorrect.");
            }
        }
        return 0;
    }

    public int getTop() {
        try {
            return parseRegionLength(getAttribute(TOP_ATTRIBUTE_NAME), false);
        } catch (NumberFormatException e) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Top attribute is not set or incorrect.");
            }
        }
        try {
            int bbh = ((SMILDocument) getOwnerDocument()).getLayout().getRootLayout().getHeight();
            int bottom = parseRegionLength(getAttribute(BOTTOM_ATTRIBUTE_NAME), false);
            int height = parseRegionLength(getAttribute(HEIGHT_ATTRIBUTE_NAME), false);
            return bbh - bottom - height;
        } catch (NumberFormatException e) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Bottom or height attribute is not set or incorrect.");
            }
        }
        return 0;
    }

    public int getZIndex() {
        try {
            return Integer.parseInt(this.getAttribute(Z_INDEX_ATTRIBUTE_NAME));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void setFit(String fit) throws DOMException {
        if (fit.equalsIgnoreCase(FILL_ATTRIBUTE)
                || fit.equalsIgnoreCase(MEET_ATTRIBUTE)
                || fit.equalsIgnoreCase(SCROLL_ATTRIBUTE)
                || fit.equalsIgnoreCase(SLICE_ATTRIBUTE)) {
            this.setAttribute(FIT_ATTRIBUTE_NAME, fit.toLowerCase());
        } else {
            this.setAttribute(FIT_ATTRIBUTE_NAME, HIDDEN_ATTRIBUTE);
        }
    }

    public void setLeft(int left) throws DOMException {
        this.setAttribute(LEFT_ATTRIBUTE_NAME, String.valueOf(left));
    }

    public void setTop(int top) throws DOMException {
        this.setAttribute(TOP_ATTRIBUTE_NAME, String.valueOf(top));
    }

    public void setZIndex(int zIndex) throws DOMException {
        if (zIndex > 0) {
            this.setAttribute(Z_INDEX_ATTRIBUTE_NAME, Integer.toString(zIndex));
        } else {
            this.setAttribute(Z_INDEX_ATTRIBUTE_NAME, Integer.toString(0));
        }
    }

    public String getBackgroundColor() {
        return this.getAttribute(BACKGROUND_COLOR_ATTRIBUTE_NAME);
    }

    public int getHeight() {
        try {
            final int height = parseRegionLength(getAttribute(HEIGHT_ATTRIBUTE_NAME), false);
            return height == 0 ?
                    ((SMILDocument) getOwnerDocument()).getLayout().getRootLayout().getHeight() :
                        height;
        } catch (NumberFormatException e) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Height attribute is not set or incorrect.");
            }
        }
        int bbh = ((SMILDocument) getOwnerDocument()).getLayout().getRootLayout().getHeight();
        try {
            bbh -= parseRegionLength(getAttribute(TOP_ATTRIBUTE_NAME), false);
        } catch (NumberFormatException e) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Top attribute is not set or incorrect.");
            }
        }
        try {
            bbh -= parseRegionLength(getAttribute(BOTTOM_ATTRIBUTE_NAME), false);
        } catch (NumberFormatException e) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Bottom attribute is not set or incorrect.");
            }
        }
        return bbh;
    }

    public String getTitle() {
        return this.getAttribute(TITLE_ATTRIBUTE_NAME);
    }

    public int getWidth() {
        try {
            final int width = parseRegionLength(getAttribute(WIDTH_ATTRIBUTE_NAME), true);
            return width == 0 ?
                    ((SMILDocument) getOwnerDocument()).getLayout().getRootLayout().getWidth() :
                        width;
        } catch (NumberFormatException e) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Width attribute is not set or incorrect.");
            }
        }
        int bbw = ((SMILDocument) getOwnerDocument()).getLayout().getRootLayout().getWidth();
        try {
            bbw -= parseRegionLength(getAttribute(LEFT_ATTRIBUTE_NAME), true);
        } catch (NumberFormatException e) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Left attribute is not set or incorrect.");
            }
        }
        try {
            bbw -= parseRegionLength(getAttribute(RIGHT_ATTRIBUTE_NAME), true);
        } catch (NumberFormatException e) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Right attribute is not set or incorrect.");
            }
        }
        return bbw;
    }

    public void setBackgroundColor(String backgroundColor) throws DOMException {
        this.setAttribute(BACKGROUND_COLOR_ATTRIBUTE_NAME, backgroundColor);
    }

    public void setHeight(int height) throws DOMException {
        this.setAttribute(HEIGHT_ATTRIBUTE_NAME, String.valueOf(height) + "px");
    }

    public void setTitle(String title) throws DOMException {
        this.setAttribute(TITLE_ATTRIBUTE_NAME, title);
    }

    public void setWidth(int width) throws DOMException {
        this.setAttribute(WIDTH_ATTRIBUTE_NAME, String.valueOf(width) + "px");
    }

    /*
     * SMILElement Interface
     */

    @Override
    public String getId() {
        return this.getAttribute(ID_ATTRIBUTE_NAME);
    }

    @Override
    public void setId(String id) throws DOMException {
        this.setAttribute(ID_ATTRIBUTE_NAME, id);
    }

    /*
     * Internal Interface
     */

    private int parseRegionLength(String length, boolean horizontal) {
        if (length.endsWith("px")) {
            length = length.substring(0, length.indexOf("px"));
            return Integer.parseInt(length);
        } else if (length.endsWith("%")) {
            double value = 0.01*Integer.parseInt(length.substring(0, length.length() - 1));
            if (horizontal) {
                value *= ((SMILDocument) getOwnerDocument()).getLayout().getRootLayout().getWidth();
            } else {
                value *= ((SMILDocument) getOwnerDocument()).getLayout().getRootLayout().getHeight();
            }
            return (int) Math.round(value);
        } else {
            return Integer.parseInt(length);
        }
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return super.toString()
                + ": id=" + getId()
                + ", width=" + getWidth()
                + ", height=" + getHeight()
                + ", left=" + getLeft()
                + ", top=" + getTop();
    }
}
