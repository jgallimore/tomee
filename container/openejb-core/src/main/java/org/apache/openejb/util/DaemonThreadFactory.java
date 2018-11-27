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

package org.apache.openejb.util;

import org.apache.openejb.core.ParentClassLoaderFinder;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.openejb.util.Join.join;

/**
 * @version $Rev$ $Date$
 */
public class DaemonThreadFactory implements ThreadFactory {

    private final String name;
    private final ThreadGroup group;
    private final AtomicInteger ids = new AtomicInteger(0);

    public DaemonThreadFactory(final Object... name) {
        this.name = join(" ", name).trim();
        final SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            group = securityManager.getThreadGroup();
        } else {
            group = Thread.currentThread().getThreadGroup();
        }
    }

    public DaemonThreadFactory(final Class... clazz) {
        this(asStrings(clazz));
    }

    private static Object[] asStrings(final Class[] clazz) {
        final String[] strings = new String[clazz.length];
        int i = 0;
        for (final Class c : clazz) {
            strings[i++] = c.getSimpleName();
        }
        return strings;
    }

    @Override
    public Thread newThread(final Runnable runnable) {
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final ClassLoader classLoader = ParentClassLoaderFinder.Helper.get();
        final Thread thread1 = Thread.currentThread();
        if (thread1 == null) {
            throw new NullPointerException("Attempting to set context classloader on null thread");
        }

        if (classLoader == null) {
            throw new NullPointerException("Attempting to set null context classloader thread");
        }

        final ClassLoader oldClassLoader1 = thread1.getContextClassLoader();

        if ((System.getSecurityManager() != null)) {
            PrivilegedAction<Void> pa1 = new PrivilegedAction<Void>() {
                private final ClassLoader cl = classLoader;
                private final Thread t = thread1;

                @Override
                public Void run() {
                    t.setContextClassLoader(cl);
                    return null;
                }
            };
            AccessController.doPrivileged(pa1);
        } else {
            thread1.setContextClassLoader(classLoader);
        }

        try {
            final Thread thread = new Thread(group, runnable, name + " - " + ids.incrementAndGet());
            if (!thread.isDaemon()) {
                thread.setDaemon(true);
            }
            if (thread.getPriority() != Thread.NORM_PRIORITY) {
                thread.setPriority(Thread.NORM_PRIORITY);
            }
            return thread;
        } finally {
            final Thread thread = Thread.currentThread();
            if (thread == null) {
                throw new NullPointerException("Attempting to set context classloader on null thread");
            }

            if (loader == null) {
                throw new NullPointerException("Attempting to set null context classloader thread");
            }

            final ClassLoader oldClassLoader = thread.getContextClassLoader();

            if ((System.getSecurityManager() != null)) {
                PrivilegedAction<Void> pa = new PrivilegedAction<Void>() {
                    private final ClassLoader cl = loader;
                    private final Thread t = thread;

                    @Override
                    public Void run() {
                        t.setContextClassLoader(cl);
                        return null;
                    }
                };
                AccessController.doPrivileged(pa);
            } else {
                thread.setContextClassLoader(loader);
            }

        }
    }
}
