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

import org.apache.openejb.itest.soak.ejb.CalculatorBean;
import org.apache.openejb.loader.IO;
import org.apache.openejb.loader.JarLocation;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Builds the deployable EJB jar ({@code soak.jar}) from this module's compiled main classes
 * (the {@code ejb} package plus {@code META-INF/ejb-jar.xml}). The jar is deployed unchanged to
 * both servers; its base name drives the module id, hence the {@code global/soak/...} JNDI names.
 */
public final class AppArchive {

    private AppArchive() {
    }

    public static File build(final File dir) throws Exception {
        final File classes = JarLocation.jarLocation(CalculatorBean.class);
        final File jar = new File(dir, "soak.jar");

        if (classes.isFile()) {
            // already packaged (e.g. run from an installed artifact) - reuse as-is
            IO.copy(classes, jar);
            return jar;
        }

        final Path root = classes.toPath();
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar));
             Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                final String name = root.relativize(path).toString().replace(File.separatorChar, '/');
                try {
                    out.putNextEntry(new JarEntry(name));
                    out.write(Files.readAllBytes(path));
                    out.closeEntry();
                } catch (final Exception e) {
                    throw new IllegalStateException("Cannot add " + name + " to " + jar, e);
                }
            });
        }
        return jar;
    }
}
