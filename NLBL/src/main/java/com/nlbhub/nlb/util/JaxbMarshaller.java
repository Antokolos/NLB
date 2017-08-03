/**
 * @(#)JaxbMarshaller.java
 *
 * This file is part of the Non-Linear Book project.
 * Copyright (c) 2012-2014 Anton P. Kolosov
 * Authors: Anton P. Kolosov, et al.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * ANTON P. KOLOSOV. ANTON P. KOLOSOV DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the Non-Linear Book software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Anton P. Kolosov at this
 * address: antokolos@gmail.com
 *
 * Copyright (c) 2014 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.util;

import com.nlbhub.nlb.exception.NLBJAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The JaxbMarshaller class creates javax.xml.bind.Marshaller and javax.xml.bind.Unmarshaller instances for the
 * specified list of classes.
 *
 * @author Anton P. Kolosov
 * @version 1.0 6/13/12
 */
public class JaxbMarshaller {
    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = (
            LoggerFactory.getLogger(JaxbMarshaller.class)
    );
    /**
     * javax.xml.bind.Marshaller instance.
     */
    private Marshaller m_jaxbMarshaller;
    /**
     * javax.xml.bind.Unmarshaller instance.
     */
    private Unmarshaller m_jaxbUnmarshaller;

    /**
     * Constructor.
     *
     * @param classes list of classes for which javax.xml.bind.Marshaller instance will be created
     */
    public JaxbMarshaller(Class... classes) {
        if (classes == null) {
            LOGGER.error("An attempt were made to create JaxbMarshaller with classes = null");
            throw new IllegalArgumentException("Parameter 'classes' cannot be null");
        }
        try {
            JAXBContext jaxbContext = (
                    JAXBContext.newInstance(
                            classes
                    )
            );
            m_jaxbMarshaller = jaxbContext.createMarshaller();
            m_jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            // output pretty printed
            m_jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        } catch (JAXBException e) {
            StringBuilder sb = new StringBuilder();
            for (Class c : classes) {
                sb.append(c.getName()).append("; ");
            }
            LOGGER.error(
                    "Error creating javax.xml.bind.Marshaller instance"
                            + " for the following list of classes: " + sb.toString(),
                    e
            );
        }
    }

    /**
     * Marshals the object to the output stream.
     *
     * @param objectToMarshal object to marshal
     * @param outputStream    output stream into which objectToMarshal will be marshaled
     * @param isFragment      if <code>true</code>, <? ... ?> string in the beginning will NOT be
     *                        generated (i.e. it is not complete XML document, but only its fragment),
     *                        if <code>false</code>, it will be generated
     * @throws JAXBException on marshaling errors
     */
    public void marshal(
            final Object objectToMarshal,
            final OutputStream outputStream,
            final boolean isFragment
    ) throws NLBJAXBException {
        try {
            m_jaxbMarshaller.setProperty(Marshaller.JAXB_FRAGMENT, isFragment);
            m_jaxbMarshaller.marshal(objectToMarshal, outputStream);
        } catch (JAXBException e) {
            throw new NLBJAXBException("Exception when marshalling", e);
        }
    }

    /**
     * Unmarshals the object from input stream.
     *
     * @param inputStream input stream with object content
     * @return unmarshalled instance
     * @throws NLBJAXBException
     */
    public Object unmarshal(final InputStream inputStream) throws NLBJAXBException {
        try {
            return m_jaxbUnmarshaller.unmarshal(inputStream);
        } catch (JAXBException e) {
            throw new NLBJAXBException("Exception when unmarshalling", e);
        }
    }

    /**
     * Converts object to Node instance.
     *
     * @param object object to convert
     * @throws NLBJAXBException on marshaling errors or DocumentBuilder creation errors
     */
    public Document getAsDocument(
            final Object object,
            final boolean isFragment
    ) throws NLBJAXBException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document document = db.newDocument();
            m_jaxbMarshaller.setProperty(Marshaller.JAXB_FRAGMENT, isFragment);
            m_jaxbMarshaller.marshal(object, document);
            return document;
        } catch (JAXBException | ParserConfigurationException e) {
            throw new NLBJAXBException("Exception when marshalling", e);
        }
    }
}
