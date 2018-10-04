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

package org.apache.openejb.resource;

import org.apache.geronimo.connector.outbound.ConnectionInfo;
import org.apache.geronimo.connector.outbound.ConnectionReturnAction;
import org.apache.geronimo.connector.outbound.ConnectionTrackingInterceptor;
import org.apache.geronimo.connector.outbound.ManagedConnectionInfo;
import org.apache.geronimo.connector.outbound.connectiontracking.ConnectionTracker;
import org.apache.openejb.dyni.DynamicSubclass;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.util.LogCategory;
import org.apache.openejb.util.Logger;
import org.apache.openejb.util.proxy.LocalBeanProxyFactory;

import javax.resource.ResourceException;
import javax.resource.spi.DissociatableManagedConnection;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.apache.commons.lang3.ClassUtils.getAllInterfaces;

public class AutoConnectionTracker implements ConnectionTracker {
    private final Logger logger = Logger.getInstance(LogCategory.OPENEJB_CONNECTOR, "org.apache.openejb.resource");

    private final ConcurrentMap<ManagedConnectionInfo, ProxyPhantomReference> references = new ConcurrentHashMap<ManagedConnectionInfo, ProxyPhantomReference>();
    private final ReferenceQueue referenceQueue = new ReferenceQueue();
    private final ConcurrentMap<Class<?>, Class<?>> proxies = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, Class<?>[]> interfaces = new ConcurrentHashMap<>();

    private final boolean useConnectionProxies;
    private final TransactionSynchronizationRegistry registry;

    public AutoConnectionTracker(boolean useConnectionProxies) {
        this.useConnectionProxies = useConnectionProxies;
        registry = SystemInstance.get().getComponent(TransactionSynchronizationRegistry.class);
    }

    public Set<ManagedConnectionInfo> connections() {
        return references.keySet();
    }

    /**
     * Releases any managed connections held by a garbage collected connection proxy.
     *
     * @param connectionInfo the connection to be obtained
     * @param key            the unique id of the connection manager
     */
    public void setEnvironment(final ConnectionInfo connectionInfo, final String key) {
        ProxyPhantomReference reference = (ProxyPhantomReference) referenceQueue.poll();
        while (reference != null) {
            reference.clear();
            references.remove(reference.managedConnectionInfo);

            destroyConnection(reference);
            reference = (ProxyPhantomReference) referenceQueue.poll();
        }
    }

    private void destroyConnection(final ProxyPhantomReference reference) {
        final ConnectionInfo released = new ConnectionInfo(reference.managedConnectionInfo);
        reference.interceptor.returnConnection(released, ConnectionReturnAction.DESTROY);
        logger.warning("Destroyed abandoned connection " + reference.managedConnectionInfo + " opened at " + stackTraceToString(reference.stackTrace));
    }

    private void destroyConnection(final ManagedConnectionInfo managedConnectionInfo, final ConnectionTrackingInterceptor interceptor) {
        logger.warning("Transaction complete, but connection still has handles associated. Destroying connection: " + managedConnectionInfo);

        if (logger.isDebugEnabled()) {
            final StringBuilder sb = new StringBuilder();
            final Collection<ConnectionInfo> connectionInfos = managedConnectionInfo.getConnectionInfos();
            for (final ConnectionInfo connectionInfo : connectionInfos) {
                sb.append("\n  ").append("Connection handle opened at ").append(stackTraceToString(connectionInfo.getTrace().getStackTrace()));
            }

            logger.debug("Abandoned connection information: " + sb.toString());
        }

        final ConnectionInfo released = new ConnectionInfo(managedConnectionInfo);
        interceptor.returnConnection(released, ConnectionReturnAction.DESTROY);
    }

    /**
     * Proxies new connection handles so we can detect when they have been garbage collected.
     *
     * @param interceptor    the interceptor used to release the managed connection when the handled is garbage collected.
     * @param connectionInfo the connection that was obtained
     * @param reassociate    should always be false
     */
    public void handleObtained(final ConnectionTrackingInterceptor interceptor, final ConnectionInfo connectionInfo, final boolean reassociate) throws ResourceException {
        if (!reassociate) {
            proxyConnection(interceptor, connectionInfo);

            if (useConnectionProxies && isTxActive()) {


                if (null != registry) {
                    final ManagedConnectionInfo managedConnectionInfo = connectionInfo.getManagedConnectionInfo();

                    // hold a reference until the tx is complete
                    final Object proxy = connectionInfo.getConnectionProxy();

                    registry.registerInterposedSynchronization(new Synchronization() {

                        private Object proxyReference = proxy;

                        @Override
                        public void beforeCompletion() {
                        }

                        @Override
                        public void afterCompletion(int status) {
                            final ProxyPhantomReference reference = references.remove(managedConnectionInfo);
                            if (reference != null) {
                                destroyConnection(reference);
                                return;
                            }

                            if (managedConnectionInfo.hasConnectionHandles()) {
                                destroyConnection(managedConnectionInfo, interceptor);
                            }
                        }
                    });
                } else {
                    logger.warning("TransactionSynchronizationRegistry has not been initialized");
                }

            }

        }
    }

    private boolean isTxActive() {
        final TransactionManager txManager = SystemInstance.get().getComponent(TransactionManager.class);
        if (txManager == null) {
            logger.error("Transaction manager is not available");
            return false;
        }

        try {
            final Transaction transaction = txManager.getTransaction();
            if (transaction == null) {
                return false;
            }

            final int status = transaction.getStatus();
            return status == Status.STATUS_ACTIVE || status == Status.STATUS_MARKED_ROLLBACK;
        } catch (SystemException e) {
            logger.error("Error getting current transaction", e);
            return false;
        }
    }

    /**
     * Removes the released collection from the garbage collection reference tracker, since this
     * connection is being release via a normal close method.
     *
     * @param interceptor    ignored
     * @param connectionInfo the connection that was released
     * @param action         ignored
     */
    public void handleReleased(final ConnectionTrackingInterceptor interceptor, final ConnectionInfo connectionInfo, final ConnectionReturnAction action) {
        final PhantomReference phantomReference = references.remove(connectionInfo.getManagedConnectionInfo());
        if (phantomReference != null) {
            phantomReference.clear();
        }
    }

    private void proxyConnection(final ConnectionTrackingInterceptor interceptor, final ConnectionInfo connectionInfo) throws ResourceException {
        // no-op if we have opted to not use proxies
        if (! useConnectionProxies) {
            return;
        }

        // if this connection already has a proxy no need to create another
        if (connectionInfo.getConnectionProxy() != null) {
            return;
        }

        // DissociatableManagedConnection do not need to be proxied
        if (connectionInfo.getManagedConnectionInfo().getManagedConnection() instanceof DissociatableManagedConnection) {
            return;
        }

        try {
            final Object handle = connectionInfo.getConnectionHandle();
            final ConnectionInvocationHandler invocationHandler = new ConnectionInvocationHandler(handle);
            final Object proxy = newProxy(handle, invocationHandler);
            connectionInfo.setConnectionProxy(proxy);
            final ProxyPhantomReference reference = new ProxyPhantomReference(interceptor, connectionInfo.getManagedConnectionInfo(), invocationHandler, referenceQueue);
            references.put(connectionInfo.getManagedConnectionInfo(), reference);
        } catch (final Throwable e) {
            throw new ResourceException("Unable to construct connection proxy", e);
        }
    }

    private Object newProxy(final Object handle, final InvocationHandler invocationHandler) {
        ClassLoader loader = handle.getClass().getClassLoader();
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }
        if (!Proxy.isProxyClass(handle.getClass())) {
            final Object proxy = LocalBeanProxyFactory.Unsafe.allocateInstance(getProxy(handle.getClass(), loader));
            DynamicSubclass.setHandler(proxy, invocationHandler);
            return proxy;
        }

        return Proxy.newProxyInstance(loader, getAPi(handle.getClass()), invocationHandler);
    }

    private Class<?>[] getAPi(final Class<?> aClass) {
        Class<?>[] found = interfaces.get(aClass);
        if (found == null) {
            synchronized (this) {
                found = interfaces.get(aClass);
                if (found == null) {
                    final List<Class<?>> allInterfaces = getAllInterfaces(aClass);
                    final Class<?>[] asArray = allInterfaces.toArray(new Class<?>[allInterfaces.size()]);
                    interfaces.put(aClass, asArray);
                    found = interfaces.get(aClass);
                }
            }
        }
        return found;
    }

    private Class<?> getProxy(final Class<?> aClass, final ClassLoader loader) {
        Class<?> found = proxies.get(aClass);
        if (found == null) {
            synchronized (this) {
                found = proxies.get(aClass);
                if (found == null) {
                    proxies.put(aClass, DynamicSubclass.createSubclass(aClass, loader, true));
                    found = proxies.get(aClass);
                }
            }
        }
        return found;
    }

    public static String stackTraceToString(final StackTraceElement[] stackTrace) {
        if (stackTrace == null) {
            return "";
        }

        final StringBuilder sb = new StringBuilder();

        for (int i = 0; i < stackTrace.length; i++) {
            final StackTraceElement element = stackTrace[i];
            if (i > 0) {
                sb.append(", ");
            }

            sb.append(element.toString());
        }

        return sb.toString();
    }

    public static class ConnectionInvocationHandler implements InvocationHandler {
        private final Object handle;

        public ConnectionInvocationHandler(final Object handle) {
            this.handle = handle;
        }

        public Object invoke(final Object object, final Method method, final Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                if (method.getName().equals("finalize")) {
                    // ignore the handle will get called if it implemented the method
                    return null;
                }
                if (method.getName().equals("clone")) {
                    throw new CloneNotSupportedException();
                }
            }

            try {
                return method.invoke(handle, args);
            } catch (final InvocationTargetException ite) {
                // catch InvocationTargetExceptions and turn them into the target exception (if there is one)
                final Throwable t = ite.getTargetException();
                if (AbstractMethodError.class.isInstance(t)) {
                    // "debug" info
                    Logger.getInstance(LogCategory.OPENEJB, AutoConnectionTracker.class)
                        .error("Missing method: " + method + " on " + handle);
                }
                if (t != null) {
                    throw t;
                }
                throw ite;
            }
        }
    }

    private static class ProxyPhantomReference extends PhantomReference<ConnectionInvocationHandler> {
        private final ConnectionTrackingInterceptor interceptor;
        private final ManagedConnectionInfo managedConnectionInfo;
        private StackTraceElement[] stackTrace;

        @SuppressWarnings({"unchecked"})
        public ProxyPhantomReference(final ConnectionTrackingInterceptor interceptor,
                                     final ManagedConnectionInfo managedConnectionInfo,
                                     final ConnectionInvocationHandler handler,
                                     final ReferenceQueue referenceQueue) {
            super(handler, referenceQueue);
            this.interceptor = interceptor;
            this.managedConnectionInfo = managedConnectionInfo;
            this.stackTrace = Thread.currentThread().getStackTrace();
        }
    }
}
