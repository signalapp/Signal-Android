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
 *  This interface present the <code>animationMotion</code> element in SMIL. 
 */
public interface SMILAnimateMotionElement extends SMILAnimateElement {
    /**
     *  Specifies the curve that describes the attribute value as a function 
     * of time.  Check with the SVG spec for better support 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getPath();
    public void setPath(String path)
                                throws DOMException;

    /**
     *  Specifies the origin of motion for the animation. 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getOrigin();
    public void setOrigin(String origin)
                                throws DOMException;

}

