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

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ActivationSpec;
import jakarta.resource.spi.BootstrapContext;
import jakarta.resource.spi.ConnectionDefinition;
import jakarta.resource.spi.ConnectionEvent;
import jakarta.resource.spi.ConnectionEventListener;
import jakarta.resource.spi.ConnectionManager;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.LocalTransaction;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionFactory;
import jakarta.resource.spi.ManagedConnectionMetaData;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.ResourceAdapterAssociation;
import jakarta.resource.spi.endpoint.MessageEndpointFactory;
import javax.security.auth.Subject;
import javax.transaction.xa.XAResource;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A minimal resource adapter used by the {@link jakarta.resource.ConnectionFactoryDefinition} tests:
 * connections greet with the {@code greeting} property of their managed connection factory.
 */
public final class GreetingAdapter {
    private GreetingAdapter() {
        // no-op
    }

    public interface GreetingConnectionFactory {
        GreetingConnection getConnection() throws ResourceException;
    }

    public interface GreetingConnection extends AutoCloseable {
        String greet();

        ResourceAdapter resourceAdapter();

        @Override
        void close();
    }

    public static class GreetingResourceAdapter implements ResourceAdapter {
        @Override
        public void start(final BootstrapContext ctx) {
            // no-op
        }

        @Override
        public void stop() {
            // no-op
        }

        @Override
        public void endpointActivation(final MessageEndpointFactory endpointFactory, final ActivationSpec spec) {
            // no-op
        }

        @Override
        public void endpointDeactivation(final MessageEndpointFactory endpointFactory, final ActivationSpec spec) {
            // no-op
        }

        @Override
        public XAResource[] getXAResources(final ActivationSpec[] specs) {
            return new XAResource[0];
        }
    }

    @ConnectionDefinition(connectionFactory = GreetingConnectionFactory.class,
        connectionFactoryImpl = GreetingConnectionFactoryImpl.class,
        connection = GreetingConnection.class,
        connectionImpl = GreetingConnectionImpl.class)
    public static class GreetingManagedConnectionFactory implements ManagedConnectionFactory, ResourceAdapterAssociation {
        private String greeting = "hello";
        private ResourceAdapter resourceAdapter;
        private PrintWriter logWriter;

        public String getGreeting() {
            return greeting;
        }

        public void setGreeting(final String greeting) {
            this.greeting = greeting;
        }

        @Override
        public ResourceAdapter getResourceAdapter() {
            return resourceAdapter;
        }

        @Override
        public void setResourceAdapter(final ResourceAdapter resourceAdapter) {
            this.resourceAdapter = resourceAdapter;
        }

        @Override
        public Object createConnectionFactory(final ConnectionManager cxManager) {
            return new GreetingConnectionFactoryImpl(this, cxManager);
        }

        @Override
        public Object createConnectionFactory() throws ResourceException {
            throw new ResourceException("Only managed environments are supported");
        }

        @Override
        public ManagedConnection createManagedConnection(final Subject subject, final ConnectionRequestInfo cxRequestInfo) {
            return new GreetingManagedConnection(this);
        }

        @Override
        public ManagedConnection matchManagedConnections(final Set connectionSet, final Subject subject, final ConnectionRequestInfo cxRequestInfo) {
            final Iterator<?> it = connectionSet.iterator();
            while (it.hasNext()) {
                final Object connection = it.next();
                if (connection instanceof GreetingManagedConnection) {
                    return (ManagedConnection) connection;
                }
            }
            return null;
        }

        @Override
        public void setLogWriter(final PrintWriter out) {
            this.logWriter = out;
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }
    }

    public static class GreetingConnectionFactoryImpl implements GreetingConnectionFactory, Serializable {
        private final transient GreetingManagedConnectionFactory mcf;
        private final transient ConnectionManager connectionManager;

        public GreetingConnectionFactoryImpl(final GreetingManagedConnectionFactory mcf, final ConnectionManager connectionManager) {
            this.mcf = mcf;
            this.connectionManager = connectionManager;
        }

        @Override
        public GreetingConnection getConnection() throws ResourceException {
            return (GreetingConnection) connectionManager.allocateConnection(mcf, null);
        }
    }

    public static class GreetingConnectionImpl implements GreetingConnection {
        private final GreetingManagedConnection managedConnection;

        public GreetingConnectionImpl(final GreetingManagedConnection managedConnection) {
            this.managedConnection = managedConnection;
        }

        @Override
        public String greet() {
            return managedConnection.mcf.getGreeting();
        }

        @Override
        public ResourceAdapter resourceAdapter() {
            return managedConnection.mcf.getResourceAdapter();
        }

        @Override
        public void close() {
            managedConnection.closed(this);
        }
    }

    public static class GreetingManagedConnection implements ManagedConnection {
        private final GreetingManagedConnectionFactory mcf;
        private final List<ConnectionEventListener> listeners = new CopyOnWriteArrayList<>();
        private PrintWriter logWriter;

        public GreetingManagedConnection(final GreetingManagedConnectionFactory mcf) {
            this.mcf = mcf;
        }

        void closed(final GreetingConnection connection) {
            final ConnectionEvent event = new ConnectionEvent(this, ConnectionEvent.CONNECTION_CLOSED);
            event.setConnectionHandle(connection);
            for (final ConnectionEventListener listener : listeners) {
                listener.connectionClosed(event);
            }
        }

        @Override
        public Object getConnection(final Subject subject, final ConnectionRequestInfo cxRequestInfo) {
            return new GreetingConnectionImpl(this);
        }

        @Override
        public void destroy() {
            // no-op
        }

        @Override
        public void cleanup() {
            // no-op
        }

        @Override
        public void associateConnection(final Object connection) {
            // no-op
        }

        @Override
        public void addConnectionEventListener(final ConnectionEventListener listener) {
            listeners.add(listener);
        }

        @Override
        public void removeConnectionEventListener(final ConnectionEventListener listener) {
            listeners.remove(listener);
        }

        @Override
        public XAResource getXAResource() throws ResourceException {
            throw new ResourceException("XA is not supported");
        }

        @Override
        public LocalTransaction getLocalTransaction() {
            return new LocalTransaction() {
                @Override
                public void begin() {
                    // no-op
                }

                @Override
                public void commit() {
                    // no-op
                }

                @Override
                public void rollback() {
                    // no-op
                }
            };
        }

        @Override
        public ManagedConnectionMetaData getMetaData() throws ResourceException {
            throw new ResourceException("No metadata");
        }

        @Override
        public void setLogWriter(final PrintWriter out) {
            this.logWriter = out;
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }
    }
}
