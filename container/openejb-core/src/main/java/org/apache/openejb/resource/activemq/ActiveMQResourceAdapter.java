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

package org.apache.openejb.resource.activemq;

import org.apache.activemq.*;
import org.apache.activemq.advisory.DestinationSource;
import org.apache.activemq.blob.BlobTransferPolicy;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.RedeliveryPolicyMap;
import org.apache.activemq.command.*;
import org.apache.activemq.command.Message;
import org.apache.activemq.management.JMSConnectionStatsImpl;
import org.apache.activemq.management.JMSStatsImpl;
import org.apache.activemq.management.StatsImpl;
import org.apache.activemq.ra.ActiveMQConnectionRequestInfo;
import org.apache.activemq.ra.ActiveMQEndpointActivationKey;
import org.apache.activemq.ra.ActiveMQEndpointWorker;
import org.apache.activemq.ra.ActiveMQManagedConnection;
import org.apache.activemq.ra.MessageActivationSpec;
import org.apache.activemq.thread.Scheduler;
import org.apache.activemq.thread.TaskRunnerFactory;
import org.apache.activemq.transport.Transport;
import org.apache.activemq.transport.TransportListener;
import org.apache.activemq.util.IdGenerator;
import org.apache.activemq.util.LongSequenceGenerator;
import org.apache.openejb.BeanContext;
import org.apache.openejb.core.mdb.MdbContainer;
import org.apache.openejb.dyni.DynamicSubclass;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.resource.AutoConnectionTracker;
import org.apache.openejb.resource.activemq.jms2.TomEEConnectionFactory;
import org.apache.openejb.resource.activemq.jms2.TomEEManagedConnectionProxy;
import org.apache.openejb.spi.ContainerSystem;
import org.apache.openejb.util.Duration;
import org.apache.openejb.util.LogCategory;
import org.apache.openejb.util.Logger;
import org.apache.openejb.util.URISupport;
import org.apache.openejb.util.URLs;
import org.apache.openejb.util.proxy.LocalBeanProxyFactory;
import org.apache.openejb.util.reflection.Reflections;

import javax.jms.*;
import javax.jms.Queue;
import javax.management.ObjectName;
import javax.naming.NamingException;
import javax.resource.ResourceException;
import javax.resource.spi.BootstrapContext;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.resource.spi.TransactionSupport;
import java.io.IOException;
import java.io.Serializable;
import java.lang.IllegalStateException;
import java.lang.reflect.*;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("UnusedDeclaration")
public class ActiveMQResourceAdapter extends org.apache.activemq.ra.ActiveMQResourceAdapter {

    private String dataSource;
    private String useDatabaseLock;
    private String startupTimeout = "60000";
    private BootstrapContext bootstrapContext;

    private boolean shareMdbConnections = false;
    private final Map<ConnectionKey, ActiveMQConnection> sharedMdbConnections = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, Class<?>> proxies = new ConcurrentHashMap<>();

    private final Map<BeanContext, ObjectName> mbeanNames = new ConcurrentHashMap<>();
    private static final Map<String,String> PREVENT_CREATION_PARAMS = new HashMap<String, String>() { {
        put("create", "false");
    }};

    private static final Logger LOGGER = Logger.getInstance(LogCategory.ACTIVEMQ, ActiveMQ5Factory.class);

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(final String dataSource) {
        this.dataSource = dataSource;
    }

    public void setUseDatabaseLock(final String useDatabaseLock) {
        this.useDatabaseLock = useDatabaseLock;
    }

    public int getStartupTimeout() {
        return Integer.parseInt(this.startupTimeout);
    }

    public void setStartupTimeout(final Duration startupTimeout) {
        if (startupTimeout.getUnit() == null) {
            startupTimeout.setUnit(TimeUnit.MILLISECONDS);
        }
        this.startupTimeout = String.valueOf(TimeUnit.MILLISECONDS.convert(startupTimeout.getTime(), startupTimeout.getUnit()));
    }

    public boolean isShareMdbConnections() {
        return shareMdbConnections;
    }

    public void setShareMdbConnections(boolean shareMdbConnections) {
        this.shareMdbConnections = shareMdbConnections;
    }

    @Override
    public void setServerUrl(final String url) {
        try {
            final URISupport.CompositeData compositeData = URISupport.parseComposite(URLs.uri(url));
            if ("vm".equals(compositeData.getScheme())) {
                super.setServerUrl(URISupport.addParameters(URLs.uri(url), PREVENT_CREATION_PARAMS).toString());
                return;
            }
        } catch (URISyntaxException e) {
            // if we hit an exception, we'll log this and simple pass the URL we were given to ActiveMQ.
            LOGGER.error("Error occurred while processing ActiveMQ ServerUrl: " + url, e);
        }

        super.setServerUrl(url);
    }

    @Override
    public void start(final BootstrapContext bootstrapContext) throws ResourceAdapterInternalException {

        this.bootstrapContext = bootstrapContext;
        final String brokerXmlConfig = getBrokerXmlConfig();
        super.setBrokerXmlConfig(null);
        super.start(bootstrapContext);

        final Properties properties = new Properties();

        if (null != this.dataSource) {
            properties.put("DataSource", this.dataSource);
        }

        if (null != this.useDatabaseLock) {
            properties.put("UseDatabaseLock", this.useDatabaseLock);
        }

        if (null != this.startupTimeout) {
            properties.put("StartupTimeout", this.startupTimeout);
        }

        // prefix server uri with 'broker:' so our broker factory is used
        if (brokerXmlConfig != null && !brokerXmlConfig.trim().isEmpty()) {

            try {

                if (brokerXmlConfig.startsWith("broker:")) {

                    final URISupport.CompositeData compositeData = URISupport.parseComposite(URLs.uri(brokerXmlConfig));

                    if (!compositeData.getParameters().containsKey("persistent")) {
                        //Override default - Which is 'true'
                        //noinspection unchecked
                        compositeData.getParameters().put("persistent", "false");
                    }

                    if ("false".equalsIgnoreCase(compositeData.getParameters().get("persistent").toString())) {
                        properties.remove("DataSource"); // no need
                    }

                    setBrokerXmlConfig(ActiveMQFactory.getBrokerMetaFile() + compositeData.toURI());
                } else if (brokerXmlConfig.toLowerCase(Locale.ENGLISH).startsWith("xbean:")) {
                    setBrokerXmlConfig(ActiveMQFactory.getBrokerMetaFile() + brokerXmlConfig);
                }

            } catch (final URISyntaxException e) {
                throw new ResourceAdapterInternalException("Invalid BrokerXmlConfig", e);
            }

            createInternalBroker(brokerXmlConfig, properties);
        }
    }

    private void createInternalBroker(final String brokerXmlConfig, final Properties properties) {
        ActiveMQFactory.setThreadProperties(properties);

        try {
            //The returned broker should be started, but calling start is harmless.
            //We do not need to track the instance as the factory takes care of this.
            ActiveMQFactory.createBroker(URLs.uri(getBrokerXmlConfig())).start();
        } catch (final Exception e) {
            Logger.getInstance(LogCategory.OPENEJB_STARTUP, ActiveMQResourceAdapter.class).getChildLogger("service").fatal("Failed to start ActiveMQ", e);
        } finally {
            ActiveMQFactory.setThreadProperties(null);

            // reset brokerXmlConfig
            if (brokerXmlConfig != null) {
                setBrokerXmlConfig(brokerXmlConfig);
            }
        }
    }

    private ActiveMQEndpointWorker getWorker(final BeanContext beanContext) throws ResourceException {
        final Map<ActiveMQEndpointActivationKey, ActiveMQEndpointWorker> workers = Map.class.cast(Reflections.get(
                MdbContainer.class.cast(beanContext.getContainer()).getResourceAdapter(), "endpointWorkers"));
        for (final Map.Entry<ActiveMQEndpointActivationKey, ActiveMQEndpointWorker> entry : workers.entrySet()) {
            if (entry.getKey().getMessageEndpointFactory() == beanContext.getContainerData()) {
                return entry.getValue();
            }
        }
        throw new IllegalStateException("No worker for " + beanContext.getDeploymentID());
    }

    @Override
    public BootstrapContext getBootstrapContext() {
        return this.bootstrapContext;
    }

    @Override
    public void stop() {

        Logger.getInstance(LogCategory.OPENEJB_STARTUP, ActiveMQResourceAdapter.class).getChildLogger("service").info("Stopping ActiveMQ");

        final Thread stopThread = new Thread("ActiveMQResourceAdapter stop") {

            @Override
            public void run() {
                try {
                    stopImpl();
                } catch (final Throwable t) {
                    Logger.getInstance(LogCategory.OPENEJB_STARTUP, ActiveMQResourceAdapter.class).getChildLogger("service").error("ActiveMQ shutdown failed", t);
                }
            }
        };

        stopThread.setDaemon(true);
        stopThread.start();

        int timeout = 60000;

        try {
            timeout = Integer.parseInt(this.startupTimeout);
        } catch (final Throwable e) {
            //Ignore
        }

        try {
            //Block for a maximum of timeout milliseconds waiting for this thread to die.
            stopThread.join(timeout);
        } catch (final InterruptedException ex) {
            Logger.getInstance(LogCategory.OPENEJB_STARTUP, ActiveMQResourceAdapter.class).getChildLogger("service").warning("Gave up on ActiveMQ shutdown after " + timeout + "ms", ex);
        }
    }

    @Override
    public ActiveMQConnection makeConnection(final MessageActivationSpec activationSpec) throws JMSException {
        if (TomEEMessageActivationSpec.class.isInstance(activationSpec)) {
            final TomEEMessageActivationSpec s = TomEEMessageActivationSpec.class.cast(activationSpec);
            if (s.getConnectionFactoryLookup() != null) {
                try {
                    final Object lookup = SystemInstance.get().getComponent(ContainerSystem.class).getJNDIContext()
                            .lookup("openejb:Resource/" + s.getConnectionFactoryLookup());
                    if (!ActiveMQConnectionFactory.class.isInstance(lookup)) {
                        final org.apache.activemq.ra.ActiveMQConnectionFactory connectionFactory = org.apache.activemq.ra.ActiveMQConnectionFactory.class.cast(lookup);
                        Connection connection = connectionFactory.createConnection();
                        if (Proxy.isProxyClass(connection.getClass())) { // not great, we should find a better want without bypassing ra layer
                            final InvocationHandler invocationHandler = Proxy.getInvocationHandler(connection);
                            final ActiveMQConnection physicalConnection = getActiveMQConnection(activationSpec, invocationHandler);
                            if (physicalConnection != null) {
                                return physicalConnection;
                            }
                        }

                        // see if this is a dynamic subclass as opposed to a regular proxy
                        try {
                            final Field handler = connection.getClass().getDeclaredField("this$handler");
                            handler.setAccessible(true);
                            final Object o = handler.get(connection);

                            if (InvocationHandler.class.isInstance(o)) {
                                final InvocationHandler invocationHandler = InvocationHandler.class.cast(o);
                                final ActiveMQConnection physicalConnection = getActiveMQConnection(activationSpec, invocationHandler);
                                if (physicalConnection != null) {
                                    return physicalConnection;
                                }
                            }
                        } catch (NoSuchFieldException | IllegalAccessException e) {
                            // ignore, this is not a dynamic subclass
                        }

                        /*
                        final RedeliveryPolicy redeliveryPolicy = activationSpec.redeliveryPolicy();
                        if (redeliveryPolicy != null) {
                            physicalConnection.setRedeliveryPolicy(redeliveryPolicy);
                        }
                        */
                        return null;
                    }
                } catch (final ClassCastException cce) {
                    throw new java.lang.IllegalStateException(cce);
                } catch (final NamingException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        }

        if (! isShareMdbConnections()) {
            return super.makeConnection(activationSpec);
        }

        String userName = defaultValue(activationSpec.getUserName(), getInfo().getUserName());
        String password = defaultValue(activationSpec.getPassword(), getInfo().getPassword());
        String clientId = defaultValue(activationSpec.getClientId(), getInfo().getClientid());

        final ConnectionKey connectionKey = new ConnectionKey(userName, password, clientId);
        if (sharedMdbConnections.containsKey(connectionKey)) {
            return sharedMdbConnections.get(connectionKey);
        }

        final ActiveMQConnection wrappedConnection = wrap(super.makeConnection(activationSpec));
        sharedMdbConnections.put(connectionKey, wrappedConnection);
        return wrappedConnection;
    }

    private ActiveMQConnection wrap(ActiveMQConnection connection) {
        final Object wrapped = LocalBeanProxyFactory.Unsafe.allocateInstance(getProxy(connection.getClass(), connection.getClass().getClassLoader()));
        DynamicSubclass.setHandler(wrapped, new InvocationHandler() {
            @Override
            public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
                switch (method.getName()) {
                    case "toString":
                        return "Shared JMS Connection: " + connection;
                    case "setClientID":
                        // Handle setClientID method: throw exception if not compatible.
                        String currentClientId = connection.getClientID();
                        if (currentClientId != null && currentClientId.equals(args[0])) {
                            return null;
                        } else {
                            throw new IllegalStateException(
                                    "setClientID call not supported on proxy for shared Connection. " +
                                            "Set the 'clientId' property on the SingleConnectionFactory instead.");
                        }
                    case "start":
                        if (! connection.isStarted()) {
                            connection.start();
                        }

                        return null;
                    case "stop":
                        return null;
                    case "close":
                        return null;
                }

                try {
                    return method.invoke(connection, args);
                }
                catch (InvocationTargetException ex) {
                    throw ex.getTargetException();
                }
            }
        });

        return (ActiveMQConnection) wrapped;
    }

    private Class getProxy(final Class<?> cls, final ClassLoader classLoader) {
        Class<?> found = proxies.get(cls);
        if (found == null) {
            synchronized (this) {
                found = proxies.get(cls);
                if (found == null) {
                    proxies.put(cls, DynamicSubclass.createSubclass(cls, classLoader, true));
                    found = proxies.get(cls);
                }
            }
        }
        return found;
    }

    private ActiveMQConnection getActiveMQConnection(MessageActivationSpec activationSpec, InvocationHandler invocationHandler) {
        if (AutoConnectionTracker.ConnectionInvocationHandler.class.isInstance(invocationHandler)) {
            final Object handle = Reflections.get(invocationHandler, "handle");
            if (TomEEManagedConnectionProxy.class.isInstance(handle)) {
                final ActiveMQManagedConnection c = ActiveMQManagedConnection.class.cast(Reflections.get(handle, "connection"));
                final ActiveMQConnection physicalConnection = ActiveMQConnection.class.cast(Reflections.get(c, "physicalConnection"));
                final RedeliveryPolicy redeliveryPolicy = activationSpec.redeliveryPolicy();
                if (redeliveryPolicy != null) {
                    physicalConnection.setRedeliveryPolicy(redeliveryPolicy);
                }
                return physicalConnection;
            }
        }
        return null;
    }

    @Override
    protected ActiveMQConnectionFactory createConnectionFactory(final ActiveMQConnectionRequestInfo connectionRequestInfo, final MessageActivationSpec activationSpec) {
        if (TomEEMessageActivationSpec.class.isInstance(activationSpec)) {
            final TomEEMessageActivationSpec s = TomEEMessageActivationSpec.class.cast(activationSpec);
            if (s.getConnectionFactoryLookup() != null) {
                try {
                    final Object lookup = SystemInstance.get().getComponent(ContainerSystem.class).getJNDIContext()
                            .lookup("openejb:Resource/" + s.getConnectionFactoryLookup());
                    if (ActiveMQConnectionFactory.class.isInstance(lookup)) {
                        return ActiveMQConnectionFactory.class.cast(lookup);
                    }
                    return ActiveMQConnectionFactory.class.cast(lookup); // already handled
                } catch (final NamingException e) {
                    throw new IllegalArgumentException("");
                }
            }
        }

        final ActiveMQConnectionFactory factory = new TomEEConnectionFactory(TransactionSupport.TransactionSupportLevel.XATransaction);
        connectionRequestInfo.configure(factory, activationSpec);
        return factory;
    }

    private void stopImpl() throws Exception {
        super.stop();
        final Collection<BrokerService> brokers = ActiveMQFactory.getBrokers();
        final Iterator<BrokerService> it = brokers.iterator();
        while (it.hasNext()) {
            final BrokerService bs = it.next();
            try {
                bs.stop();
                bs.waitUntilStopped();
            } catch (final Throwable t) {
                //Ignore
            }
            it.remove();
        }
        stopScheduler();
        Logger.getInstance(LogCategory.OPENEJB_STARTUP, ActiveMQResourceAdapter.class).getChildLogger("service").info("Stopped ActiveMQ broker");
    }

    private static void stopScheduler() {
        try {
            final Class<?> clazz = Class.forName("org.apache.kahadb.util.Scheduler");
            final Method method = clazz.getMethod("shutdown");
            method.invoke(null);
        } catch (final Throwable e) {
            //Ignore
        }
    }

    /**
     * This overridden version of {@link org.apache.activemq.ra.ActiveMQResourceAdapter#makeConnection(ActiveMQConnectionRequestInfo, ActiveMQConnectionFactory)}
     * enables connection sharing between message driven beans. If sharedMdbConnections is set to true in this resource adapter, any message driven beans
     * with the same userName, password and clientId combination (which can be specified as properties on {@link org.apache.activemq.ra.ActiveMQActivationSpec}),
     * will share the same physical connection to the ActiveMQ broker, with the connection being referenced by the connection key in the sharedMdbConnections Map.
     * @param connectionRequestInfo The connection request information used to create the connection
     * @return The connection to the ActiveMQ broker
     * @throws JMSException thrown if an exception occurs
     */
    @Override
    public ActiveMQConnection makeConnection(ActiveMQConnectionRequestInfo connectionRequestInfo) throws JMSException {
        if (! isShareMdbConnections()) {
            return super.makeConnection(connectionRequestInfo);
        }

        final ConnectionKey connectionKey = new ConnectionKey(connectionRequestInfo);
        if (sharedMdbConnections.containsKey(connectionKey)) {
            return sharedMdbConnections.get(connectionKey);
        }

        final ActiveMQConnection connection = super.makeConnection(connectionRequestInfo);
        sharedMdbConnections.put(connectionKey, connection);
        return connection;
    }

    /**
     * This overridden version of {@link org.apache.activemq.ra.ActiveMQResourceAdapter#makeConnection(ActiveMQConnectionRequestInfo, ActiveMQConnectionFactory)}
     * enables connection sharing between message driven beans. If sharedMdbConnections is set to true in this resource adapter, any message driven beans
     * with the same userName, password and clientId combination (which can be specified as properties on {@link org.apache.activemq.ra.ActiveMQActivationSpec}),
     * will share the same physical connection to the ActiveMQ broker, with the connection being referenced by the connection key in the sharedMdbConnections Map.
     * @param connectionRequestInfo The connection request information used to create the connection
     * @param connectionFactory The connection factory to use to create a new connection, if needed
     * @return The connection to the ActiveMQ broker
     * @throws JMSException thrown if an exception occurs
     */
    @Override
    public ActiveMQConnection makeConnection(final ActiveMQConnectionRequestInfo connectionRequestInfo, final ActiveMQConnectionFactory connectionFactory) throws JMSException {
        if (! isShareMdbConnections()) {
            return super.makeConnection(connectionRequestInfo, connectionFactory);
        }

        final ConnectionKey connectionKey = new ConnectionKey(connectionRequestInfo);
        if (sharedMdbConnections.containsKey(connectionKey)) {
            return sharedMdbConnections.get(connectionKey);
        }

        final ActiveMQConnection connection = super.makeConnection(connectionRequestInfo, connectionFactory);
        sharedMdbConnections.put(connectionKey, connection);
        return connection;
    }

    /**
     * This is a simple POJO to hold key connection details - userName, password and clientID, enabling
     * connections to be shared.
     */
    public static class ConnectionKey implements Serializable {
        private final String userName;
        private final String password;
        private final String cliendId;

        public ConnectionKey(final String userName, final String password, final String cliendId) {
            this.userName = userName;
            this.password = password;
            this.cliendId = cliendId;
        }

        public ConnectionKey(final ActiveMQConnectionRequestInfo connectionRequestInfo) {
            this.userName = connectionRequestInfo.getUserName();
            this.password = connectionRequestInfo.getPassword();
            this.cliendId = connectionRequestInfo.getClientid();
        }

        public String getUserName() {
            return userName;
        }

        public String getPassword() {
            return password;
        }

        public String getCliendId() {
            return cliendId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConnectionKey that = (ConnectionKey) o;
            return Objects.equals(userName, that.userName) &&
                    Objects.equals(password, that.password) &&
                    Objects.equals(cliendId, that.cliendId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userName, password, cliendId);
        }
    }
}
