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

import org.apache.openejb.loader.Files;
import org.apache.openejb.loader.IO;
import org.apache.openejb.loader.Zips;
import org.apache.openejb.util.NetworkUtil;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Unpacks and launches a single real (Tomcat) TomEE server in its own process and home
 * directory, with an NIO https connector wired to a throwaway keystore. Modelled on
 * {@link org.apache.openejb.config.RemoteServer}'s catalina invocation, but per-instance so
 * two servers can run side by side.
 */
public class TomEEServer implements AutoCloseable {

    private final String name;
    private final File home;
    private final int httpPort;
    private final int httpsPort;
    private final int shutdownPort;
    private final int ajpPort;
    private final List<String> jvmArgs = new ArrayList<>();
    private Process process;

    public TomEEServer(final String name, final File zip, final File baseDir,
                       final File keystore, final String keystorePassword) throws Exception {
        this.name = name;
        this.home = new File(baseDir, name);
        this.httpPort = NetworkUtil.getNextAvailablePort();
        this.httpsPort = NetworkUtil.getNextAvailablePort();
        this.shutdownPort = NetworkUtil.getNextAvailablePort();
        this.ajpPort = NetworkUtil.getNextAvailablePort();

        Files.mkdir(home);
        Zips.unzip(zip, home, true); // noparent: strip the apache-tomee-<flavor>-<version>/ root

        Files.mkdirs(new File(home, "temp"));
        Files.mkdirs(new File(home, "logs"));
        Files.mkdirs(new File(home, "apps"));

        configureServerXml(keystore, keystorePassword);
        configureTomeeXml();
    }

    public String name() {
        return name;
    }

    public File home() {
        return home;
    }

    public int httpPort() {
        return httpPort;
    }

    public int httpsPort() {
        return httpsPort;
    }

    /** Copy the application into the apps/ directory (deployed via conf/tomee.xml Deployments). */
    public void deploy(final File appJar, final String asName) throws Exception {
        IO.copy(appJar, new File(new File(home, "apps"), asName));
    }

    public void addJvmArg(final String arg) {
        jvmArgs.add(arg);
    }

    public void start(final long timeout, final TimeUnit unit) throws Exception {
        final List<String> args = new ArrayList<>();
        args.add(new File(new File(System.getProperty("java.home"), "bin"), "java").getAbsolutePath());
        args.add("-XX:+HeapDumpOnOutOfMemoryError");

        // JDK 11+ opens required by TomEE (see RemoteServer)
        args.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
        args.add("--add-opens=java.base/java.io=ALL-UNNAMED");
        args.add("--add-opens=java.base/java.util=ALL-UNNAMED");
        args.add("--add-opens=java.base/java.util.concurrent=ALL-UNNAMED");
        args.add("--add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED");
        args.add("--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED");
        args.add("--add-opens=java.base/java.net=ALL-UNNAMED");

        args.add("-Dcatalina.base=" + home.getAbsolutePath());
        args.add("-Dcatalina.home=" + home.getAbsolutePath());
        args.add("-Djava.io.tmpdir=" + new File(home, "temp").getAbsolutePath());
        args.add("-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager");
        final File logging = new File(new File(home, "conf"), "logging.properties");
        if (logging.exists()) {
            args.add("-Djava.util.logging.config.file=" + logging.getAbsolutePath());
        }
        args.add("-Dorg.apache.catalina.STRICT_SERVLET_COMPLIANCE=true");

        final File javaagent = findJavaagent();
        if (javaagent != null) {
            args.add("-javaagent:" + javaagent.getAbsolutePath());
        }

        args.addAll(jvmArgs);

        final File bin = new File(home, "bin");
        final String cp = new File(bin, "bootstrap.jar").getAbsolutePath()
            + File.pathSeparator + new File(bin, "tomcat-juli.jar").getAbsolutePath();
        args.add("-classpath");
        args.add(cp);
        args.add("org.apache.catalina.startup.Bootstrap");
        args.add("start");

        System.out.printf("[%s] starting TomEE (http=%d https=%d shutdown=%d) home=%s%n",
            name, httpPort, httpsPort, shutdownPort, home.getAbsolutePath());

        process = new ProcessBuilder(args)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .directory(home)
            .start();

        if (!waitForPort(httpPort, unit.toMillis(timeout))) {
            destroy();
            throw new IllegalStateException("[" + name + "] server did not open http port " + httpPort
                + " within " + timeout + " " + unit);
        }
        System.out.printf("[%s] started%n", name);
    }

    private File findJavaagent() {
        final File lib = new File(home, "lib");
        final File[] jars = lib.listFiles((d, n) -> n.startsWith("openejb-javaagent") && n.endsWith(".jar"));
        return (jars != null && jars.length > 0) ? jars[0] : null;
    }

    private void configureServerXml(final File keystore, final String keystorePassword) throws Exception {
        final File serverXml = new File(new File(home, "conf"), "server.xml");
        String xml = IO.slurp(serverXml);

        // Repoint the stock ports (Tomcat defaults) at our free ports.
        xml = xml.replace("port=\"8005\"", "port=\"" + shutdownPort + "\"");
        xml = xml.replace("port=\"8080\"", "port=\"" + httpPort + "\"");
        xml = xml.replace("port=\"8009\"", "port=\"" + ajpPort + "\"");
        xml = xml.replace("redirectPort=\"8443\"", "redirectPort=\"" + httpsPort + "\"");

        // Inject an NIO https connector (Http11NioProtocol is the connector whose
        // SecureNioChannel write/reset path is under investigation).
        final String https = ""
            + "    <Connector port=\"" + httpsPort + "\" protocol=\"org.apache.coyote.http11.Http11NioProtocol\"\n"
            + "               SSLEnabled=\"true\" scheme=\"https\" secure=\"true\" maxThreads=\"200\"\n"
            + "               defaultSSLHostConfigName=\"localhost\">\n"
            + "      <SSLHostConfig hostName=\"localhost\">\n"
            + "        <Certificate certificateKeystoreFile=\"" + keystore.getAbsolutePath() + "\"\n"
            + "                     certificateKeystorePassword=\"" + keystorePassword + "\"\n"
            + "                     certificateKeystoreType=\"" + Keystores.TYPE + "\"\n"
            + "                     type=\"RSA\"/>\n"
            + "      </SSLHostConfig>\n"
            + "    </Connector>\n";

        if (!xml.contains("</Service>")) {
            throw new IllegalStateException("Unexpected server.xml layout, no </Service>: " + serverXml);
        }
        xml = xml.replace("</Service>", https + "  </Service>");

        IO.copy(IO.read(xml), serverXml);
    }

    private void configureTomeeXml() throws Exception {
        final String tomeeXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<tomee>\n"
            + "  <Deployments dir=\"apps\"/>\n"
            + "</tomee>\n";
        IO.copy(IO.read(tomeeXml), new File(new File(home, "conf"), "tomee.xml"));
    }

    private boolean waitForPort(final int port, final long timeoutMillis) {
        final long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (process != null && !process.isAlive()) {
                return false;
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("localhost", port), 1000);
                return true;
            } catch (final Exception ignored) {
                try {
                    Thread.sleep(500);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    public void destroy() {
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(20, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (final InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            process = null;
        }
    }

    @Override
    public void close() {
        destroy();
    }
}
