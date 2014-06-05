/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nlbhub.nlb.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.nlbhub.nlb.exception.NLBJAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

/**
 * The JaxbMarshaller class creates javax.xml.bind.Marshaller instance for the specified list of
 * classes.
 *
 * @author Anton P. Kolosov
 * @version 1.0 6/13/12
 */
public class JaxbMarshaller {
    /** Logger for this class. */
    private static final Logger LOGGER = (
        LoggerFactory.getLogger(JaxbMarshaller.class)
    );
    /** javax.xml.bind.Marshaller instance. */
    private Marshaller m_jaxbMarshaller;
    
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
     * @param outputStream output stream into which objectToMarshal will be marshaled
     * @param isFragment if <code>true</code>, <? ... ?> string in the beginning will NOT be
     * generated (i.e. it is not complete XML document, but only its fragment),
     * if <code>false</code>, it will be generated
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
     * Converts object to Node instance.
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
