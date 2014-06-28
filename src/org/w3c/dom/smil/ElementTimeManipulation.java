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
 *  This interface support use-cases commonly associated with animation.  
 * "accelerate" and "decelerate" are float values in the timing draft and 
 * percentage values even in this draft if both of them represent a 
 * percentage. 
 */
public interface ElementTimeManipulation {
    /**
     *  Defines the playback  speed of element time. The value is specified as 
     * a multiple of normal (parent time container) play speed.  Legal values 
     * are signed floating point values.  Zero values are not allowed.  The 
     * default is <code>1.0</code> (no modification of speed). 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public float getSpeed();
    public void setSpeed(float speed)
                            throws DOMException;

    /**
     *  The percentage value of the  simple acceleration of time for the 
     * element. Allowed values are from <code>0</code> to <code>100</code> . 
     * Default value is <code>0</code> (no acceleration). 
     * <br> The sum of the values for accelerate and decelerate must not exceed
     *  100. If it does, the deceleration value will be reduced to make the 
     * sum legal. 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public float getAccelerate();
    public void setAccelerate(float accelerate)
                            throws DOMException;

    /**
     *  The percentage value of the  simple decelerate of time for the 
     * element. Allowed values are from <code>0</code> to <code>100</code> . 
     * Default value is <code>0</code> (no deceleration). 
     * <br> The sum of the values for accelerate and decelerate must not exceed
     *  100. If it does, the deceleration value will be reduced to make the 
     * sum legal. 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public float getDecelerate();
    public void setDecelerate(float decelerate)
                            throws DOMException;

    /**
     *  The  autoReverse attribute controls the "play forwards then backwards" 
     * functionality. Default value is <code>false</code> . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public boolean getAutoReverse();
    public void setAutoReverse(boolean autoReverse)
                            throws DOMException;

}

