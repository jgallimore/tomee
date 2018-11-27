/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.openejb.util;

import java.lang.reflect.Field;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permission;
import java.security.ProtectionDomain;

public class DebugSecurityManager extends SecurityManager {

    private static final ThreadLocal<Boolean> IN_CHECK_PERM = new ThreadLocal<>();

    @Override
    public void checkPermission(Permission perm) {

        if (Boolean.TRUE.equals(IN_CHECK_PERM.get())) {
            // we allow org.tomitribe.secmgr.TrackingSecurityManager.checkPermission(java.security.Permission)
            // to do anything
            return;
        }

        try {
            // prevent stack overflow on recursion
            IN_CHECK_PERM.set(Boolean.TRUE);
            super.checkPermission(perm);
        } catch (SecurityException e) {
            AccessControlContext stack = AccessController.getContext();

            ProtectionDomain[] context = getContext(stack);
            // find the domain that failed
            if (context != null) {
                for (final ProtectionDomain protectionDomain : context) {

                    // if we have a domain that *passed* and its on the list, then that code needs wrapping in
                    // AccessController.doPrivileged(new PrivilegedAction<String>() {
                    //            @Override
                    //            public String run() {
                    //                return System.getProperty("user.home");
                    //            }
                    //        });

                    if (protectionDomain.implies(perm)) {
                        // somehow we need to walk the stack to find this

                        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                        for (StackTraceElement stackTraceElement : stackTrace) {

                            final String className = stackTraceElement.getClassName();

                            // attempt to load the class
                            try {
                                Class clazz = Class.forName(className);
                                if (protectionDomain.equals(clazz.getProtectionDomain())) {
                                    // this is possibly it
                                    System.out.println("ACCESS_CONTROL issue - " + stackTraceElement.getClassName() +  "." + stackTraceElement.getMethodName()
                                        + "(" + stackTraceElement.getFileName() + ":" + stackTraceElement.getLineNumber() + ")");

                                }
                            } catch (ClassNotFoundException e1) {
                                // ignore
                            }
                        }

                    }


                    if (! protectionDomain.implies(perm)) {
                        // this is where we failed
                        System.out.println("PERMISSION_ISSUE - " + protectionDomain.getCodeSource().getLocation().toString() + " - " + perm.toString());
                    }
                }
            }

        } finally {
            IN_CHECK_PERM.remove();
        }
    }

    private ProtectionDomain[] getContext(AccessControlContext stack) {
        ProtectionDomain[] context = null;

        try {
            final Field contextField = AccessControlContext.class.getDeclaredField("context");
            contextField.setAccessible(true);
            final Object o = contextField.get(stack);

            if (ProtectionDomain[].class.isInstance(o)) {
                context = ProtectionDomain[].class.cast(o);
            }
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            // ignore
        }

        return context;
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
        super.checkPermission(perm, context);
    }
}
