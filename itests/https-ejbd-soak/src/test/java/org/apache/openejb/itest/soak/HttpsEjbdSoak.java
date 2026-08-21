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
package org.apache.openejb.itest.soak;

import org.apache.openejb.client.RemoteInitialContextFactory;
import org.apache.openejb.itest.soak.ejb.Caller;
import org.apache.openejb.loader.Files;
import org.junit.Test;

import javax.naming.Context;
import javax.naming.InitialContext;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Endless soak harness for the TomEE-to-TomEE EJB-over-https (httpejbd) path.
 *
 * <p>Topology (see the module description):</p>
 * <pre>
 *   driver --http--&gt; Server B (CallerBean) --https/ejbd--&gt; Server A (CalculatorBean)
 * </pre>
 *
 * <p>Every {@code relay()} drives one B-&gt;A remote invocation over the https connector, so the
 * openejb-client keep-alive socket pool on B and the Tomcat {@code Http11NioProtocol}/SecureNioChannel
 * path on A are exercised continuously. Point {@code lsof}/{@code ss} at server A's PID and watch for
 * a slow climb in file descriptors / CLOSE_WAIT sockets.</p>
 *
 * <p>This class is intentionally <b>not</b> named {@code *Test}, so Surefire skips it during a normal
 * build. Run it manually:</p>
 * <pre>
 *   mvn test -pl itests/https-ejbd-soak -am -Dtest=HttpsEjbdSoak
 * </pre>
 * <p>or straight from an IDE / plain java via {@link #main(String[])}. Tunables (system properties):</p>
 * <ul>
 *   <li>{@code soak.threads}          concurrent driver threads (default 4)</li>
 *   <li>{@code soak.duration.seconds} stop after N seconds; 0 = forever (default 0)</li>
 *   <li>{@code soak.report.seconds}   stats interval (default 10)</li>
 *   <li>{@code soak.caller.jndi}      remote JNDI name of the CallerBean on B</li>
 *   <li>{@code version}               apache-tomee version to unpack (Surefire sets it; else derived)</li>
 * </ul>
 */
public class HttpsEjbdSoak {

    private static final String CALLER_JNDI = System.getProperty(
        "soak.caller.jndi", "global/soak/CallerBean!" + Caller.class.getName());

    private final AtomicLong ok = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean(true);

    @Test
    public void soak() throws Exception {
        run();
    }

    public static void main(final String[] args) throws Exception {
        new HttpsEjbdSoak().run();
    }

    private void run() throws Exception {
        final int threads = Integer.getInteger("soak.threads", 4);
        final long durationSeconds = Long.getLong("soak.duration.seconds", 0L);
        final long reportSeconds = Long.getLong("soak.report.seconds", 10L);

        final File base = Files.tmpdir();
        final File keystore = Keystores.generate(base);
        final File appJar = AppArchive.build(base);
        final File zip = Repository.tomeeZip();

        final TomEEServer serverA = new TomEEServer("A", zip, base, keystore, Keystores.PASSWORD);
        serverA.deploy(appJar, "soak.jar");
        serverA.addJvmArg("-Dname=A");
        serverA.start(3, TimeUnit.MINUTES);

        final TomEEServer serverB = new TomEEServer("B", zip, base, keystore, Keystores.PASSWORD);
        serverB.deploy(appJar, "soak.jar");
        serverB.addJvmArg("-Dname=B");
        serverB.addJvmArg("-Dsoak.callee.url=https://localhost:" + serverA.httpsPort() + "/tomee/ejb");
        serverB.addJvmArg("-Djavax.net.ssl.trustStore=" + keystore.getAbsolutePath());
        serverB.addJvmArg("-Djavax.net.ssl.trustStorePassword=" + Keystores.PASSWORD);
        serverB.addJvmArg("-Djavax.net.ssl.trustStoreType=" + Keystores.TYPE);
        serverB.start(3, TimeUnit.MINUTES);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            serverB.destroy();
            serverA.destroy();
        }));

        final Caller caller = lookupCaller(serverB.httpPort());

        // prove the relay round-trips B -> A over https before starting the load
        System.out.println("[driver] warmup relay -> " + caller.relay(1, 2));
        System.out.printf("[driver] soaking with %d thread(s); duration=%s%n",
            threads, durationSeconds == 0 ? "forever" : durationSeconds + "s");

        final List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final Thread worker = new Thread(() -> loop(caller), "soak-worker-" + i);
            worker.setDaemon(true);
            worker.start();
            workers.add(worker);
        }

        report(caller, reportSeconds, durationSeconds);

        running.set(false);
        for (final Thread worker : workers) {
            worker.join(TimeUnit.SECONDS.toMillis(5));
        }
        serverB.destroy();
        serverA.destroy();
    }

    private void loop(final Caller caller) {
        while (running.get()) {
            try {
                caller.relay(1, 2);
                ok.incrementAndGet();
            } catch (final Throwable t) {
                final long n = errors.incrementAndGet();
                if (n == 1 || n % 100 == 0) {
                    System.err.printf("[driver] relay failure #%d: %s%n", n, t);
                }
            }
        }
    }

    private void report(final Caller caller, final long reportSeconds, final long durationSeconds) {
        final long start = System.currentTimeMillis();
        long previous = 0;
        while (running.get()) {
            try {
                TimeUnit.SECONDS.sleep(reportSeconds);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            final long total = ok.get();
            final long rate = (total - previous) / Math.max(1, reportSeconds);
            previous = total;
            long relays = -1;
            try {
                relays = caller.count();
            } catch (final Throwable ignored) {
                // server B may be momentarily unreachable - keep reporting
            }
            System.out.printf("[driver] ok=%d err=%d rate=%d/s serverB.relays=%d elapsed=%ds%n",
                total, errors.get(), rate, relays, (System.currentTimeMillis() - start) / 1000);

            if (durationSeconds > 0 && (System.currentTimeMillis() - start) >= TimeUnit.SECONDS.toMillis(durationSeconds)) {
                System.out.println("[driver] duration reached, stopping");
                return;
            }
        }
    }

    private static Caller lookupCaller(final int httpPort) throws Exception {
        final Properties p = new Properties();
        p.put(Context.INITIAL_CONTEXT_FACTORY, RemoteInitialContextFactory.class.getName());
        p.put(Context.PROVIDER_URL, "http://localhost:" + httpPort + "/tomee/ejb");
        final InitialContext ctx = new InitialContext(p);
        return Caller.class.cast(ctx.lookup(CALLER_JNDI));
    }
}
