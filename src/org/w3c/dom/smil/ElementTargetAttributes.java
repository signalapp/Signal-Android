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
 *  This interface define the set of animation target extensions. 
 */
public interface ElementTargetAttributes {
    /**
     *  The name of the target attribute. 
     */
    public String getAttributeName();
    public void setAttributeName(String attributeName);

    // attributeTypes
    public static final short ATTRIBUTE_TYPE_AUTO       = 0;
    public static final short ATTRIBUTE_TYPE_CSS        = 1;
    public static final short ATTRIBUTE_TYPE_XML        = 2;

    /**
     *  A code representing the value of the  attributeType attribute, as 
     * defined above. Default value is <code>ATTRIBUTE_TYPE_CODE</code> . 
     */
    public short getAttributeType();
    public void setAttributeType(short attributeType);

}

