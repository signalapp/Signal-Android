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

import org.w3c.dom.Element;

/**
 *  Defines a block of content control. See the  switch element definition . 
 */
public interface SMILSwitchElement extends SMILElement {
    /**
     *  Returns the slected element at runtime. <code>null</code> if the 
     * selected element is not yet available. 
     * @return  The selected <code>Element</code> for thisd <code>switch</code>
     *    element. 
     */
    public Element getSelectedElement();

}

