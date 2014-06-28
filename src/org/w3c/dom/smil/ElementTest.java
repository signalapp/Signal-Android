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
 *  Defines the test attributes interface. See the  Test attributes definition 
 * . 
 */
public interface ElementTest {
    /**
     *  The  systemBitrate value. 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public int getSystemBitrate();
    public void setSystemBitrate(int systemBitrate)
                                      throws DOMException;

    /**
     *  The  systemCaptions value. 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public boolean getSystemCaptions();
    public void setSystemCaptions(boolean systemCaptions)
                                      throws DOMException;

    /**
     *  The  systemLanguage value. 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getSystemLanguage();
    public void setSystemLanguage(String systemLanguage)
                                      throws DOMException;

    /**
     *  The result of the evaluation of the  systemRequired attribute. 
     */
    public boolean getSystemRequired();

    /**
     *  The result of the evaluation of the  systemScreenSize attribute. 
     */
    public boolean getSystemScreenSize();

    /**
     *  The result of the evaluation of the  systemScreenDepth attribute. 
     */
    public boolean getSystemScreenDepth();

    /**
     *  The value of the  systemOverdubOrSubtitle attribute. 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getSystemOverdubOrSubtitle();
    public void setSystemOverdubOrSubtitle(String systemOverdubOrSubtitle)
                                      throws DOMException;

    /**
     *  The value of the  systemAudioDesc attribute. 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public boolean getSystemAudioDesc();
    public void setSystemAudioDesc(boolean systemAudioDesc)
                                      throws DOMException;

}

