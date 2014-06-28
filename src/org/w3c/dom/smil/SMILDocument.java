/*
 * Copyright (c) 2000 World Wide Web Consortium,
 * (Massachusetts Institute of Technology, Institut National de
 * Recherche en Informatique et en Automatique, Keio University). All
 * Rights Reserved. This program is distributed under the W3C's Software
 * Intellectual Property License. This program is distributed in the
 * hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See W3C License http://www.w3.org/Consortium/Legal/ for more
 * details.
 *
 * Difference to the original copy of this file:
 *   1) ADD public SMILElement getHead();
 *   2) ADD public SMILElement getBody();
 *   3) ADD public SMILLayoutElement getLayout();
 */

package org.w3c.dom.smil;

import org.w3c.dom.Document;

/**
 *  A SMIL document is the root of the SMIL Hierarchy and holds the entire
 * content. Beside providing access to the hierarchy, it also provides some
 * convenience methods for accessing certain sets of information from the
 * document.  Cover document timing, document locking?, linking modality and
 * any other document level issues. Are there issues with nested SMIL files?
 * Is it worth talking about different document scenarios, corresponding to
 * differing profiles? E.g. Standalone SMIL, HTML integration, etc.
 */
public interface SMILDocument extends Document, ElementSequentialTimeContainer {

    /**
     * Returns the element that contains the layout node of this document,
     * i.e. the <code>HEAD</code> element.
     */
    public SMILElement getHead();

    /**
     * Returns the element that contains the par's of the document, i.e. the
     * <code>BODY</code> element.
     */
    public SMILElement getBody();

    /**
     * Returns the element that contains the layout information of the presentation,
     * i.e. the <code>LAYOUT</code> element.
     */
    public SMILLayoutElement getLayout();
}

