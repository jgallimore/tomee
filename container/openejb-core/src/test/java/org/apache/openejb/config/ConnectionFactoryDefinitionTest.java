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
import jakarta.resource.ConnectionFactoryDefinitions;
import jakarta.resource.spi.TransactionSupport;
import org.apache.openejb.assembler.classic.OpenEjbConfiguration;
import org.apache.openejb.assembler.classic.ResourceInfo;
import org.apache.openejb.config.GreetingAdapter.GreetingConnection;
import org.apache.openejb.config.GreetingAdapter.GreetingConnectionFactory;
import org.apache.openejb.config.GreetingAdapter.GreetingManagedConnectionFactory;
import org.apache.openejb.config.GreetingAdapter.GreetingResourceAdapter;
import org.apache.openejb.jee.ConfigProperty;
import org.apache.openejb.jee.ConnectionDefinition;
import org.apache.openejb.jee.Connector;
import org.apache.openejb.jee.OutboundResourceAdapter;
import org.apache.openejb.jee.ResourceAdapter;
import org.apache.openejb.jee.TransactionSupportType;
import org.apache.openejb.junit.ApplicationComposer;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.spi.ContainerSystem;
import org.apache.openejb.testing.Module;
import org.apache.openejb.testing.SimpleLog;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link ConnectionFactoryDefinition}s backed by a connector module of the same application.
 */
@SimpleLog
@RunWith(ApplicationComposer.class)
public class ConnectionFactoryDefinitionTest {
    @EJB
    private Client client;

    @Module
    public Connector eis() {
        final ConfigProperty greeting = new ConfigProperty();
        greeting.setConfigPropertyName("greeting");
        greeting.setConfigPropertyType(String.class.getName());
        greeting.setConfigPropertyValue("bonjour");

        final ConnectionDefinition connectionDefinition = new ConnectionDefinition();
        connectionDefinition.setManagedConnectionFactoryClass(GreetingManagedConnectionFactory.class.getName());
        connectionDefinition.setConnectionFactoryInterface(GreetingConnectionFactory.class.getName());
        connectionDefinition.setConnectionFactoryImplClass(GreetingAdapter.GreetingConnectionFactoryImpl.class.getName());
        connectionDefinition.setConnectionInterface(GreetingConnection.class.getName());
        connectionDefinition.setConnectionImplClass(GreetingAdapter.GreetingConnectionImpl.class.getName());
        connectionDefinition.getConfigProperty().add(greeting);

        final OutboundResourceAdapter outbound = new OutboundResourceAdapter();
        outbound.setTransactionSupport(TransactionSupportType.NO_TRANSACTION);
        outbound.getConnectionDefinition().add(connectionDefinition);

        final ResourceAdapter resourceAdapter = new ResourceAdapter();
        resourceAdapter.setResourceAdapterClass(GreetingResourceAdapter.class.getName());
        resourceAdapter.setOutboundResourceAdapter(outbound);

        final Connector connector = new Connector();
        connector.setVersion("1.7");
        connector.setResourceAdapter(resourceAdapter);
        return connector;
    }

    @Module
    public Class<?>[] app() {
        return new Class<?>[]{Client.class, MoreDefinitions.class};
    }

    @Test
    public void injectedWithPortableName() throws Exception {
        final GreetingConnectionFactory cf = client.getApp();
        assertNotNull(cf);
        assertTrue(cf instanceof GreetingAdapter.GreetingConnectionFactoryImpl);
        try (final GreetingConnection connection = cf.getConnection()) {
            assertEquals("hola", connection.greet()); // definition property overrides the connection definition one
            assertTrue(connection.resourceAdapter() instanceof GreetingResourceAdapter);
        }
        assertEquals("hola", client.greetFromApp());
    }

    @Test
    public void plural() throws Exception {
        try (final GreetingConnection connection = client.getGlobal().getConnection()) {
            assertEquals("bonjour", connection.greet()); // default of the connection definition
            assertTrue(connection.resourceAdapter() instanceof GreetingResourceAdapter);
        }
        try (final GreetingConnection connection = client.getModule().getConnection()) {
            assertEquals("bonjour", connection.greet());
        }

        final Object global = SystemInstance.get().getComponent(ContainerSystem.class).getJNDIContext().lookup("java:global/eis/cf2");
        assertTrue(global instanceof GreetingConnectionFactory);
    }

    @Test
    public void properties() {
        final Properties single = findResource("app/eis/cf").properties;
        assertEquals(GreetingManagedConnectionFactory.class.getName(), findResource("app/eis/cf").className);
        assertEquals("7", single.getProperty("PoolMaxSize"));
        assertEquals("2", single.getProperty("PoolMinSize"));
        assertEquals("local", single.getProperty("TransactionSupport"));
        assertEquals("hola", single.getProperty("greeting"));
        assertEquals("eisRA", single.getProperty("ResourceAdapter"));
        assertTrue(findResource("app/eis/cf").types.contains(GreetingConnectionFactory.class.getName()));

        final Properties defaults = findResource("global/eis/cf2").properties;
        assertFalse(defaults.containsKey("PoolMaxSize"));
        assertFalse(defaults.containsKey("PoolMinSize"));
        assertEquals("none", defaults.getProperty("TransactionSupport"));
        assertEquals("bonjour", defaults.getProperty("greeting"));
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
        name = "java:app/eis/cf",
        description = "a connection factory of the eis connector",
        interfaceName = "org.apache.openejb.config.GreetingAdapter$GreetingConnectionFactory",
        resourceAdapter = "eis",
        transactionSupport = TransactionSupport.TransactionSupportLevel.LocalTransaction,
        maxPoolSize = 7,
        minPoolSize = 2,
        properties = {"greeting=hola", "unknownProperty=ignored"})
    @Singleton
    public static class Client {
        @Resource(lookup = "java:app/eis/cf")
        private GreetingConnectionFactory app;

        @Resource(lookup = "java:global/eis/cf2")
        private GreetingConnectionFactory global;

        @Resource(lookup = "java:module/eis/cf3")
        private GreetingConnectionFactory module;

        public GreetingConnectionFactory getApp() {
            return app;
        }

        public GreetingConnectionFactory getGlobal() {
            return global;
        }

        public GreetingConnectionFactory getModule() {
            return module;
        }

        public String greetFromApp() throws Exception { // in a transaction
            try (final GreetingConnection connection = app.getConnection()) {
                return connection.greet();
            }
        }
    }

    @ConnectionFactoryDefinitions({
        @ConnectionFactoryDefinition(
            name = "java:global/eis/cf2",
            interfaceName = "org.apache.openejb.config.GreetingAdapter$GreetingConnectionFactory",
            resourceAdapter = "eis"),
        @ConnectionFactoryDefinition(
            name = "java:module/eis/cf3",
            interfaceName = "org.apache.openejb.config.GreetingAdapter$GreetingConnectionFactory",
            resourceAdapter = "eisRA")
    })
    @Singleton
    public static class MoreDefinitions {
    }
}
