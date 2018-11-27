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

package org.apache.openejb.assembler.classic;

import org.apache.openejb.AppContext;
import org.apache.openejb.BeanContext;
import org.apache.openejb.Container;
import org.apache.openejb.Injection;
import org.apache.openejb.ModuleContext;
import org.apache.openejb.ModuleTestContext;
import org.apache.openejb.OpenEJBException;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import javax.naming.Context;

/**
 * @version $Revision$ $Date$
 */
public class EjbJarBuilder {
    private final Properties props;
    private final AppContext context;

    public EjbJarBuilder(final Properties props, final AppContext context) {
        this.props = props;
        this.context = context;
    }

    public HashMap<String, BeanContext> build(final EjbJarInfo ejbJar, final Collection<Injection> appInjections, final ClassLoader classLoader) throws OpenEJBException {
        final InjectionBuilder injectionBuilder = new InjectionBuilder(classLoader);
        final List<Injection> moduleInjections = injectionBuilder.buildInjections(ejbJar.moduleJndiEnc);
        moduleInjections.addAll(appInjections);
        final Context moduleJndiContext = new JndiEncBuilder(ejbJar.moduleJndiEnc, moduleInjections, null, ejbJar.moduleName, ejbJar.moduleUri, ejbJar.uniqueId, classLoader, context.getProperties())
            .build(JndiEncBuilder.JndiScope.module);

        final HashMap<String, BeanContext> deployments = new HashMap<String, BeanContext>();

        final ModuleContext moduleContext = !ejbJar.properties.containsKey("openejb.test.module") ?
                new ModuleContext(ejbJar.moduleName, ejbJar.moduleUri, ejbJar.uniqueId, context, moduleJndiContext, classLoader) :
                new ModuleTestContext(ejbJar.moduleName, ejbJar.moduleUri, ejbJar.uniqueId, context, moduleJndiContext, classLoader);
        moduleContext.getProperties().putAll(ejbJar.properties);
        final InterceptorBindingBuilder interceptorBindingBuilder = new InterceptorBindingBuilder(classLoader, ejbJar);

        final MethodScheduleBuilder methodScheduleBuilder = new MethodScheduleBuilder();

        for (final EnterpriseBeanInfo ejbInfo : ejbJar.enterpriseBeans) {
            final ClassLoader loader = Thread.currentThread().getContextClassLoader();
            final ClassLoader classLoader1 = moduleContext.getClassLoader();
            final Thread thread1 = Thread.currentThread();
            if (thread1 == null) {
                throw new NullPointerException("Attempting to set context classloader on null thread");
            }

            if (classLoader1 == null) {
                throw new NullPointerException("Attempting to set null context classloader thread");
            }

            final ClassLoader oldClassLoader1 = thread1.getContextClassLoader();

            if ((System.getSecurityManager() != null)) {
                PrivilegedAction<Void> pa1 = new PrivilegedAction<Void>() {
                    private final ClassLoader cl = classLoader1;
                    private final Thread t = thread1;

                    @Override
                    public Void run() {
                        t.setContextClassLoader(cl);
                        return null;
                    }
                };
                AccessController.doPrivileged(pa1);
            } else {
                thread1.setContextClassLoader(classLoader1);
            }

            try {
                final EnterpriseBeanBuilder deploymentBuilder = new EnterpriseBeanBuilder(ejbInfo, moduleContext, moduleInjections);
                final BeanContext bean = deploymentBuilder.build();

                interceptorBindingBuilder.build(bean, ejbInfo);

                methodScheduleBuilder.build(bean, ejbInfo);

                deployments.put(ejbInfo.ejbDeploymentId, bean);

                // TODO: replace with get() on application context or parent
                final Container container = (Container) props.get(ejbInfo.containerId);

                if (container == null) {
                    throw new IllegalStateException("Container does not exist: " + ejbInfo.containerId + ".  Referenced by deployment: " + bean.getDeploymentID());
                }
                // Don't deploy to the container, yet. That will be done by deploy() once Assembler as finished configuring the DeploymentInfo
                bean.setContainer(container);
            } catch (final Throwable e) {
                throw new OpenEJBException("Error building bean '" + ejbInfo.ejbName + "'.  Exception: " + e.getClass() + ": " + e.getMessage(), e);
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
        return deployments;
    }

}
