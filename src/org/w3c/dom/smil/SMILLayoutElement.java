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
 *   1) ADD public SMILRootLayoutElement getRootLayout();
 *   2) ADD public NodeList getRegions();
 */

package org.w3c.dom.smil;

import org.w3c.dom.NodeList;

/**
 *  Declares layout type for the document. See the  LAYOUT element definition .
 *
 */
public interface SMILLayoutElement extends SMILElement {
    /**
     *  The mime type of the layout langage used in this layout element.The
     * default value of the type attribute is "text/smil-basic-layout".
     */
    public String getType();

    /**
     *  <code>true</code> if the player can understand the mime type,
     * <code>false</code> otherwise.
     */
    public boolean getResolved();

    /**
     * Returns the root layout element of this document.
     */
    public SMILRootLayoutElement getRootLayout();

    /**
     * Return the region elements of this document.
     */
    public NodeList getRegions();
}

