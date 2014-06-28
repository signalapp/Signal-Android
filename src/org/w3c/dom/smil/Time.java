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
 *  The <code>Time</code> interface is a datatype that represents times within 
 * the timegraph. A <code>Time</code> has a type, key values to describe the 
 * time, and a boolean to indicate whether the values are currently 
 * unresolved.  Still need to address the wallclock values. 
 */
public interface Time {
    /**
     *  A boolean indicating whether the current <code>Time</code> has been 
     * fully resolved to the document schedule.  Note that for this to be 
     * true, the current <code>Time</code> must be defined (not indefinite), 
     * the syncbase and all <code>Time</code> 's that the syncbase depends on 
     * must be defined (not indefinite), and the begin <code>Time</code> of 
     * all ascendent time containers of this element and all <code>Time</code>
     *  elements that this depends upon must be defined (not indefinite). 
     * <br> If this <code>Time</code> is based upon an event, this 
     * <code>Time</code> will only be resolved once the specified event has 
     * happened, subject to the constraints of the time container. 
     * <br> Note that this may change from true to false when the parent time 
     * container ends its simple duration (including when it repeats or 
     * restarts). 
     */
    public boolean getResolved();

    /**
     *  The clock value in seconds relative to the parent time container begin.
     *  This indicates the resolved time relationship to the parent time 
     * container.  This is only valid if resolved is true. 
     */
    public double getResolvedOffset();

    // TimeTypes
    public static final short SMIL_TIME_INDEFINITE      = 0;
    public static final short SMIL_TIME_OFFSET          = 1;
    public static final short SMIL_TIME_SYNC_BASED      = 2;
    public static final short SMIL_TIME_EVENT_BASED     = 3;
    public static final short SMIL_TIME_WALLCLOCK       = 4;
    public static final short SMIL_TIME_MEDIA_MARKER    = 5;

    /**
     *  A code representing the type of the underlying object, as defined 
     * above. 
     */
    public short getTimeType();

    /**
     *  The clock value in seconds relative to the syncbase or eventbase. 
     * Default value is <code>0</code> . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised on attempts to modify this 
     *   readonly attribute. 
     */
    public double getOffset();
    public void setOffset(double offset)
                                      throws DOMException;

    /**
     *  The base element for a sync-based or event-based time. 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised on attempts to modify this 
     *   readonly attribute. 
     */
    public Element getBaseElement();
    public void setBaseElement(Element baseElement)
                                      throws DOMException;

    /**
     *  If <code>true</code> , indicates that a sync-based time is relative to 
     * the begin of the baseElement.  If <code>false</code> , indicates that a
     *  sync-based time is relative to the active end of the baseElement. 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised on attempts to modify this 
     *   readonly attribute. 
     */
    public boolean getBaseBegin();
    public void setBaseBegin(boolean baseBegin)
                                      throws DOMException;

    /**
     *  The name of the event for an event-based time. Default value is 
     * <code>null</code> . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised on attempts to modify this 
     *   readonly attribute. 
     */
    public String getEvent();
    public void setEvent(String event)
                                      throws DOMException;

    /**
     *  The name of the marker from the media element, for media marker times. 
     * Default value is <code>null</code> . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised on attempts to modify this 
     *   readonly attribute. 
     */
    public String getMarker();
    public void setMarker(String marker)
                                      throws DOMException;

}

