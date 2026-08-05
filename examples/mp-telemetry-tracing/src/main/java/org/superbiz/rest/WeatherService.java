/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.superbiz.rest;

import io.opentelemetry.api.trace.Span;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/weather")
@RequestScoped
public class WeatherService {

    @Inject
    private WeatherGateway gateway;

    // The span for the current request. MicroProfile Telemetry automatically
    // starts a server span around every JAX-RS invocation.
    @Inject
    private Span span;

    @GET
    @Path("/forecast/{city}")
    @Produces(MediaType.TEXT_PLAIN)
    public String forecast(@PathParam("city") final String city) {
        return gateway.forecast(city);
    }

    // Exposes the trace id of the current request span. When MicroProfile
    // Telemetry is enabled this is a valid, non-zero 32 character hex id; the
    // test uses it to prove that tracing is active.
    @GET
    @Path("/traceid")
    @Produces(MediaType.TEXT_PLAIN)
    public String traceId() {
        return span.getSpanContext().getTraceId();
    }
}
