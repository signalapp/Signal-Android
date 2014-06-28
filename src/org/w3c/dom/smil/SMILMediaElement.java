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
 *  Declares media content. 
 */
public interface SMILMediaElement extends ElementTime, SMILElement {
    /**
     *  See the  abstract attribute from  . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getAbstractAttr();
    public void setAbstractAttr(String abstractAttr)
                              throws DOMException;

    /**
     *  See the  alt attribute from  . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getAlt();
    public void setAlt(String alt)
                              throws DOMException;

    /**
     *  See the  author attribute from  . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getAuthor();
    public void setAuthor(String author)
                              throws DOMException;

    /**
     *  See the  clipBegin attribute from  . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getClipBegin();
    public void setClipBegin(String clipBegin)
                              throws DOMException;

    /**
     *  See the  clipEnd attribute from  . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getClipEnd();
    public void setClipEnd(String clipEnd)
                              throws DOMException;

    /**
     *  See the  copyright attribute from  . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getCopyright();
    public void setCopyright(String copyright)
                              throws DOMException;

    /**
     *  See the  longdesc attribute from  . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getLongdesc();
    public void setLongdesc(String longdesc)
                              throws DOMException;

    /**
     *  See the  port attribute from  . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getPort();
    public void setPort(String port)
                              throws DOMException;

    /**
     *  See the  readIndex attribute from  . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getReadIndex();
    public void setReadIndex(String readIndex)
                              throws DOMException;

    /**
     *  See the  rtpformat attribute from  . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getRtpformat();
    public void setRtpformat(String rtpformat)
                              throws DOMException;

    /**
     *  See the  src attribute from  . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getSrc();
    public void setSrc(String src)
                              throws DOMException;

    /**
     *  See the  stripRepeat attribute from  . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getStripRepeat();
    public void setStripRepeat(String stripRepeat)
                              throws DOMException;

    /**
     *  See the  title attribute from  . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getTitle();
    public void setTitle(String title)
                              throws DOMException;

    /**
     *  See the  transport attribute from  . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getTransport();
    public void setTransport(String transport)
                              throws DOMException;

    /**
     *  See the  type attribute from  . 
     * @exception DOMException
     *    NO_MODIFICATION_ALLOWED_ERR: Raised if this attribute is readonly. 
     */
    public String getType();
    public void setType(String type)
                              throws DOMException;

}

