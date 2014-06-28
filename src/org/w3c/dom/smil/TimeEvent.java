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

import org.w3c.dom.events.Event;
import org.w3c.dom.views.AbstractView;

/**
 *  The <code>TimeEvent</code> interface provides specific contextual 
 * information associated with Time events. 
 */
public interface TimeEvent extends Event {
    /**
     *  The <code>view</code> attribute identifies the 
     * <code>AbstractView</code> from which the event was generated. 
     */
    public AbstractView getView();

    /**
     *  Specifies some detail information about the <code>Event</code> , 
     * depending on the type of event. 
     */
    public int getDetail();

    /**
     *  The <code>initTimeEvent</code> method is used to initialize the value 
     * of a <code>TimeEvent</code> created through the 
     * <code>DocumentEvent</code> interface.  This method may only be called 
     * before the <code>TimeEvent</code> has been dispatched via the 
     * <code>dispatchEvent</code> method, though it may be called multiple 
     * times during that phase if necessary.  If called multiple times, the 
     * final invocation takes precedence. 
     * @param typeArg  Specifies the event type.
     * @param viewArg  Specifies the <code>Event</code> 's 
     *   <code>AbstractView</code> .
     * @param detailArg  Specifies the <code>Event</code> 's detail.
     */
    public void initTimeEvent(String typeArg, 
                              AbstractView viewArg, 
                              int detailArg);

}

