/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package org.switchyard.transform.ootb.xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.namespace.QName;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

/**
 * Basic DOM transformations.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class BasicDOMTransformer extends AbstractDOMTransformer {

    private static final QName TYPE_DOCUMENT     = toMessageType(Document.class);
    private static final QName TYPE_ELEMENT      = toMessageType(Element.class);
    private static final QName TYPE_STRING       = toMessageType(String.class);
    private static final QName TYPE_CHAR_ARRAY   = toMessageType(char[].class);

    @Override
    public Object transform(Object from) {
        if (from instanceof Node) {
            return transformFromDOMNode((Node) from);
        } else if (from instanceof String) {
            return transformFromInputSource(new InputSource(new StringReader((String) from)));
        } else if (from instanceof char[]) {
            return transformFromInputSource(new InputSource(new StringReader(new String((char[]) from))));
        } else if (from instanceof byte[]) {
            return transformFromInputSource(new InputSource(new ByteArrayInputStream((byte[]) from)));
        } else if (from instanceof Reader) {
            return transformFromInputSource(new InputSource((Reader) from));
        } else if (from instanceof InputStream) {
            return transformFromInputSource(new InputSource((InputStream) from));
        } else if (from instanceof InputSource) {
            return transformFromInputSource((InputSource) from);
        }

        return null;
    }

    private Object transformFromDOMNode(Node from) {
        if(getTo().equals(TYPE_DOCUMENT)) {
            return from.getOwnerDocument();
        }
        if(getTo().equals(TYPE_ELEMENT)) {
            if(from.getNodeType() == Node.ELEMENT_NODE) {
                return from;
            }
            if(from.getNodeType() == Node.ATTRIBUTE_NODE) {
                return from.getParentNode();
            }
            if(from.getNodeType() == Node.DOCUMENT_NODE) {
                return ((Document)from).getDocumentElement();
            }
        }
        if(getTo().equals(TYPE_STRING)) {
            return serialize(from);
        }
        if(getTo().equals(TYPE_CHAR_ARRAY)) {
            return serialize(from).toCharArray();
        }

        return null;
    }

    private Object transformFromInputSource(InputSource from) {
        Document document = parse(from);

        if(getTo().equals(TYPE_DOCUMENT)) {
            return document;
        }
        if(getTo().equals(TYPE_ELEMENT)) {
            return document.getDocumentElement();
        }

        return null;
    }
}
