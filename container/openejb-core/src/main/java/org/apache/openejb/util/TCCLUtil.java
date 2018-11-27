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

import java.security.AccessController;
import java.security.PrivilegedAction;

public class TCCLUtil {

    public static ClassLoader setThreadContextClassLoader(final Thread thread, final ClassLoader classLoader) {
        if (thread == null) {
            throw new NullPointerException("Attempting to set context classloader on null thread");
        }

        if (classLoader == null) {
            throw new NullPointerException("Attempting to set null context classloader thread");
        }

        final ClassLoader oldClassLoader = thread.getContextClassLoader();

        if ((System.getSecurityManager() != null)) {
            PrivilegedAction<Void> pa = new PrivilegedAction<Void>() {
                private final ClassLoader cl = classLoader;
                private final Thread t = thread;

                @Override
                public Void run() {
                    t.setContextClassLoader(cl);
                    return null;
                }
            };
            AccessController.doPrivileged(pa);
        } else {
            thread.setContextClassLoader(classLoader);
        }

        return oldClassLoader;
    }

    public static ClassLoader setThreadContextClassLoader(final ClassLoader classLoader) {
        final Thread thread = Thread.currentThread();
		if (thread == null) {
		    throw new NullPointerException("Attempting to set context classloader on null thread");
		}
		
		if (classLoader == null) {
		    throw new NullPointerException("Attempting to set null context classloader thread");
		}
		
		final ClassLoader oldClassLoader = thread.getContextClassLoader();
		
		if ((System.getSecurityManager() != null)) {
		    PrivilegedAction<Void> pa = new PrivilegedAction<Void>() {
		        private final ClassLoader cl = classLoader;
		        private final Thread t = thread;
		
		        @Override
		        public Void run() {
		            t.setContextClassLoader(cl);
		            return null;
		        }
		    };
		    AccessController.doPrivileged(pa);
		} else {
		    thread.setContextClassLoader(classLoader);
		}
		
		return oldClassLoader;
    }

}
