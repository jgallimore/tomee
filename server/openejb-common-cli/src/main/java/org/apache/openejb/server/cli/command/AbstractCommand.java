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
package org.apache.openejb.server.cli.command;

import org.apache.openejb.core.LocalInitialContextFactory;
import org.apache.openejb.server.cli.StreamManager;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Properties;

public abstract class AbstractCommand {
    protected StreamManager streamManager;
    protected String command;

    public abstract void execute(final String cmd);

    public void setStreamManager(StreamManager streamManager) {
        this.streamManager = streamManager;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public <T> T lookup(final Class<T> clazz, final String jndiName) throws NamingException {
        Properties p = new Properties();
        p.setProperty(Context.INITIAL_CONTEXT_FACTORY, LocalInitialContextFactory.class.getName());

        ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
        final ClassLoader classLoader = getClass().getClassLoader();
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
            return (T) new InitialContext(p).lookup(jndiName);
        } finally {
            final Thread thread = Thread.currentThread();
            if (thread == null) {
                throw new NullPointerException("Attempting to set context classloader on null thread");
            }

            if (oldCl == null) {
                throw new NullPointerException("Attempting to set null context classloader thread");
            }

            final ClassLoader oldClassLoader = thread.getContextClassLoader();

            if ((System.getSecurityManager() != null)) {
                PrivilegedAction<Void> pa = new PrivilegedAction<Void>() {
                    private final ClassLoader cl = oldCl;
                    private final Thread t = thread;

                    @Override
                    public Void run() {
                        t.setContextClassLoader(cl);
                        return null;
                    }
                };
                AccessController.doPrivileged(pa);
            } else {
                thread.setContextClassLoader(oldCl);
            }

        }
    }
}
