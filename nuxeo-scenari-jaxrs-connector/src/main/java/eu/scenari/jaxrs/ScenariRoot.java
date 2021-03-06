/*
 * (C) Copyright ${year} Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     ogrisel
 */

package eu.scenari.jaxrs;

import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.api.impl.blob.StreamingBlob;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.platform.ui.web.util.BaseURL;
import org.nuxeo.ecm.webengine.jaxrs.session.SessionFactory;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.ModuleRoot;

/**
 * HTTP API with Cross Origin Resource Sharing support to make it possible to
 * import blobs in temporary file and then redirect the client to a Web page to
 * let the user finish the import (select container workspace or folder, choose
 * to create as new document or updated existing document) and trigger
 * additional actions on the result.
 *
 * @author ogrisel
 */
@Path("/scenari")
@WebObject(type = "ScenariRoot")
public class ScenariRoot extends ModuleRoot {

    protected final CoreSession session;

    protected final String baseURL;

    protected final HttpHeaders headers;

    protected final Set<String> authorizedOrigins = new HashSet<String>();

    protected DocumentModel zipDoc;

    public ScenariRoot(@Context
    HttpServletRequest request, @Context
    HttpHeaders headers) {
        this.headers = headers;
        session = SessionFactory.getSession(request);
        baseURL = BaseURL.getBaseURL(request);

        // TODO: read framework property to configure authorized origins instead
        authorizedOrigins.add("*");
    }

    @OPTIONS
    public Response handleCorsPreflightOnManifest(@Context
    HttpHeaders headers) {
        ResponseBuilder res = Response.ok();
        CorsHelper.enableCORS(authorizedOrigins, res, headers);
        return res.build();
    }

    @GET
    @Produces("application/xml")
    public Object getManifest() {
        ResponseBuilder builder = Response.ok(getView("index"));
        CorsHelper.addCORSOrigin(authorizedOrigins, builder, headers);
        return builder.build();
    }

    public String getBaseNuxeoUrl() {
        return baseURL;
    }

    public String getScenariConnectorBaseUrl() {
        return baseURL + "site/scenari";
    }

    /* ZIP Upload */

    public String getZipUploadUrl() {
        return baseURL + "site/scenari/upload";
    }

    @OPTIONS
    @Path("/upload")
    public Response handleCorsPreflightForUpload() {
        ResponseBuilder res = Response.ok();
        CorsHelper.enableCORS(authorizedOrigins, res, headers);
        return res.build();
    }

    @POST()
    @Path("/upload")
    public Object upload(InputStream input) throws URISyntaxException,
            ClientException {
        final Blob zipBlob = StreamingBlob.createFromStream(input);
        ZipDocumentImporter importer = new ZipDocumentImporter(session, zipBlob);
        importer.runUnrestricted();
        ResponseBuilder builder = Response.created(getImportScreenUrl(
                session.getRepositoryName(), importer.documentRef));
        CorsHelper.addCORSOrigin(authorizedOrigins, builder, headers);
        return builder.build();
    }

    /* Document Import UI */

    protected URI getImportScreenUrl(String repositoryName, DocumentRef ref)
            throws URISyntaxException {
        return new URI(baseURL
                + String.format("site/scenari/importscreen/%s/%s",
                        repositoryName, ref.toString()));
    }

    // TODO: turn me into a sub resource
    @GET()
    @Produces("text/html;charset=utf-8")
    @Path("/importscreen/{repository}/{idref}")
    public Object getImportScreen(@PathParam("repository")
    String repository, @PathParam("idref")
    String idRef) throws ClientException {
        zipDoc = session.getDocument(new IdRef(idRef));
        return getView("import_screen");
    }

    public static class ZipDocumentImporter extends UnrestrictedSessionRunner {

        protected Blob blob;

        public DocumentRef documentRef;

        protected final Principal principal;

        public ZipDocumentImporter(CoreSession session, Blob zipBlob) {
            super(session.getRepositoryName());
            this.principal = session.getPrincipal();
            this.blob = zipBlob;
        }

        @Override
        public void run() throws ClientException {
            DocumentModel zipBlobDoc = session.createDocumentModel("File");
            zipBlobDoc.setPropertyValue("file:content", (Serializable) blob);
            zipBlobDoc = session.createDocument(zipBlobDoc);
            ACP acp = zipBlobDoc.getACP();
            ACL acl = acp.getOrCreateACL();
            acl.add(new ACE(principal.getName(), SecurityConstants.READ_WRITE, true));
            acp.addACL(acl);
            documentRef = zipBlobDoc.getRef();
            session.setACP(documentRef, acp, true);
        }

    }

}
