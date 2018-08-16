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

package org.apache.openejb.client.util;

import java.security.AccessController;
import java.security.PrivilegedAction;

public class TCCLUtil {

    public static final boolean IS_SECURITY_ENABLED = (System.getSecurityManager() != null);

    public static ClassLoader setThreadContextClassLoader(final Thread thread, final ClassLoader classLoader) {
        if (thread == null) {
            throw new NullPointerException("Attempting to set context classloader on null thread");
        }

        if (classLoader == null) {
            throw new NullPointerException("Attempting to set null context classloader thread");
        }

        final ClassLoader oldClassLoader = thread.getContextClassLoader();

        if (IS_SECURITY_ENABLED) {
            PrivilegedAction<Void> pa = new SetTcclAction(thread, classLoader);
            AccessController.doPrivileged(pa);
        } else {
            TCCLUtil.setThreadContextClassLoader(thread, classLoader);
        }

        return oldClassLoader;
    }

    public static ClassLoader setThreadContextClassLoader(final ClassLoader classLoader) {
        return setThreadContextClassLoader(Thread.currentThread(), classLoader);
    }

    public static class SetTcclAction implements PrivilegedAction<Void> {

        private final ClassLoader cl;
        private final Thread t;

        public SetTcclAction(Thread t, ClassLoader cl) {
            this.t = t;
            this.cl = cl;
        }

        @Override
        public Void run() {
            TCCLUtil.setThreadContextClassLoader(t, cl);
            return null;
        }
    }
}
