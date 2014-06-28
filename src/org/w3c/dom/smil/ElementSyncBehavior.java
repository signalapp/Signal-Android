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
 *  The synchronization behavior extension. 
 */
public interface ElementSyncBehavior {
    /**
     *  The runtime synchronization behavior for an element. 
     */
    public String getSyncBehavior();

    /**
     *  The sync tolerance for the associated element. It has an effect only if
     *  the element has <code>syncBehavior="locked"</code> . 
     */
    public float getSyncTolerance();

    /**
     *  Defines the default value for the runtime synchronization behavior for 
     * an element, and all descendents. 
     */
    public String getDefaultSyncBehavior();

    /**
     *  Defines the default value for the sync tolerance for an element, and 
     * all descendents. 
     */
    public float getDefaultSyncTolerance();

    /**
     *  If set to true, forces the time container playback to sync to this 
     * element.  
     */
    public boolean getSyncMaster();

}

