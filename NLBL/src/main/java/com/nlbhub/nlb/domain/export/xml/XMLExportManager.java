/**
 * @(#)XMLExportManager.java
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
 * Copyright (c) 2013 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.domain.export.xml;

import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.export.ExportManager;
import com.nlbhub.nlb.domain.export.NLBBuildingBlocks;
import com.nlbhub.nlb.exception.NLBConsistencyException;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.exception.NLBJAXBException;
import com.nlbhub.nlb.util.JaxbMarshaller;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;

/**
 * The XMLExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 6/01/14
 */
public abstract class XMLExportManager extends ExportManager {

    protected XMLExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    @Override
    public void exportToFile(File targetFile) throws NLBExportException {
        OutputStreamWriter writer = null;
        FileOutputStream stream = null;
        try {
            try {
                NLBBuildingBlocks nlbBlocks = createNLBBuildingBlocks();
                JaxbMarshaller marshaller = createMarshaller();

                /*
                We can simply do the following:
                outputStream = new FileOutputStream(targetFile);
                marshaller.marshal(createRootObject(nlbBlocks), outputStream, false);
                but if we want to enable CDATA sections, we should do the following stuff
                (see http://stackoverflow.com/questions/7536973/jaxb-marshalling-and-unmarshalling-cdata)
                 */
                Document document = marshaller.getAsDocument(createRootObject(nlbBlocks), false);

                // Transform the DOM to the output stream
                // TransformerFactory is not thread-safe
                stream = new FileOutputStream(targetFile);
                writer = new OutputStreamWriter(stream, getEncoding());
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer nullTransformer = transformerFactory.newTransformer();
                nullTransformer.setOutputProperty(OutputKeys.INDENT, "yes");
                nullTransformer.setOutputProperty(
                        OutputKeys.CDATA_SECTION_ELEMENTS,
                        getCDataSectionElements()
                );
                nullTransformer.transform(new DOMSource(document), new StreamResult(writer));
            } finally {
                if (writer != null) {
                    writer.close();
                }
                if (stream != null) {
                    stream.close();
                }
            }
        } catch (TransformerException | NLBConsistencyException | IOException | NLBJAXBException e) {
            throw new NLBExportException("Error while converting NLB to XML", e);
        }
    }

    protected abstract String getCDataSectionElements();

    protected abstract JaxbMarshaller createMarshaller();

    protected abstract Object createRootObject(NLBBuildingBlocks nlbBlocks);
}
