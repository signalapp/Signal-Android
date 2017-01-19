/*
 * Copyright (C) 2007 Esmertec AG.
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.thoughtcrime.securesms.dom;

import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.w3c.dom.TypeInfo;

public class ElementImpl extends NodeImpl implements Element {
    private String mTagName;
    private NamedNodeMap mAttributes = new NamedNodeMapImpl();

    /*
     * Internal methods
     */

    protected ElementImpl(DocumentImpl owner, String tagName) {
        super(owner);
        mTagName = tagName;
    }

    /*
     *  Element Interface methods
     */

    public String getAttribute(String name) {
        Attr attrNode = getAttributeNode(name);
        String attrValue = "";
        if (attrNode != null) {
            attrValue = attrNode.getValue();
        }
        return attrValue;
    }

    public String getAttributeNS(String namespaceURI, String localName) {
        // TODO Auto-generated method stub
        return null;
    }

    public Attr getAttributeNode(String name) {
        return (Attr)mAttributes.getNamedItem(name);
    }

    public Attr getAttributeNodeNS(String namespaceURI, String localName) {
        // TODO Auto-generated method stub
        return null;
    }

    public NodeList getElementsByTagName(String name) {
        return new NodeListImpl(this, name, true);
    }

    public NodeList getElementsByTagNameNS(String namespaceURI, String localName) {
        // TODO Auto-generated method stub
        return null;
    }

    public String getTagName() {
        return mTagName;
    }

    public boolean hasAttribute(String name) {
        return (getAttributeNode(name) != null);
    }

    public boolean hasAttributeNS(String namespaceURI, String localName) {
        // TODO Auto-generated method stub
        return false;
    }

    public void removeAttribute(String name) throws DOMException {
        // TODO Auto-generated method stub

    }

    public void removeAttributeNS(String namespaceURI, String localName)
            throws DOMException {
        // TODO Auto-generated method stub

    }

    public Attr removeAttributeNode(Attr oldAttr) throws DOMException {
        // TODO Auto-generated method stub
        return null;
    }

    public void setAttribute(String name, String value) throws DOMException {
        Attr attribute = getAttributeNode(name);
        if (attribute == null) {
            attribute = mOwnerDocument.createAttribute(name);
        }
        attribute.setNodeValue(value);
        mAttributes.setNamedItem(attribute);
    }

    public void setAttributeNS(String namespaceURI, String qualifiedName,
            String value) throws DOMException {
        // TODO Auto-generated method stub

    }

    public Attr setAttributeNode(Attr newAttr) throws DOMException {
        // TODO Auto-generated method stub
        return null;
    }

    public Attr setAttributeNodeNS(Attr newAttr) throws DOMException {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * Node Interface methods
     */

    @Override
    public short getNodeType() {
        return ELEMENT_NODE;
    }

    @Override
    public String getNodeName() {
        // The value of nodeName is tagName when Node is an Element
        return mTagName;
    }

    @Override
    public NamedNodeMap getAttributes() {
        return mAttributes;
    }

    @Override
    public boolean hasAttributes() {
        return (mAttributes.getLength() > 0);
    }

    public TypeInfo getSchemaTypeInfo() {
        return null;
    }

    public void setIdAttribute(String name, boolean isId) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, null);
    }

    public void setIdAttributeNS(String namespaceURI, String localName,
            boolean isId) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, null);
    }

    public void setIdAttributeNode(Attr idAttr, boolean isId)
            throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, null);
    }
}
