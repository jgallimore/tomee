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

package org.apache.openejb.log;

import org.apache.openejb.core.ParentClassLoaderFinder;
import org.apache.openejb.loader.SystemInstance;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggerCreator implements Callable<Logger> {
    private final String name;
    private Logger logger;
    private volatile boolean init;

    public LoggerCreator(final String channel) {
        name = channel;

        // force eager init if config overrided
        final Properties p = SystemInstance.get().getProperties();
        final String levelName = p.getProperty("logging.level." + channel);
        if (levelName != null) {
            try {
                call();
            } catch (final Exception e) {
                // no-op
            }
        }
    }

    @Override
    public Logger call() throws Exception {
        if (logger == null) {
            synchronized (this) { // no need of lock for this part
                if (logger == null) {
                    final Thread thread = Thread.currentThread();
                    final ClassLoader originalLoader = thread.getContextClassLoader();
                    final ClassLoader classLoader = ParentClassLoaderFinder.Helper.get();
                    final Thread thread2 = Thread.currentThread();
                    if (thread2 == null) {
                        throw new NullPointerException("Attempting to set context classloader on null thread");
                    }

                    if (classLoader == null) {
                        throw new NullPointerException("Attempting to set null context classloader thread");
                    }

                    final ClassLoader oldClassLoader1 = thread2.getContextClassLoader();

                    if ((System.getSecurityManager() != null)) {
                        PrivilegedAction<Void> pa1 = new PrivilegedAction<Void>() {
                            private final ClassLoader cl = classLoader;
                            private final Thread t = thread2;

                            @Override
                            public Void run() {
                                t.setContextClassLoader(cl);
                                return null;
                            }
                        };
                        AccessController.doPrivileged(pa1);
                    } else {
                        thread2.setContextClassLoader(classLoader);
                    }

                    try {
                        try {
                            logger = Logger.getLogger(name);
                        } catch (final Exception e) {
                            logger = Logger.getLogger(name); // try again
                        }

                        // if level set through properties force it
                        final Properties p = SystemInstance.get().getProperties();
                        final String levelName = p.getProperty("logging.level." + logger.getName());
                        if (levelName != null) {
                            final Level level = Level.parse(levelName);
                            for (final Handler handler : logger.getHandlers()) {
                                handler.setLevel(level);
                            }
                        }
                    } finally {
                        final Thread thread1 = Thread.currentThread();
                        if (thread1 == null) {
                            throw new NullPointerException("Attempting to set context classloader on null thread");
                        }

                        if (originalLoader == null) {
                            throw new NullPointerException("Attempting to set null context classloader thread");
                        }

                        final ClassLoader oldClassLoader = thread1.getContextClassLoader();

                        if ((System.getSecurityManager() != null)) {
                            PrivilegedAction<Void> pa = new PrivilegedAction<Void>() {
                                private final ClassLoader cl = originalLoader;
                                private final Thread t = thread1;

                                @Override
                                public Void run() {
                                    t.setContextClassLoader(cl);
                                    return null;
                                }
                            };
                            AccessController.doPrivileged(pa);
                        } else {
                            thread1.setContextClassLoader(originalLoader);
                        }

                    }
                }
            }
        }
        return logger;
    }

    public boolean isInit() {
        return init;
    }

    public static final class Get {
        private Get() {
            // no-op
        }

        private static Logger exec(final LoggerCreator creator) {
            final ClassLoader old = Thread.currentThread().getContextClassLoader();
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
                return creator.call();
            } catch (final Exception e) { // shouldn't occur regarding the impl we use
                return Logger.getLogger("default");
            } finally {
                final Thread thread = Thread.currentThread();
                if (thread == null) {
                    throw new NullPointerException("Attempting to set context classloader on null thread");
                }

                if (old == null) {
                    throw new NullPointerException("Attempting to set null context classloader thread");
                }

                final ClassLoader oldClassLoader = thread.getContextClassLoader();

                if ((System.getSecurityManager() != null)) {
                    PrivilegedAction<Void> pa = new PrivilegedAction<Void>() {
                        private final ClassLoader cl = old;
                        private final Thread t = thread;

                        @Override
                        public Void run() {
                            t.setContextClassLoader(cl);
                            return null;
                        }
                    };
                    AccessController.doPrivileged(pa);
                } else {
                    thread.setContextClassLoader(old);
                }

            }
        }

        public static Logger exec(final LoggerCreator logger, final AtomicBoolean debug, final AtomicBoolean info) {
            final Logger l = exec(logger);
            if (!logger.init) {
                levels(logger, debug, info);
            }
            return l;
        }

        public static void levels(final LoggerCreator lc, final AtomicBoolean debug, final AtomicBoolean info) {
            if (lc.init) {
                return;
            }

            final Logger l;
            try {
                l = lc.call();
            } catch (final Exception e) {
                return;
            }

            debug.set(l.isLoggable(Level.FINE));
            info.set(l.isLoggable(Level.INFO));
            lc.init = true;
        }
    }
}
