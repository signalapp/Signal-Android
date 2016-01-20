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

package org.privatechats.securesms.dom;

import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.TypeInfo;

public class AttrImpl extends NodeImpl implements Attr {
    private String mName;
    private String mValue;
	
	/*
     * Internal methods
     */
	
	protected AttrImpl(DocumentImpl owner, String name) {
		super(owner);
		mName = name;
	}
	
    /*
     * Attr Interface Methods
     */

	public String getName() {
		return mName;
	}

	public Element getOwnerElement() {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean getSpecified() {
		return mValue != null;
	}

	public String getValue() {
		return mValue;
	}

	// Instead of setting a <code>Text></code> with the content of the
	// String value as defined in the specs,  we directly set here the
	// internal mValue member.
	public void setValue(String value) throws DOMException {
		mValue = value;
	}
	
    /*
     * Node Interface Methods
     */

	@Override
	public String getNodeName() {
		return mName;
	}

	@Override
	public short getNodeType() {
		return Node.ATTRIBUTE_NODE;
	}
	
	@Override
	public Node getParentNode() {
		return null;
	}
	
	@Override 
	public Node getPreviousSibling() {
		return null;
	}
	
	@Override
	public Node getNextSibling() {
		return null;
	}
	
	@Override
	public void setNodeValue(String nodeValue) throws DOMException {
        setValue(nodeValue);
    }

    public TypeInfo getSchemaTypeInfo() {
        return null;
    }

    public boolean isId() {
        return false;
    }
}
