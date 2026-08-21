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

import org.apache.openejb.loader.provisining.ProvisioningResolver;
import org.apache.openejb.util.OpenEjbVersion;

import java.io.File;

/**
 * Resolves the {@code apache-tomee} webprofile zip from the local Maven repository. The
 * version is taken from the {@code version} system property (set by Surefire) and falls
 * back to the running {@link OpenEjbVersion} so the harness also works from an IDE / plain
 * {@code java} launch.
 */
public final class Repository {

    private static final ProvisioningResolver RESOLVER = new ProvisioningResolver();

    private Repository() {
    }

    public static File tomeeZip() {
        return resolve("mvn:org.apache.tomee:apache-tomee:" + version() + ":zip:webprofile");
    }

    public static String version() {
        final String property = System.getProperty("version");
        if (property != null && !property.isEmpty()) {
            return property;
        }
        return OpenEjbVersion.get().getVersion();
    }

    private static File resolve(final String coordinate) {
        final String oldCache = System.getProperty(ProvisioningResolver.OPENEJB_DEPLOYER_CACHE_FOLDER);
        final String cache = System.getProperty("openejb.itest.soak.cache", "target/cache");
        new File(cache).mkdirs(); // ensure cache folder exists otherwise copy will fail
        System.setProperty(ProvisioningResolver.OPENEJB_DEPLOYER_CACHE_FOLDER, cache);
        try {
            final String path = RESOLVER.realLocation(coordinate).iterator().next();
            return new File(path);
        } catch (final Exception e) {
            throw new IllegalStateException("Cannot resolve " + coordinate, e);
        } finally {
            if (oldCache == null) {
                System.clearProperty(ProvisioningResolver.OPENEJB_DEPLOYER_CACHE_FOLDER);
            } else {
                System.setProperty(ProvisioningResolver.OPENEJB_DEPLOYER_CACHE_FOLDER, oldCache);
            }
        }
    }
}
