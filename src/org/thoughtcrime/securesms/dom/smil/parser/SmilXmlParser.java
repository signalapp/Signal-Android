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

package org.thoughtcrime.securesms.dom.smil.parser;

import java.io.IOException;
import java.io.InputStream;

import org.w3c.dom.smil.SMILDocument;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import ws.com.google.android.mms.MmsException;

public class SmilXmlParser {
    private XMLReader mXmlReader;
    private SmilContentHandler mContentHandler;

    public SmilXmlParser() throws MmsException {
        //FIXME: Now we don't have the SAXParser wrapped inside,
        //       use the Driver class temporarily.
        System.setProperty("org.xml.sax.driver", "org.xmlpull.v1.sax2.Driver");

        try {
            mXmlReader = XMLReaderFactory.createXMLReader();
            mContentHandler = new SmilContentHandler();
            mXmlReader.setContentHandler(mContentHandler);
        } catch (SAXException e) {
            throw new MmsException(e);
        }
    }

    public SMILDocument parse(InputStream in) throws IOException, SAXException {
        mContentHandler.reset();

        mXmlReader.parse(new InputSource(in));

        SMILDocument doc = mContentHandler.getSmilDocument();
        validateDocument(doc);

        return doc;
    }

    private void validateDocument(SMILDocument doc) {
        /*
         * Calling getBody() will create "smil", "head", and "body" elements if they
         * are not present. It will also initialize the SequentialTimeElementContainer
         * member of SMILDocument, which could not be set on creation of the document.
         * @see org.thoughtcrime.securesms.dom.smil.SmilDocumentImpl#getBody()
         */
        doc.getBody();

        /*
         * Calling getLayout() will create "layout" element if it is not present.
         * @see org.thoughtcrime.securesms.dom.smil.SmilDocumentImpl#getLayout()
         */
        doc.getLayout();
    }
}
