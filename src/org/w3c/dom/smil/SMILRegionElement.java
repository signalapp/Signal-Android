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
 *   1) ADD public int getLeft();
 *   2) ADD public void setLeft(int top) throws DOMException;
 *   3) MODIFY public String getTop() to public int getTop();
 *   4) MODIFY public void setTop(String) to public void setTop(int);
 */

package org.w3c.dom.smil;

import org.w3c.dom.DOMException;

/**
 *  Controls the position, size and scaling of media object elements. See the
 * region element definition .
 */
public interface SMILRegionElement extends SMILElement, ElementLayout {
    /**
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly.
     */
    public String getFit();
    public void setFit(String fit)
                                      throws DOMException;

    /**
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly.
     */
    public int getLeft();
    public void setLeft(int top)
                                      throws DOMException;

    /**
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly.
     */
    public int getTop();
    public void setTop(int top)
                                      throws DOMException;

    /**
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly.
     */
    public int getZIndex();
    public void setZIndex(int zIndex)
                                      throws DOMException;

}

