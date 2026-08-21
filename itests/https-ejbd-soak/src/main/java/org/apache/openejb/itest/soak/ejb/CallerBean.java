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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.openejb.itest.soak.ejb;

import org.apache.openejb.client.RemoteInitialContextFactory;

import jakarta.ejb.Stateless;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs inside server B. On the first invocation it opens a remote {@link InitialContext}
 * pointing at server A's https httpejbd endpoint and caches the {@link Calculator} proxy.
 * Every {@link #relay(int, int)} then re-uses that proxy, so the openejb-client keep-alive
 * socket pool to A is exercised in steady state — exactly the path suspected of leaking
 * over many hours in production.
 * <p>
 * Configuration is passed to server B's JVM as system properties:
 * <ul>
 *   <li>{@code -Dsoak.callee.url}  — e.g. {@code https://localhost:8443/tomee/ejb} (required)</li>
 *   <li>{@code -Dsoak.callee.jndi} — remote JNDI name of the Calculator (defaults to the
 *       global name for a module deployed as {@code apps/soak.jar})</li>
 * </ul>
 * Server B must also trust A's certificate, via the usual
 * {@code -Djavax.net.ssl.trustStore*} properties on its JVM.
 */
@Stateless
public class CallerBean implements Caller {

    public static final String CALLEE_URL = "soak.callee.url";
    public static final String CALLEE_JNDI = "soak.callee.jndi";
    public static final String DEFAULT_JNDI = "global/soak/CalculatorBean!" + Calculator.class.getName();

    private static volatile Calculator calculator;
    private static final AtomicLong COUNT = new AtomicLong();

    @Override
    public String relay(final int a, final int b) {
        try {
            final Calculator calc = calculator();
            final int sum = calc.sum(a, b);
            if (sum != a + b) {
                throw new IllegalStateException("Unexpected sum from callee: " + sum + " != " + (a + b));
            }
            final String name = calc.name();
            COUNT.incrementAndGet();
            return name + ":" + sum;
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalStateException("relay to callee failed: " + e, e);
        }
    }

    @Override
    public long count() {
        return COUNT.get();
    }

    private static Calculator calculator() throws Exception {
        Calculator local = calculator;
        if (local == null) {
            synchronized (CallerBean.class) {
                local = calculator;
                if (local == null) {
                    final String url = System.getProperty(CALLEE_URL);
                    if (url == null || url.isEmpty()) {
                        throw new IllegalStateException("Missing required system property -D" + CALLEE_URL);
                    }
                    final String jndi = System.getProperty(CALLEE_JNDI, DEFAULT_JNDI);

                    final Properties p = new Properties();
                    p.put(Context.INITIAL_CONTEXT_FACTORY, RemoteInitialContextFactory.class.getName());
                    p.put(Context.PROVIDER_URL, url);

                    final InitialContext ctx = new InitialContext(p);
                    local = Calculator.class.cast(ctx.lookup(jndi));
                    calculator = local;
                }
            }
        }
        return local;
    }
}
