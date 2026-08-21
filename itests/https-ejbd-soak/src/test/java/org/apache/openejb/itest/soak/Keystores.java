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

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Generates a throwaway self-signed PKCS12 keystore (CN/SAN {@code localhost}, {@code 127.0.0.1})
 * at runtime by shelling out to the JDK {@code keytool}. Used both as server A's TLS keystore and
 * as server B's trust store, so nothing binary needs to be checked into the repository.
 */
public final class Keystores {

    public static final String PASSWORD = "changeit";
    public static final String TYPE = "PKCS12";

    private Keystores() {
    }

    public static File generate(final File dir) throws Exception {
        final File keystore = new File(dir, "keystore.p12");
        if (keystore.exists()) {
            return keystore;
        }

        final String keytool = new File(new File(System.getProperty("java.home"), "bin"), "keytool").getAbsolutePath();
        final List<String> cmd = Arrays.asList(
            keytool,
            "-genkeypair",
            "-alias", "localhost",
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "3650",
            "-dname", "CN=localhost, OU=TomEE, O=Apache, C=NA",
            "-ext", "SAN=dns:localhost,ip:127.0.0.1",
            "-keystore", keystore.getAbsolutePath(),
            "-storetype", TYPE,
            "-storepass", PASSWORD,
            "-keypass", PASSWORD);

        final Process process = new ProcessBuilder(cmd)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();

        final int code = process.waitFor();
        if (code != 0 || !keystore.exists()) {
            throw new IllegalStateException("keytool failed to create keystore (exit " + code + ")");
        }
        return keystore;
    }
}
