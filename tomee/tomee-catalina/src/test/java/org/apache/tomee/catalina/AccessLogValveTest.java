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

package org.apache.tomee.catalina;

import org.apache.openejb.loader.IO;
import org.apache.tomee.server.composer.Archive;
import org.apache.tomee.server.composer.TomEE;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

public class AccessLogValveTest {

    @Test
    public void test() throws Exception {
        final TomEE tomee = TomEE.webprofile()
                .add("webapps/test/WEB-INF/lib/app.jar", Archive.archive()
                        .add(Echo.class)
                        .asJar())
                .add("conf/logging.properties", "" +
                        "handlers = 1catalina.org.apache.juli.AsyncFileHandler, 2localhost.org.apache.juli.AsyncFileHandler, 3manager.org.apache.juli.AsyncFileHandler, 4host-manager.org.apache.juli.AsyncFileHandler, 5accesslog.org.apache.tomee.jul.handler.rotating.LocalFileHandler, org.apache.tomee.jul.formatter.AsyncConsoleHandler\n" +
                        "\n" +
                        ".handlers = 1catalina.org.apache.juli.AsyncFileHandler, org.apache.tomee.jul.formatter.AsyncConsoleHandler\n" +
                        "\n" +
                        "############################################################\n" +
                        "# Handler specific properties.\n" +
                        "# Describes specific configuration info for Handlers.\n" +
                        "############################################################\n" +
                        "\n" +
                        "1catalina.org.apache.juli.AsyncFileHandler.level = ALL\n" +
                        "1catalina.org.apache.juli.AsyncFileHandler.directory = ${catalina.base}/logs\n" +
                        "1catalina.org.apache.juli.AsyncFileHandler.prefix = catalina.\n" +
                        "1catalina.org.apache.juli.AsyncFileHandler.maxDays = 90\n" +
                        "1catalina.org.apache.juli.AsyncFileHandler.encoding = UTF-8\n" +
                        "\n" +
                        "2localhost.org.apache.juli.AsyncFileHandler.level = ALL\n" +
                        "2localhost.org.apache.juli.AsyncFileHandler.directory = ${catalina.base}/logs\n" +
                        "2localhost.org.apache.juli.AsyncFileHandler.prefix = localhost.\n" +
                        "2localhost.org.apache.juli.AsyncFileHandler.maxDays = 90\n" +
                        "2localhost.org.apache.juli.AsyncFileHandler.encoding = UTF-8\n" +
                        "\n" +
                        "3manager.org.apache.juli.AsyncFileHandler.level = ALL\n" +
                        "3manager.org.apache.juli.AsyncFileHandler.directory = ${catalina.base}/logs\n" +
                        "3manager.org.apache.juli.AsyncFileHandler.prefix = manager.\n" +
                        "3manager.org.apache.juli.AsyncFileHandler.maxDays = 90\n" +
                        "3manager.org.apache.juli.AsyncFileHandler.encoding = UTF-8\n" +
                        "\n" +
                        "4host-manager.org.apache.juli.AsyncFileHandler.level = ALL\n" +
                        "4host-manager.org.apache.juli.AsyncFileHandler.directory = ${catalina.base}/logs\n" +
                        "4host-manager.org.apache.juli.AsyncFileHandler.prefix = host-manager.\n" +
                        "4host-manager.org.apache.juli.AsyncFileHandler.maxDays = 90\n" +
                        "4host-manager.org.apache.juli.AsyncFileHandler.encoding = UTF-8\n" +
                        "\n" +
                        "org.apache.tomee.jul.formatter.AsyncConsoleHandler.level = ALL\n" +
                        "org.apache.tomee.jul.formatter.AsyncConsoleHandler.formatter = org.apache.juli.OneLineFormatter\n" +
                        "org.apache.tomee.jul.formatter.AsyncConsoleHandler.encoding = UTF-8\n" +
                        "\n" +
                        "com.tomitribe.tomee.valve.JULAccessLogValve.handlers = 5accesslog.org.apache.tomee.jul.handler.rotating.LocalFileHandler\n" +
                        "5accesslog.org.apache.tomee.jul.handler.rotating.LocalFileHandler.archiveOlderThan = 2s\n" +
                        "5accesslog.org.apache.tomee.jul.handler.rotating.LocalFileHandler.purgeOlderThan = 5s\n" +
                        "5accesslog.org.apache.tomee.jul.handler.rotating.LocalFileHandler.filenamePattern = ${catalina.base}/logs/accesslog.%s.%03d.log\n" +
                        "5accesslog.org.apache.tomee.jul.handler.rotating.LocalFileHandler.limit = 5 bytes" +
                        "")
                .home(this::swapValveInServerXml)
                // .debug(5005, true)
                .build();

        final int port = tomee.getPort();
        IntStream.range(0, 10).forEach(i -> {
            try {
                System.out.println(IO.slurp(new URL("http://localhost:" + port + "/test/echo/reverse/hello." + i)));
                if (i == 5) {
                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });


        final File logs = new File(tomee.getHome(), "logs");
        final long nbAccessLog = Arrays.stream(Objects.requireNonNull(new File(tomee.getHome(), "logs").listFiles()))
                .filter(file -> file.getName().startsWith("accesslog"))
                .count();

        // about 1 file per call/iteration
        // at the fifth iteration, the first files should be archived
        Assert.assertTrue(nbAccessLog >= 3); // depending on the accuracy of the system clock, might be 3 or 4

        final long nbAccesslogArchived = Arrays.stream(Objects.requireNonNull(new File(tomee.getHome(), "logs/archives").listFiles()))
                .filter(file -> file.getName().startsWith("accesslog"))
                .count();

        // the first five should be archived
        Assert.assertTrue(nbAccesslogArchived >= 5); // depending on the accuracy of the system clock, might be 5 or 6

        tomee.shutdown();


    }

    private void swapValveInServerXml(final File home) {

        try {
            final File serverXml = new File(home, "conf/server.xml");

            final String content = IO.slurp(serverXml)
                    .replaceAll("<Valve className=\"org.apache.catalina.valves.AccessLogValve[^/]+/>",
                            "<Valve className=\"com.tomitribe.tomee.valve.JULAccessLogValve\" " +
                                    "pattern=\"%h %l %u %t &quot;%r&quot; %s %b\" />");

            IO.writeString(serverXml, content);
            System.out.println("Updated server.xml successfully.");

        } catch (final IOException e) {
            throw new RuntimeException("Failed to update server.xml", e);
        }
    }
}
