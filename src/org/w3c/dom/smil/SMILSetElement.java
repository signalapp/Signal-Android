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

/**
 *  This interface represents the  set element. 
 */
public interface SMILSetElement extends ElementTimeControl, ElementTime, ElementTargetAttributes, SMILElement {
    /**
     *  Specifies the value for the attribute during the duration of this 
     * element. 
     */
    public String getTo();
    public void setTo(String to);

}

