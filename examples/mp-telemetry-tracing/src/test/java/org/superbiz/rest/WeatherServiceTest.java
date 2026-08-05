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

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Arquillian.class)
public class WeatherServiceTest {

    // An all-zero trace id is what the OpenTelemetry API returns when no valid,
    // recording span is in context - i.e. when telemetry is not active.
    private static final String INVALID_TRACE_ID = "00000000000000000000000000000000";

    @ArquillianResource
    private URL base;

    private Client client;

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "test.war")
                .addPackage(WeatherService.class.getPackage())
                .addAsResource("META-INF/microprofile-config.properties");
    }

    @Before
    public void before() {
        this.client = ClientBuilder.newClient();
    }

    @Test
    public void testForecast() {
        final WebTarget webTarget = this.client.target(this.base.toExternalForm());
        final Response response = webTarget.path("/weather/forecast/London").request().get();
        assertEquals("Sunny in London", response.readEntity(String.class));
    }

    @Test
    public void testTracingIsActive() {
        final WebTarget webTarget = this.client.target(this.base.toExternalForm());
        final Response response = webTarget.path("/weather/traceid").request().get();
        final String traceId = response.readEntity(String.class);

        // A valid trace id proves MicroProfile Telemetry started a span for the
        // request; a 32 character hex string that is not all zeros.
        assertTrue("unexpected trace id: " + traceId, traceId.matches("[0-9a-f]{32}"));
        assertNotEquals(INVALID_TRACE_ID, traceId);
    }

    @After
    public void after() {
        this.client.close();
    }
}
