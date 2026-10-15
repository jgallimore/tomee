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

import jakarta.annotation.Resource;
import jakarta.ejb.EJB;
import jakarta.ejb.Singleton;
import jakarta.resource.ConnectionFactoryDefinition;
import org.apache.openejb.assembler.classic.OpenEjbConfiguration;
import org.apache.openejb.assembler.classic.ResourceInfo;
import org.apache.openejb.config.GreetingAdapter.GreetingConnection;
import org.apache.openejb.config.GreetingAdapter.GreetingConnectionFactory;
import org.apache.openejb.config.GreetingAdapter.GreetingManagedConnectionFactory;
import org.apache.openejb.config.GreetingAdapter.GreetingResourceAdapter;
import org.apache.openejb.config.sys.Resources;
import org.apache.openejb.jee.ConnectionFactoryResource;
import org.apache.openejb.jee.EjbJar;
import org.apache.openejb.jee.SingletonBean;
import org.apache.openejb.jee.TransactionSupportType;
import org.apache.openejb.junit.ApplicationComposer;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.testing.Classes;
import org.apache.openejb.testing.Module;
import org.apache.openejb.testing.SimpleLog;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The resource adapter is a {@code <Resource>} of the application (as in a resources.xml) and the
 * managed connection factory is found through its {@link jakarta.resource.spi.ConnectionDefinition} annotation.
 * Also covers the {@code <connection-factory>} deployment descriptor element.
 */
@SimpleLog
@RunWith(ApplicationComposer.class)
public class ConnectionFactoryDefinitionResourceAdapterResourceTest {
    @EJB
    private AnnotationClient annotationClient;

    @EJB
    private DescriptorClient descriptorClient;

    @Module
    public Resources resources() {
        final org.apache.openejb.config.sys.Resource resourceAdapter = new org.apache.openejb.config.sys.Resource("GreetingRA");
        resourceAdapter.setClassName(GreetingResourceAdapter.class.getName());

        final Resources resources = new Resources();
        resources.getResource().add(resourceAdapter);
        return resources;
    }

    @Module
    @Classes(value = {AnnotationClient.class, GreetingManagedConnectionFactory.class})
    public EjbJar app() {
        final ConnectionFactoryResource connectionFactory = new ConnectionFactoryResource();
        connectionFactory.setName("java:app/xml/cf");
        connectionFactory.setInterfaceName(GreetingConnectionFactory.class.getName());
        connectionFactory.setResourceAdapter("GreetingRA");
        connectionFactory.setTransactionSupport(TransactionSupportType.LOCAL_TRANSACTION);
        connectionFactory.setMaxPoolSize(3);
        connectionFactory.property("greeting", "ciao");

        final SingletonBean bean = new SingletonBean(DescriptorClient.class);
        bean.getConnectionFactories().add(connectionFactory);

        final EjbJar ejbJar = new EjbJar("app");
        ejbJar.addEnterpriseBean(bean);
        return ejbJar;
    }

    @Test
    public void annotation() throws Exception {
        final GreetingConnectionFactory cf = annotationClient.getCf();
        assertNotNull(cf);
        try (final GreetingConnection connection = cf.getConnection()) {
            assertEquals("hello", connection.greet()); // default of the managed connection factory
            assertTrue(connection.resourceAdapter() instanceof GreetingResourceAdapter);
        }
    }

    @Test
    public void descriptor() throws Exception {
        final GreetingConnectionFactory cf = descriptorClient.getCf();
        assertNotNull(cf);
        try (final GreetingConnection connection = cf.getConnection()) {
            assertEquals("ciao", connection.greet());
            assertTrue(connection.resourceAdapter() instanceof GreetingResourceAdapter);
        }

        final ResourceInfo info = findResource("app/xml/cf");
        assertEquals(GreetingManagedConnectionFactory.class.getName(), info.className);
        assertEquals("local", info.properties.getProperty("TransactionSupport"));
        assertEquals("3", info.properties.getProperty("PoolMaxSize"));
        assertTrue(info.properties.getProperty("ResourceAdapter").endsWith("GreetingRA"));
    }

    private static ResourceInfo findResource(final String jndiName) {
        for (final ResourceInfo info : SystemInstance.get().getComponent(OpenEjbConfiguration.class).facilities.resources) {
            if (jndiName.equals(info.jndiName)) {
                return info;
            }
        }
        fail("No resource bound to " + jndiName);
        return null;
    }

    @ConnectionFactoryDefinition(
        name = "java:app/annotation/cf",
        interfaceName = "org.apache.openejb.config.GreetingAdapter$GreetingConnectionFactory",
        resourceAdapter = "GreetingRA")
    @Singleton
    public static class AnnotationClient {
        @Resource(lookup = "java:app/annotation/cf")
        private GreetingConnectionFactory cf;

        public GreetingConnectionFactory getCf() {
            return cf;
        }
    }

    public static class DescriptorClient {
        @Resource(lookup = "java:app/xml/cf")
        private GreetingConnectionFactory cf;

        public GreetingConnectionFactory getCf() {
            return cf;
        }
    }
}
