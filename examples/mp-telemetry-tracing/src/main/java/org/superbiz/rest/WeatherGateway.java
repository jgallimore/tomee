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
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@RequestScoped
public class WeatherGateway {

    // The current Span, produced by MicroProfile Telemetry. Inside a @WithSpan
    // method this resolves to the span created for that method.
    @Inject
    private Span span;

    // The application Tracer, produced by MicroProfile Telemetry, used to build
    // spans by hand.
    @Inject
    private Tracer tracer;

    // @WithSpan tells MicroProfile Telemetry to start a span named
    // "weather.forecast" around this method and end it when the method returns.
    // @SpanAttribute captures the "city" argument as an attribute on that span.
    @WithSpan("weather.forecast")
    public String forecast(@SpanAttribute("weather.city") final String city) {
        // Enrich the span created by @WithSpan with a custom attribute.
        span.setAttribute("weather.source", "AccuWeather");

        return lookUp(city);
    }

    // Spans can also be created programmatically with the injected Tracer. This
    // span becomes a child of the "weather.forecast" span that is currently active.
    private String lookUp(final String city) {
        final Span child = tracer.spanBuilder("weather.lookUp").startSpan();
        try {
            return "Sunny in " + city;
        } finally {
            child.end();
        }
    }
}
