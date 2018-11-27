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

package org.apache.openejb.util.classloader;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;

public final class ClassLoaderAwareHandler implements InvocationHandler {
    private final Object delegate;
    private final ClassLoader loader;
    private final String toString;

    public ClassLoaderAwareHandler(final String toString, final Object delegate, final ClassLoader loader) {
        this.delegate = delegate;
        this.loader = loader;
        this.toString = toString;
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        if (toString != null && method.getName().equals("toString")) {
            return toString;
        }

        final Thread thread = Thread.currentThread();
        final ClassLoader old = thread.getContextClassLoader();
        final Thread thread2 = Thread.currentThread();
        if (thread2 == null) {
            throw new NullPointerException("Attempting to set context classloader on null thread");
        }

        if (loader == null) {
            throw new NullPointerException("Attempting to set null context classloader thread");
        }

        final ClassLoader oldClassLoader1 = thread2.getContextClassLoader();

        if ((System.getSecurityManager() != null)) {
            PrivilegedAction<Void> pa1 = new PrivilegedAction<Void>() {
                private final ClassLoader cl = loader;
                private final Thread t = thread2;

                @Override
                public Void run() {
                    t.setContextClassLoader(cl);
                    return null;
                }
            };
            AccessController.doPrivileged(pa1);
        } else {
            thread2.setContextClassLoader(loader);
        }

        try {
            return method.invoke(delegate, args);
        } catch (final InvocationTargetException e) {
            // Reflection wraps all exceptions thrown from the Method with
            // InvocationTargetException.  We must unwrap it and throw the
            // real exception otherwise TomEE/OpenEJB will see 'UndeclaredThrowableException'
            throw e.getCause();
        } finally {
            final Thread thread1 = Thread.currentThread();
            if (thread1 == null) {
                throw new NullPointerException("Attempting to set context classloader on null thread");
            }

            if (old == null) {
                throw new NullPointerException("Attempting to set null context classloader thread");
            }

            final ClassLoader oldClassLoader = thread1.getContextClassLoader();

            if ((System.getSecurityManager() != null)) {
                PrivilegedAction<Void> pa = new PrivilegedAction<Void>() {
                    private final ClassLoader cl = old;
                    private final Thread t = thread1;

                    @Override
                    public Void run() {
                        t.setContextClassLoader(cl);
                        return null;
                    }
                };
                AccessController.doPrivileged(pa);
            } else {
                thread1.setContextClassLoader(old);
            }

        }
    }
}
