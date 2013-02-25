/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.scenari.jaxrs;

import java.util.List;
import java.util.Set;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

/**
 * Utilities for adding <a href="http://dev.w3.org/2006/waf/access-control/">
 * CORS</a> support to the Stanbol RESTful API
 * <p>
 * <p>
 * Note that this utility depends on the {@link JerseyEndpoint#CORS_ORIGIN}
 * property read from the Servlet Context.
 * <p>
 * Currently this support for
 * <ul>
 * <li>Origin header
 * <li>Preflight Requests.
 * </ul>
 *
 * @author Rupert Westenthaler
 *
 */
public final class CorsHelper {

    /**
     * The "Origin" header as used in requests
     */
    public static final String ORIGIN = "Origin";

    /**
     * The ALLOW_ORIGIN header as added to responses
     */
    public static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";

    /**
     * The "Access-Control-Request-Method" header
     */
    public static final String REQUEST_METHOD = "Access-Control-Request-Method";

    /**
     * The "Access-Control-Request-Headers" header
     */
    public static final String REQUEST_HEADERS = "Access-Control-Request-Headers";

    /**
     * The "Access-Control-Request-Headers" header
     */
    public static final String ALLOW_HEADERS = "Access-Control-Allow-Headers";

    /**
     * The "Access-Control-Allow-Methods" header
     */
    public static final String ALLOW_METHODS = "Access-Control-Allow-Methods";

    /**
     * The "Access-Control-Expose-Headers" header
     */
    public static final String EXPOSE_HEADERS = "Access-Control-Expose-Headers";

    /**
     * The default methods for the Access-Control-Request-Method header field.
     * Set to "GET, POST, OPTIONS"
     */
    public static final String DEFAULT_REQUEST_METHODS = "GET, POST, OPTIONS";

    public static boolean checkCorsOrigin(String origin,
            Set<String> authorizedOrigins) {
        return authorizedOrigins.contains("*")
                || authorizedOrigins.contains(origin);
    }

    /**
     * Adds the Origin response header to the response builder based on the
     * headers of an request.
     *
     * @param context the ServletContext holding the
     *            {@link JerseyEndpoint#CORS_ORIGIN} configuration.
     * @param responseBuilder The {@link ResponseBuilder} the origin header is
     *            added to if (1) a origin header is present in the request
     *            headers and (1) the origin is compatible with the
     *            configuration set for the {@link JerseyEndpoint}.
     * @param requestHeaders the request headers
     * @return <code>true</code> if the origin header was added. Otherwise
     *         <code>false</code>
     * @throws WebApplicationException it the request headers define multiple
     *             values for the "Origin" header an WebApplicationException
     *             with the Status "BAD_REQUEST" is thrown.
     */
    public static boolean addCORSOrigin(Set<String> authorizedOrigins,
            ResponseBuilder responseBuilder, HttpHeaders requestHeaders)
            throws WebApplicationException {
        List<String> originHeaders = requestHeaders.getRequestHeader(CorsHelper.ORIGIN);
        if (originHeaders != null && !originHeaders.isEmpty()) {
            if (originHeaders.size() != 1) {
                throw new WebApplicationException(new IllegalStateException(
                        "Multiple 'Origin' header values '" + originHeaders
                                + "' found in the request headers"),
                        Status.BAD_REQUEST);
            } else {
                if ((authorizedOrigins.contains("*") || authorizedOrigins.contains(originHeaders.get(0)))) {
                    addExposedHeaders(responseBuilder);
                    responseBuilder.header(CorsHelper.ALLOW_ORIGIN,
                            originHeaders.get(0));
                    return true;
                }
            }
        }
        return false;
    }

    private static void addExposedHeaders(ResponseBuilder responseBuilder) {
        // TODO: do not hardcode Location
        responseBuilder.header(EXPOSE_HEADERS, "Location");
    }

    /**
     * Enable CORS for OPTION requests based on the provided request headers and
     * the allowMethods.
     * <p>
     * The {@link #addCORSOrigin(ResponseBuilder, HttpHeaders)} method is used
     * deal with the origin. The allowMethods are set to the values or to
     * {@link CorsHelper#DEFAULT_REQUEST_METHODS} if non is of the parsed values
     * do not contain a single value that is not <code>null</code> nor empty.
     *
     * @param context the ServletContext holding the
     *            {@link JerseyEndpoint#CORS_ORIGIN} configuration.
     * @param responseBuilder The {@link ResponseBuilder} to add the CORS
     *            headers
     * @param requestHeaders the headers of the request
     * @param allowMethods the allowMethods to if <code>null</code> or empty,
     *            the {@link CorsHelper#DEFAULT_REQUEST_METHODS} are added
     * @return <code>true</code> if the CORS header where added or
     * @throws WebApplicationException it the request headers define multiple
     *             values for the "Origin" header an WebApplicationException
     *             with the Status "BAD_REQUEST" is thrown.
     * @throws IllegalArgumentException if a allowMethods is <code>null</code>
     *             or empty. NOT if the String array is <code>null</code> or
     *             empty, but if any of the items within the array is
     *             <code>null</code> or empty!
     */
    public static boolean enableCORS(Set<String> authorizedOrigins,
            ResponseBuilder responseBuilder, HttpHeaders requestHeaders,
            String... allowMethods) throws WebApplicationException {
        // first check if the Origin is present
        if (addCORSOrigin(authorizedOrigins, responseBuilder, requestHeaders)) {
            // now add the allowedMethods
            boolean added = false;
            StringBuilder methods = new StringBuilder();
            if (allowMethods != null) {
                for (String method : allowMethods) {
                    if (method != null && !method.isEmpty()) {
                        if (added) {
                            methods.append(", ");
                        }
                        methods.append(method);
                        added = true;
                    } else {
                        // throw an exception to make it easier to debug errors
                        throw new IllegalArgumentException(
                                "allow methods MUST NOT be NULL nor empty!");
                    }
                }
            }
            if (!added) {
                methods.append(CorsHelper.DEFAULT_REQUEST_METHODS);
            }
            responseBuilder.header(CorsHelper.ALLOW_METHODS, methods.toString());
            // third replay "Access-Control-Request-Headers" values
            // currently there is no need to restrict such headers so the
            // simplest
            // way is to return them as they are parsed
            List<String> requestHeaderValues = requestHeaders.getRequestHeader(REQUEST_HEADERS);
            added = false;
            if (requestHeaderValues != null && !requestHeaderValues.isEmpty()) {
                StringBuilder requestHeader = new StringBuilder();
                for (String header : requestHeaderValues) {
                    if (header != null && !header.isEmpty()) {
                        if (added) {
                            requestHeader.append(", ");
                        }
                        requestHeader.append(header);
                        added = true;
                    }
                }
                if (added) {
                    responseBuilder.header(ALLOW_HEADERS,
                            requestHeader.toString());
                }
            }
            return true;
        } else {
            return false;
        }
    }
}
