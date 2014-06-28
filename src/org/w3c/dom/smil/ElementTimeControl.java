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
 */
public interface ElementTimeControl {
    /**
     *  Causes this element to begin the local timeline (subject to sync 
     * constraints).  
     * @return  <code>true</code> if the method call was successful and the 
     *   element was begun. <code>false</code> if the method call failed. 
     *   Possible reasons for failure include:  The element doesn't support 
     *   the <code>beginElement</code> method. (the <code>begin</code> 
     *   attribute is not set to <code>"indefinite"</code> )  The element is 
     *   already active and can't be restart when it is active. (the 
     *   <code>restart</code> attribute is set to <code>"whenNotActive"</code>
     *    )  The element is active or has been active and can't be restart. 
     *   (the <code>restart</code> attribute is set to <code>"never"</code> ).
     *    
     * @exception DOMException
     *    SYNTAX_ERR: The element was not defined with the appropriate syntax 
     *   to allow <code>beginElement</code> calls. 
     */
    public boolean beginElement()
                                throws DOMException;

    /**
     *  Causes this element to begin the local timeline (subject to sync 
     * constraints), at the passed offset from the current time when the 
     * method is called. If the offset is &gt;= 0, the semantics are 
     * equivalent to an event-base begin with the specified offset. If the 
     * offset is &lt; 0, the semantics are equivalent to beginElement(), but 
     * the element active duration is evaluated as though the element had 
     * begun at the passed (negative) offset from the current time when the 
     * method is called. 
     * @param offset  The offset in seconds at which to begin the element. 
     * @return  <code>true</code> if the method call was successful and the 
     *   element was begun. <code>false</code> if the method call failed. 
     *   Possible reasons for failure include:  The element doesn't support 
     *   the <code>beginElementAt</code> method. (the <code>begin</code> 
     *   attribute is not set to <code>"indefinite"</code> )  The element is 
     *   already active and can't be restart when it is active. (the 
     *   <code>restart</code> attribute is set to <code>"whenNotActive"</code>
     *    )  The element is active or has been active and can't be restart. 
     *   (the <code>restart</code> attribute is set to <code>"never"</code> ).
     *    
     * @exception DOMException
     *    SYNTAX_ERR: The element was not defined with the appropriate syntax 
     *   to allow <code>beginElementAt</code> calls. 
     */
    public boolean beginElementAt(float offset)
                                  throws DOMException;

    /**
     *  Causes this element to end the local timeline (subject to sync 
     * constraints). 
     * @return  <code>true</code> if the method call was successful and the 
     *   element was ended. <code>false</code> if method call failed. 
     *   Possible reasons for failure include:  The element doesn't support 
     *   the <code>endElement</code> method. (the <code>end</code> attribute 
     *   is not set to <code>"indefinite"</code> )  The element is not active.
     *    
     * @exception DOMException
     *    SYNTAX_ERR: The element was not defined with the appropriate syntax 
     *   to allow <code>endElement</code> calls. 
     */
    public boolean endElement()
                              throws DOMException;

    /**
     *  Causes this element to end the local timeline (subject to sync 
     * constraints) at the specified offset from the current time when the 
     * method is called. 
     * @param offset  The offset in seconds at which to end the element. Must 
     *   be &gt;= 0. 
     * @return  <code>true</code> if the method call was successful and the 
     *   element was ended. <code>false</code> if method call failed. 
     *   Possible reasons for failure include:  The element doesn't support 
     *   the <code>endElementAt</code> method. (the <code>end</code> 
     *   attribute is not set to <code>"indefinite"</code> )  The element is 
     *   not active. 
     * @exception DOMException
     *    SYNTAX_ERR: The element was not defined with the appropriate syntax 
     *   to allow <code>endElementAt</code> calls. 
     */
    public boolean endElementAt(float offset)
                                throws DOMException;

}

