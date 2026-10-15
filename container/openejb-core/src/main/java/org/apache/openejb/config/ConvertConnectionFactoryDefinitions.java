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

import jakarta.resource.spi.ConnectionDefinitions;
import org.apache.openejb.OpenEJBException;
import org.apache.openejb.assembler.classic.OpenEjbConfiguration;
import org.apache.openejb.assembler.classic.ResourceInfo;
import org.apache.openejb.config.sys.Resource;
import org.apache.openejb.jee.ConfigProperty;
import org.apache.openejb.jee.ConnectionDefinition;
import org.apache.openejb.jee.ConnectionFactoryResource;
import org.apache.openejb.jee.Connector;
import org.apache.openejb.jee.JndiConsumer;
import org.apache.openejb.jee.KeyedCollection;
import org.apache.openejb.jee.OutboundResourceAdapter;
import org.apache.openejb.jee.Property;
import org.apache.openejb.jee.ResourceAdapter;
import org.apache.openejb.jee.TransactionSupportType;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.util.PropertyPlaceHolderHelper;
import org.apache.xbean.finder.IAnnotationFinder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 * Converts {@link jakarta.resource.ConnectionFactoryDefinition} annotations and
 * {@code <connection-factory>} deployment descriptor elements into application resources.
 *
 * The definition only names the resource adapter and the connection factory interface, so the
 * managed connection factory class is looked up, in this order, in:
 * <ol>
 *     <li>the connector module of the application whose module name (or resource adapter id) is the
 *     requested resource adapter;</li>
 *     <li>otherwise the resource adapter must be an existing resource (of the application or of the server) and:
 *     <ol>
 *         <li>the connector module of the application whose resource adapter class is the class of this resource;</li>
 *         <li>the classes of the application annotated with {@link jakarta.resource.spi.ConnectionDefinition};</li>
 *         <li>any connector module of the application.</li>
 *     </ol></li>
 * </ol>
 * The definition fails the deployment when the resource adapter or the connection definition can't be found.
 */
public class ConvertConnectionFactoryDefinitions extends BaseConvertDefinitions {
    @Override
    public AppModule deploy(final AppModule appModule) throws OpenEJBException {
        final List<JndiConsumer> jndiConsumers = collectConsumers(appModule);
        final KeyedCollection<String, ConnectionFactoryResource> factories = new KeyedCollection<>();
        for (final JndiConsumer consumer : jndiConsumers) {
            if (consumer != null) {
                factories.addAll(consumer.getConnectionFactories());
            }
        }
        for (final ConnectionFactoryResource factory : factories) {
            appModule.getResources().add(toResource(appModule, factory));
        }
        return appModule;
    }

    private Resource toResource(final AppModule appModule, final ConnectionFactoryResource factory) throws OpenEJBException {
        final String definitionName = factory.getName();
        if (isEmpty(definitionName)) {
            throw new OpenEJBException("A connection factory definition requires a name");
        }
        if (isEmpty(factory.getInterfaceName())) {
            throw new OpenEJBException("Connection factory definition '" + definitionName + "' requires an interfaceName");
        }
        if (isEmpty(factory.getResourceAdapter())) {
            throw new OpenEJBException("Connection factory definition '" + definitionName + "' requires a resourceAdapter");
        }

        final ResolvedDefinition resolved = resolve(appModule, factory);

        final String name = cleanUpName(definitionName);
        final Resource factoryResource = new Resource(name, factory.getInterfaceName());
        factoryResource.setJndi(definitionName.replaceFirst("java:", ""));
        factoryResource.setClassName(resolved.connectionDefinition.getManagedConnectionFactoryClass());

        final Properties p = factoryResource.getProperties();

        // defaults of the connection definition, overridden by the definition properties
        for (final ConfigProperty property : resolved.connectionDefinition.getConfigProperty()) {
            put(p, property.getConfigPropertyName(), property.getConfigPropertyValue());
        }

        put(p, AutoConfig.ORIGIN_FLAG, AutoConfig.ORIGIN_ANNOTATION);
        put(p, "JndiName", factoryResource.getJndi());
        put(p, "ResourceAdapter", resolved.resourceAdapterId);
        put(p, "TransactionSupport", toTransactionSupport(factory.getTransactionSupport() != null ?
            factory.getTransactionSupport() : resolved.transactionSupport));
        if (factory.getMaxPoolSize() != null && factory.getMaxPoolSize() > 0) {
            put(p, "PoolMaxSize", factory.getMaxPoolSize());
        }
        if (factory.getMinPoolSize() != null && factory.getMinPoolSize() >= 0) {
            put(p, "PoolMinSize", factory.getMinPoolSize());
        }

        setProperties(factory, p);

        // the managed connection factory is an application class and its resource adapter can be a connector
        // of the application which is only created with the application: create the factory once the application
        // is deployed, failing the deployment if it can't be created
        put(p, "Lazy", "true");
        put(p, "UseAppClassLoader", "true");
        put(p, "InitializeAfterDeployment", "true");

        return factoryResource;
    }

    private ResolvedDefinition resolve(final AppModule appModule, final ConnectionFactoryResource factory) throws OpenEJBException {
        final String interfaceName = factory.getInterfaceName().trim();
        final String resourceAdapter = factory.getResourceAdapter().trim();
        // embedded resource adapters can be referenced as <application name>#<module name>
        final String resourceAdapterName = resourceAdapter.substring(resourceAdapter.indexOf('#') + 1);

        // 1. a connector module of the application
        for (final ConnectorModule connectorModule : appModule.getConnectorModules()) {
            final Connector connector = connectorModule.getConnector();
            if (connector == null || connector.getResourceAdapter() == null) {
                continue;
            }

            final String resourceAdapterId = getResourceAdapterId(connectorModule);
            if (!resourceAdapterName.equals(connectorModule.getModuleId()) && !resourceAdapterName.equals(resourceAdapterId)) {
                continue;
            }

            ConnectionDefinition definition = findConnectionDefinition(connector, interfaceName);
            if (definition == null) {
                definition = findAnnotatedConnectionDefinition(connectorModule.getFinder(), interfaceName);
            }
            if (definition == null) {
                throw notFound(factory, "resource adapter module '" + connectorModule.getModuleId() + "' has no connection definition for this interface");
            }
            return new ResolvedDefinition(definition, resourceAdapterId, getTransactionSupport(connector));
        }

        // 2. the resource adapter is a resource: look for its connection definition in the application
        if (!resourceAdapterExists(appModule, resourceAdapterName)) {
            throw notFound(factory, "no resource adapter is defined with this name");
        }

        final String resourceAdapterClass = findResourceAdapterClass(appModule, resourceAdapterName);
        ConnectorModule fallback = null;
        for (final ConnectorModule connectorModule : appModule.getConnectorModules()) {
            final Connector connector = connectorModule.getConnector();
            if (connector == null || connector.getResourceAdapter() == null) {
                continue;
            }
            final ConnectionDefinition definition = findConnectionDefinition(connector, interfaceName);
            if (definition == null) {
                continue;
            }
            if (resourceAdapterClass != null && resourceAdapterClass.equals(connector.getResourceAdapter().getResourceAdapterClass())) {
                return new ResolvedDefinition(definition, resourceAdapterName, getTransactionSupport(connector));
            }
            if (fallback == null) {
                fallback = connectorModule;
            }
        }

        // @ConnectionDefinition annotated classes of the application
        for (final IAnnotationFinder finder : collectFinders(appModule)) {
            final ConnectionDefinition definition = findAnnotatedConnectionDefinition(finder, interfaceName);
            if (definition != null) {
                return new ResolvedDefinition(definition, resourceAdapterName, null);
            }
        }

        if (fallback != null) {
            return new ResolvedDefinition(findConnectionDefinition(fallback.getConnector(), interfaceName), resourceAdapterName, getTransactionSupport(fallback.getConnector()));
        }

        throw notFound(factory, "no connection definition provides this interface in the application");
    }

    private static OpenEJBException notFound(final ConnectionFactoryResource factory, final String reason) {
        return new OpenEJBException("Can't create connection factory definition '" + factory.getName()
            + "' (resourceAdapter='" + factory.getResourceAdapter() + "', interfaceName='" + factory.getInterfaceName()
            + "'): " + reason);
    }

    private static String getResourceAdapterId(final ConnectorModule connectorModule) {
        // see AppInfoBuilder#getId(ConnectorModule)
        final String id = connectorModule.getConnector().getResourceAdapter().getId();
        return id != null ? id : connectorModule.getModuleId() + "RA";
    }

    private static TransactionSupportType getTransactionSupport(final Connector connector) {
        final OutboundResourceAdapter outbound = connector.getResourceAdapter().getOutboundResourceAdapter();
        return outbound != null ? outbound.getTransactionSupport() : null;
    }

    private static ConnectionDefinition findConnectionDefinition(final Connector connector, final String interfaceName) {
        final ResourceAdapter resourceAdapter = connector.getResourceAdapter();
        if (resourceAdapter == null || resourceAdapter.getOutboundResourceAdapter() == null) {
            return null;
        }
        for (final ConnectionDefinition definition : resourceAdapter.getOutboundResourceAdapter().getConnectionDefinition()) {
            if (interfaceName.equals(definition.getConnectionFactoryInterface()) && definition.getManagedConnectionFactoryClass() != null) {
                return definition;
            }
        }
        return null;
    }

    private static ConnectionDefinition findAnnotatedConnectionDefinition(final IAnnotationFinder finder, final String interfaceName) {
        if (finder == null) {
            return null;
        }

        final List<jakarta.resource.spi.ConnectionDefinition> annotations = new ArrayList<>();
        final List<Class<?>> annotatedClasses = new ArrayList<>();
        for (final Class<?> clazz : finder.findAnnotatedClasses(ConnectionDefinitions.class)) {
            for (final jakarta.resource.spi.ConnectionDefinition annotation : clazz.getAnnotation(ConnectionDefinitions.class).value()) {
                annotations.add(annotation);
                annotatedClasses.add(clazz);
            }
        }
        for (final Class<?> clazz : finder.findAnnotatedClasses(jakarta.resource.spi.ConnectionDefinition.class)) {
            annotations.add(clazz.getAnnotation(jakarta.resource.spi.ConnectionDefinition.class));
            annotatedClasses.add(clazz);
        }

        for (int i = 0; i < annotations.size(); i++) {
            if (interfaceName.equals(annotations.get(i).connectionFactory().getName())) {
                final ConnectionDefinition definition = new ConnectionDefinition();
                definition.setManagedConnectionFactoryClass(annotatedClasses.get(i).getName());
                definition.setConnectionFactoryInterface(interfaceName);
                return definition;
            }
        }
        return null;
    }

    private static String findResourceAdapterClass(final AppModule appModule, final String resourceAdapterId) {
        for (final Resource resource : appModule.getResources()) {
            if (resourceAdapterId.equals(resource.getId())) {
                return resource.getClassName();
            }
        }
        return null;
    }

    private static boolean resourceAdapterExists(final AppModule appModule, final String resourceAdapterId) {
        final Collection<String> ids = new ArrayList<>();
        for (final Resource resource : appModule.getResources()) {
            ids.add(resource.getId());
        }

        // resources of the server (or of other applications) already created
        final OpenEjbConfiguration configuration = SystemInstance.get().getComponent(OpenEjbConfiguration.class);
        if (configuration == null || configuration.facilities == null) { // can't check, let the assembler fail if it is missing
            return true;
        }
        for (final ResourceInfo resource : configuration.facilities.resources) {
            ids.add(resource.id);
            ids.addAll(resource.aliases);
        }
        return AutoConfig.findResourceId(ids, resourceAdapterId) != null;
    }

    private static Collection<IAnnotationFinder> collectFinders(final AppModule appModule) {
        final Collection<IAnnotationFinder> finders = new ArrayList<>();
        for (final WebModule webModule : appModule.getWebModules()) {
            finders.add(webModule.getFinder());
        }
        for (final EjbModule ejbModule : appModule.getEjbModules()) {
            finders.add(ejbModule.getFinder());
        }
        for (final ClientModule clientModule : appModule.getClientModules()) {
            finders.add(clientModule.getFinder());
        }
        for (final ConnectorModule connectorModule : appModule.getConnectorModules()) {
            finders.add(connectorModule.getFinder());
        }
        finders.add(appModule.getEarLibFinder());
        return finders;
    }

    private static String toTransactionSupport(final TransactionSupportType transactionSupport) {
        if (transactionSupport == null) {
            return "none";
        }
        return switch (transactionSupport) {
            case LOCAL_TRANSACTION -> "local";
            case NO_TRANSACTION -> "none";
            case XA_TRANSACTION -> "xa";
        };
    }

    private static boolean isEmpty(final String value) {
        return value == null || value.trim().isEmpty();
    }

    private void setProperties(final ConnectionFactoryResource d, final Properties p) {
        for (final Property property : d.getProperty()) {

            final String key = property.getName();
            final String value = property.getValue();

            put(p, key, value);
        }
    }

    private static void put(final Properties properties, final String key, final Object value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            return;
        }
        properties.put(key, PropertyPlaceHolderHelper.value(String.valueOf(value)));
    }

    private static class ResolvedDefinition {
        private final ConnectionDefinition connectionDefinition;
        private final String resourceAdapterId;
        private final TransactionSupportType transactionSupport;

        private ResolvedDefinition(final ConnectionDefinition connectionDefinition, final String resourceAdapterId,
                                   final TransactionSupportType transactionSupport) {
            this.connectionDefinition = connectionDefinition;
            this.resourceAdapterId = resourceAdapterId;
            this.transactionSupport = transactionSupport;
        }
    }
}
