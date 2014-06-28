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
 *  The <code>TimeList</code> interface provides the abstraction of an ordered 
 * collection of times, without defining or constraining how this collection 
 * is implemented.
 * <p> The items in the <code>TimeList</code> are accessible via an integral 
 * index, starting from 0. 
 */
public interface TimeList {
    /**
     *  Returns the <code>index</code> th item in the collection. If 
     * <code>index</code> is greater than or equal to the number of times in 
     * the list, this returns <code>null</code> .
     * @param index  Index into the collection.
     * @return  The time at the <code>index</code> th position in the 
     *   <code>TimeList</code> , or <code>null</code> if that is not a valid 
     *   index.
     */
    public Time item(int index);

    /**
     *  The number of times in the list. The range of valid child time indices 
     * is 0 to <code>length-1</code> inclusive. 
     */
    public int getLength();

}

