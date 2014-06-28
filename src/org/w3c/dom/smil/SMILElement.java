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
 */

package org.w3c.dom.smil;

import org.w3c.dom.DOMException;
import org.w3c.dom.Element;

/**
 *  The <code>SMILElement</code> interface is the base for all SMIL element 
 * types. It follows the model of the <code>HTMLElement</code> in the HTML 
 * DOM, extending the base <code>Element</code> class to denote SMIL-specific 
 * elements. 
 * <p> Note that the <code>SMILElement</code> interface overlaps with the 
 * <code>HTMLElement</code> interface. In practice, an integrated document 
 * profile that include HTML and SMIL modules will effectively implement both 
 * interfaces (see also the DOM documentation discussion of  Inheritance vs 
 * Flattened Views of the API ).  // etc. This needs attention
 */
public interface SMILElement extends Element {
    /**
     *  The unique id.
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getId();
    public void setId(String id)
                                      throws DOMException;

}

