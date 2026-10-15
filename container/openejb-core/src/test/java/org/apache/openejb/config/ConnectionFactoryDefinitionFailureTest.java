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
package org.apache.openejb.config;

import jakarta.ejb.Singleton;
import jakarta.resource.ConnectionFactoryDefinition;
import org.apache.openejb.OpenEJBException;
import org.apache.openejb.config.GreetingAdapter.GreetingManagedConnectionFactory;
import org.apache.openejb.config.GreetingAdapter.GreetingResourceAdapter;
import org.apache.openejb.config.sys.Resources;
import org.apache.openejb.testing.ApplicationComposers;
import org.apache.openejb.testing.Module;
import org.apache.openejb.testing.SimpleLog;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A {@link ConnectionFactoryDefinition} which can't be satisfied must fail the deployment.
 */
public class ConnectionFactoryDefinitionFailureTest {
    @Test
    public void unknownInterface() throws Exception {
        final Throwable error = deploy(UnknownInterfaceApp.class);
        final OpenEJBException definitionError = find(error);
        assertNotNull("deployment should fail with an OpenEJBException, got " + error, definitionError);
        assertTrue(definitionError.getMessage(), definitionError.getMessage().contains("java:app/unknown/cf"));
        assertTrue(definitionError.getMessage(), definitionError.getMessage().contains("GreetingRA"));
        assertTrue(definitionError.getMessage(), definitionError.getMessage().contains("org.acme.UnknownConnectionFactory"));
    }

    @Test
    public void unknownResourceAdapter() throws Exception {
        final Throwable error = deploy(UnknownResourceAdapterApp.class);
        assertNotNull("deployment should fail", error);
        assertTrue(String.valueOf(error), messages(error).contains("MissingRA"));
    }

    private static Throwable deploy(final Class<?> app) throws Exception {
        final ApplicationComposers composers = new ApplicationComposers(app);
        try {
            composers.before(app.getDeclaredConstructor().newInstance());
            fail("deployment of " + app.getSimpleName() + " should fail");
            return null;
        } catch (final Exception e) {
            return e;
        } finally {
            composers.after();
        }
    }

    private static OpenEJBException find(final Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof OpenEJBException && current.getMessage() != null
                && current.getMessage().startsWith("Can't create connection factory definition")) {
                return (OpenEJBException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String messages(final Throwable error) {
        final StringBuilder builder = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            builder.append(current.getMessage()).append('\n');
            current = current.getCause();
        }
        return builder.toString();
    }

    private static Resources greetingResourceAdapter() {
        final org.apache.openejb.config.sys.Resource resourceAdapter = new org.apache.openejb.config.sys.Resource("GreetingRA");
        resourceAdapter.setClassName(GreetingResourceAdapter.class.getName());

        final Resources resources = new Resources();
        resources.getResource().add(resourceAdapter);
        return resources;
    }

    @SimpleLog
    public static class UnknownInterfaceApp {
        @Module
        public Resources resources() {
            return greetingResourceAdapter();
        }

        @Module
        public Class<?>[] app() {
            return new Class<?>[]{UnknownInterfaceBean.class, GreetingManagedConnectionFactory.class};
        }
    }

    @ConnectionFactoryDefinition(
        name = "java:app/unknown/cf",
        interfaceName = "org.acme.UnknownConnectionFactory",
        resourceAdapter = "GreetingRA")
    @Singleton
    public static class UnknownInterfaceBean {
    }

    @SimpleLog
    public static class UnknownResourceAdapterApp {
        @Module
        public Class<?>[] app() {
            return new Class<?>[]{UnknownResourceAdapterBean.class, GreetingManagedConnectionFactory.class};
        }
    }

    @ConnectionFactoryDefinition(
        name = "java:app/missing/cf",
        interfaceName = "org.apache.openejb.config.GreetingAdapter$GreetingConnectionFactory",
        resourceAdapter = "MissingRA")
    @Singleton
    public static class UnknownResourceAdapterBean {
    }
}
