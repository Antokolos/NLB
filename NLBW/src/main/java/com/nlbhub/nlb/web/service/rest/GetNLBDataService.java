/**
 * @(#)GetNLBDataService.java
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
package com.nlbhub.nlb.web.service.rest;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.Link;
import com.nlbhub.nlb.api.Page;
import com.nlbhub.nlb.domain.*;
import com.nlbhub.nlb.exception.DecisionException;
import com.nlbhub.nlb.exception.NLBConsistencyException;
import com.nlbhub.nlb.exception.NLBIOException;
import com.nlbhub.nlb.exception.NLBVCSException;
import com.nlbhub.nlb.util.JaxbMarshaller;
import com.nlbhub.nlb.util.StringHelper;
import com.nlbhub.user.domain.DecisionPoint;
import com.nlbhub.user.domain.History;

import javax.script.ScriptException;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The GetNLBDataService class
 *
 * @author Anton P. Kolosov
 * @version 1.0 11/13/13
 */
@Path("{bookId}")
public class GetNLBDataService {
    public static String s_nlbLibraryRoot = "/D:/work/Azartox/NLB/books/";
    private static final Map<String, NonLinearBookImpl> m_nlbCache = new HashMap<>();
    private static final JaxbMarshaller PAGE_MARSHALLER = (
        new JaxbMarshaller(
            PageImpl.class,
            LinkImpl.class,
            AbstractModifyingItem.class,
            AbstractIdentifiableItem.class,
            ModificationImpl.class
        )
    );
    private static final JaxbMarshaller DECISIONS_MARSHALLER = (
        new JaxbMarshaller(
            History.class,
            DecisionPoint.class
        )
    );
    private static History s_history = new History();

    private class ReturnBookIdAndModulePage {
        private String m_bookId;
        private String m_modulePageId;

        private String getBookId() {
            return m_bookId;
        }

        private void setBookId(String bookId) {
            m_bookId = bookId;
        }

        private String getModulePageId() {
            return m_modulePageId;
        }

        private void setModulePageId(String modulePageId) {
            m_modulePageId = modulePageId;
        }
    }

    private class ModuleData {
        private String m_mainNLBId;
        private NonLinearBookImpl m_module;
        private int m_moduleDepth;

        private NonLinearBookImpl getModule() {
            return m_module;
        }

        private void setModule(NonLinearBookImpl module) {
            m_module = module;
        }

        private int getModuleDepth() {
            return m_moduleDepth;
        }

        private void setModuleDepth(int moduleDepth) {
            m_moduleDepth = moduleDepth;
        }

        private String getMainNLBId() {
            return m_mainNLBId;
        }

        private void setMainNLBId(String mainNLBId) {
            m_mainNLBId = mainNLBId;
        }
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("start")
    public Response start(
        @PathParam("bookId") final PathSegment bookId
    ) {
        s_history.clear();
        return getStartPointData(bookId, null, null);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getStartPointData(
        @PathParam("bookId") final PathSegment bookId,
        @QueryParam("rollback") final Boolean rollback,
        @QueryParam("visit-count") final Integer visitCount
    ) {
        Response response;
        try {
            NonLinearBookImpl mainNLB = getNLBFromCache(bookId.getPath());
            // Now get required module
            ModuleData moduleData = getNonLinearBookModuleData(bookId, mainNLB);
            final DecisionPoint decisionPointToBeMade = (
                new DecisionPoint(bookId.toString(), moduleData.getModule().getStartPoint())
            );
            s_history.suggestDecisionPointToBeMade(
                decisionPointToBeMade,
                (rollback != null) ? rollback : false,
                (visitCount != null) ? visitCount : History.DO_NOT_USE_VISIT_COUNT
            );
            ReturnBookIdAndModulePage returnBookIdAndModulePage = (
                getReturnLinkBookId(decisionPointToBeMade, moduleData)
            );
            final String normalLinkBookId = getNormalLinkBookId(decisionPointToBeMade);
            final String traversalLinkBookId = getTraversalLinkBookId(
                decisionPointToBeMade,
                moduleData,
                moduleData.getModule().getStartPoint()
            );
            final Page pageToBeVisited = (
                moduleData.getModule().getPageById(moduleData.getModule().getStartPoint())
            );
            addPossibleNextDecisions(
                pageToBeVisited,
                decisionPointToBeMade,
                returnBookIdAndModulePage,
                normalLinkBookId,
                traversalLinkBookId
            );
            response = generateResponse(
                bookId.getPath(),
                normalLinkBookId,
                traversalLinkBookId,
                returnBookIdAndModulePage,
                pageToBeVisited
            );
            s_history.makeDecision(
                !StringHelper.isEmpty(pageToBeVisited.getCaption())
                    ? pageToBeVisited.getCaption()
                    : pageToBeVisited.getId()
            );
            return response;
        } catch (NLBIOException ex) {
            Logger.getLogger(GetNLBDataService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NLBConsistencyException ex) {
            Logger.getLogger(GetNLBDataService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NLBVCSException ex) {
            Logger.getLogger(GetNLBDataService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (DecisionException ex) {
            Logger.getLogger(GetNLBDataService.class.getName()).log(Level.SEVERE, null, ex);
        }
        return Response.serverError().build();
    }

    private ModuleData getNonLinearBookModuleData(
        PathSegment bookId,
        NonLinearBookImpl mainNLB
    ) {
        ModuleData result = new ModuleData();
        NonLinearBookImpl module = mainNLB;
        MultivaluedMap<String, String> mvm = bookId.getMatrixParameters();
        int parentDepth = 0;
        for (Map.Entry<String, List<String>> entry : mvm.entrySet()) {
            int curParentDepth = Integer.parseInt(entry.getKey());
            if (curParentDepth > parentDepth) {
                parentDepth = curParentDepth;
                String modulePageId = entry.getValue().get(0);
                PageImpl pageImpl = module.getPageImplById(modulePageId);
                module = pageImpl.getModuleImpl();
            }
        }
        result.setModuleDepth(parentDepth);
        result.setModule(module);
        result.setMainNLBId(bookId.getPath());
        return result;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("page/{pageId}")
    public Response getPageData(
        @PathParam("bookId") final PathSegment bookId,
        @PathParam("pageId") final String pageId,
        @QueryParam("rollback") final Boolean rollback,
        @QueryParam("visit-count") final Integer visitCount
    ) {
        Response response;
        try {
            s_history.suggestDecisionPointToBeMade(
                new DecisionPoint(bookId.toString(), pageId),
                (rollback != null) ? rollback : false,
                (visitCount != null) ? visitCount : History.DO_NOT_USE_VISIT_COUNT
            );
            NonLinearBookImpl mainNLB = getNLBFromCache(bookId.getPath());
            // Now get required module
            ModuleData moduleData = getNonLinearBookModuleData(bookId, mainNLB);
            response = generateFilteredResponse(moduleData, pageId);
            Page pageToBeVisited = moduleData.getModule().getPageById(pageId);
            s_history.makeDecision(
                !StringHelper.isEmpty(pageToBeVisited.getCaption())
                    ? pageToBeVisited.getCaption()
                    : pageToBeVisited.getId()
            );
            return response;
        } catch (NLBIOException ex) {
            Logger.getLogger(GetNLBDataService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ScriptException ex) {
            Logger.getLogger(GetNLBDataService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NLBConsistencyException ex) {
            Logger.getLogger(GetNLBDataService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NLBVCSException ex) {
            Logger.getLogger(GetNLBDataService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (DecisionException ex) {
            Logger.getLogger(GetNLBDataService.class.getName()).log(Level.SEVERE, null, ex);
        }
        return Response.serverError().build();
    }

    private NonLinearBookImpl getNLBFromCache(
        String bookId
    ) throws NLBIOException, NLBConsistencyException, NLBVCSException {
        NonLinearBookImpl nlb;
        if (m_nlbCache.containsKey(bookId)) {
            nlb = m_nlbCache.get(bookId);
        } else {
            final String path = s_nlbLibraryRoot + bookId;
            File rootDir = new File(path);
            if (!rootDir.exists()) {
                throw new NLBIOException("Specified NLB root directory " + path + " does not exist");
            }
            nlb = new NonLinearBookImpl();
            nlb.load(path);
            m_nlbCache.put(bookId, nlb);
        }
        return nlb;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("link/{pageId}/{linkId}")
    public Response followLink(
        @PathParam("bookId") final PathSegment bookId,
        @PathParam("pageId") final String pageId,
        @PathParam("linkId") final String linkId,
        @QueryParam("rollback") final Boolean rollback,
        @QueryParam("visit-count") final Integer visitCount
    ) {
        Response response;
        try {
            s_history.suggestDecisionPointToBeMade(
                new DecisionPoint(bookId.toString(), pageId, linkId),
                (rollback != null) ? rollback : false,
                (visitCount != null) ? visitCount : History.DO_NOT_USE_VISIT_COUNT
            );
            NonLinearBookImpl mainNLB = getNLBFromCache(bookId.getPath());
            // Now get required module
            ModuleData moduleData = getNonLinearBookModuleData(bookId, mainNLB);
            final Link link = moduleData.getModule().getPageById(pageId).getLinkById(linkId);
            if (link != null) {
                response = generateFilteredResponse(moduleData, link.getTarget());
                s_history.makeDecision(link.getText());
            } else {
                response = Response.serverError().build();
            }
            return response;
        } catch (NLBIOException ex) {
            Logger.getLogger(GetNLBDataService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ScriptException ex) {
            Logger.getLogger(GetNLBDataService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NLBConsistencyException ex) {
            Logger.getLogger(GetNLBDataService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NLBVCSException ex) {
            Logger.getLogger(GetNLBDataService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (DecisionException ex) {
            Logger.getLogger(GetNLBDataService.class.getName()).log(Level.SEVERE, null, ex);
        }
        return Response.serverError().build();
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("history")
    public Response getHistory(
        @PathParam("bookId") final PathSegment bookId
    ) {
        StreamingOutput stream = new StreamingOutput() {
            public void write(OutputStream output) throws IOException, WebApplicationException {
                try {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    DECISIONS_MARSHALLER.marshal(s_history, outputStream, false);
                    ByteArrayInputStream inputStream = (
                        new ByteArrayInputStream(
                            outputStream
                                .toString(Constants.UNICODE_ENCODING)
                                .getBytes(Constants.UNICODE_ENCODING)
                        )
                    );
                    transform(
                        bookId.getPath(),
                        inputStream,
                        "xsl/history.xsl",
                        output
                    );
                } catch (Exception e) {
                    throw new WebApplicationException(e);
                }
            }
        };

        Response.ResponseBuilder builder = Response.ok(stream);
        builder.type(MediaType.TEXT_HTML);
        builder.status(Response.Status.OK);

        return builder.build();
    }

    public void transform(
        final String mainNLBId,
        final String normalLinkBookId,
        final String traversalLinkBookId,
        final ReturnBookIdAndModulePage returnBookIdAndModulePage,
        InputStream inputStream,
        String xslID,
        OutputStream outputStream
    ) throws TransformerException, TransformerConfigurationException {
        // Create a transform factory instance.
        TransformerFactory tfactory = TransformerFactory.newInstance();

        // Create a transformer for the stylesheet.
        Transformer transformer = tfactory.newTransformer(new StreamSource(new File(xslID)));
        transformer.setParameter("mainNLBId", mainNLBId);
        transformer.setParameter("normalLinkBookId", normalLinkBookId);
        transformer.setParameter("traversalLinkBookId", traversalLinkBookId);
        transformer.setParameter("returnLinkBookId", returnBookIdAndModulePage.getBookId());
        transformer.setParameter("returnModulePageId", returnBookIdAndModulePage.getModulePageId());

        // Transform the source XML to System.out.
        transformer.transform(
            new StreamSource(inputStream),
            new StreamResult(outputStream)
        );
    }

    public void transform(
        String mainNLBId,
        InputStream inputStream,
        String xslID,
        OutputStream outputStream
    ) throws TransformerException, TransformerConfigurationException {
        // Create a transform factory instance.
        TransformerFactory tfactory = TransformerFactory.newInstance();

        // Create a transformer for the stylesheet.
        Transformer transformer = tfactory.newTransformer(new StreamSource(new File(xslID)));
        transformer.setParameter("mainNLBId", mainNLBId);

        // Transform the source XML to System.out.
        transformer.transform(
            new StreamSource(inputStream),
            new StreamResult(outputStream)
        );
    }

    /**
     * Generates web service response using process instance.
     *
     * @return Initialized javax.ws.rs.core.Response instance containing information about
     * request results
     */
    protected Response generateResponse(
        final String mainNLBId,
        final String normalLinkBookId,
        final String traversalLinkBookId,
        final ReturnBookIdAndModulePage returnBookIdAndModulePage,
        final Page page
    ) {
        StreamingOutput stream = new StreamingOutput() {
            public void write(OutputStream output) throws IOException, WebApplicationException {
                try {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    PAGE_MARSHALLER.marshal(page, outputStream, false);
                    ByteArrayInputStream inputStream = (
                        new ByteArrayInputStream(
                            outputStream
                                .toString(Constants.UNICODE_ENCODING)
                                .getBytes(Constants.UNICODE_ENCODING)
                        )
                    );
                    transform(
                        mainNLBId,
                        normalLinkBookId,
                        traversalLinkBookId,
                        returnBookIdAndModulePage,
                        inputStream,
                        "xsl/page.xsl",
                        output
                    );
                } catch (Exception e) {
                    throw new WebApplicationException(e);
                }
            }
        };

        Response.ResponseBuilder builder = Response.ok(stream);
        builder.type(MediaType.TEXT_HTML);
        builder.status(Response.Status.OK);

        return builder.build();
    }

    /**
     * Generates web service response using process instance.
     *
     * @return Initialized javax.ws.rs.core.Response instance containing information about
     * request results
     */
    protected Response generateFilteredResponse(
        final ModuleData moduleData,
        final String pageId
    ) throws ScriptException {
        final PageImpl filteredPage = (
            moduleData.getModule().createFilteredPage(pageId, s_history)
        );

        final DecisionPoint decisionPointToBeMade = s_history.getDecisionPointToBeMade();
        ReturnBookIdAndModulePage returnBookIdAndPage = getReturnLinkBookId(
            decisionPointToBeMade, moduleData
        );
        final String normalLinkBookId = getNormalLinkBookId(decisionPointToBeMade);
        final String traversalLinkBookId = (
            getTraversalLinkBookId(decisionPointToBeMade, moduleData, pageId)
        );
        addPossibleNextDecisions(
            filteredPage,
            decisionPointToBeMade,
            returnBookIdAndPage,
            normalLinkBookId,
            traversalLinkBookId
        );
        return generateResponse(
            moduleData.getMainNLBId(),
            normalLinkBookId,
            traversalLinkBookId,
            returnBookIdAndPage,
            filteredPage
        );
    }

    private void addPossibleNextDecisions(
        Page pageToBeVisited,
        DecisionPoint decisionPointToBeMade,
        ReturnBookIdAndModulePage returnBookIdAndPage,
        String normalLinkBookId,
        String traversalLinkBookId
    ) {
        for (final Link link : pageToBeVisited.getLinks()) {
            // This code is equivalent to the code in page.xsl
            if (link.isTraversalLink()) {
                decisionPointToBeMade.addPossibleNextDecisionPoint(
                    new DecisionPoint(
                        traversalLinkBookId,
                        link.getTarget()
                    )
                );
            } else if (link.isReturnLink()) {
                if (StringHelper.isEmpty(pageToBeVisited.getReturnPageId())) {
                    decisionPointToBeMade.addPossibleNextDecisionPoint(
                        new DecisionPoint(
                            returnBookIdAndPage.getBookId(),
                            returnBookIdAndPage.getModulePageId()
                        )
                    );
                } else {
                    decisionPointToBeMade.addPossibleNextDecisionPoint(
                        new DecisionPoint(
                            returnBookIdAndPage.getBookId(),
                            pageToBeVisited.getReturnPageId()
                        )
                    );
                }
            } else {
                // Normal link
                decisionPointToBeMade.addPossibleNextDecisionPoint(
                    new DecisionPoint(
                        normalLinkBookId,
                        pageToBeVisited.getId(),
                        link.getId()
                    )
                );
            }
        }
    }

    private String getNormalLinkBookId(final DecisionPoint decisionPoint) {
        return decisionPoint.getBookId();
    }

    private String getTraversalLinkBookId(
        final DecisionPoint decisionPoint,
        final ModuleData moduleData,
        final String pageId
    ) {
        return decisionPoint.getBookId() + ";" + (moduleData.getModuleDepth() + 1) + "=" + pageId;
    }

    private ReturnBookIdAndModulePage getReturnLinkBookId(
        final DecisionPoint decisionPoint,
        final ModuleData moduleData
    ) {
        ReturnBookIdAndModulePage result = new ReturnBookIdAndModulePage();
        // Exclude the current module and return to its module page
        String[] idParts = decisionPoint.getBookId().split(";");
        StringBuilder bookIdBuilder = new StringBuilder();
        bookIdBuilder.append(idParts[0]);  // The book name
        for (int i = 1; i < idParts.length; i++) {
            String[] moduleIdParts = idParts[i].split("=");
            if (Integer.parseInt(moduleIdParts[0]) != moduleData.getModuleDepth()) {
                bookIdBuilder.append(";").append(idParts[i]);
            } else {
                result.setModulePageId(moduleIdParts[1]);
            }
        }
        result.setBookId(bookIdBuilder.toString());
        return result;
    }
}
