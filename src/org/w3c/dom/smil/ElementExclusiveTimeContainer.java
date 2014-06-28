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
import org.w3c.dom.NodeList;

/**
 *  This interface defines a time container with semantics based upon par, but 
 * with the additional constraint that only one child element may play at a 
 * time. 
 */
public interface ElementExclusiveTimeContainer extends ElementTimeContainer {
    /**
     *  Controls the end of the container.  Need to address thr id-ref value. 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getEndSync();
    public void setEndSync(String endSync)
                                     throws DOMException;

    /**
     *  This should support another method to get the ordered collection of 
     * paused elements (the paused stack) at a given point in time. 
     * @return  All paused elements at the current time. 
     */
    public NodeList getPausedElements();

}

