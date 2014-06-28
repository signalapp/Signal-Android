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

/**
 *  A <code>parallel</code> container defines a simple parallel time grouping 
 * in which multiple elements can play back at the same time.  It may have to 
 * specify a repeat iteration. (?) 
 */
public interface ElementParallelTimeContainer extends ElementTimeContainer {
    /**
     *  Controls the end of the container.  Need to address thr id-ref value. 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getEndSync();
    public void setEndSync(String endSync)
                                        throws DOMException;

    /**
     *  This method returns the implicit duration in seconds. 
     * @return  The implicit duration in seconds or -1 if the implicit is 
     *   unknown (indefinite?). 
     */
    public float getImplicitDuration();

}

