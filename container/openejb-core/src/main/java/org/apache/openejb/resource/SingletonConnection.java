/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.openejb.resource;

import org.apache.geronimo.connector.outbound.*;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.PoolingSupport;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SingletonConnection implements PoolingSupport {
    private static final long serialVersionUID = -7871656583080873750L;

    public ConnectionInterceptor addPoolingInterceptors(ConnectionInterceptor tail) {
        return new SingleConnection(tail);
    }

    public int getPartitionCount() {
        return 0;
    }

    public int getIdleConnectionCount() {
        return 0;
    }

    public int getConnectionCount() {
        return 0;
    }

    public int getPartitionMaxSize() {
        return 0;
    }

    public void setPartitionMaxSize(int maxSize) {

    }

    public int getPartitionMinSize() {
        return 0;
    }

    public void setPartitionMinSize(int minSize) {

    }

    public int getBlockingTimeoutMilliseconds() {
        return 0;
    }

    public void setBlockingTimeoutMilliseconds(int timeoutMilliseconds) {

    }

    public int getIdleTimeoutMinutes() {
        return 0;
    }

    public void setIdleTimeoutMinutes(int idleTimeoutMinutes) {

    }

    private static class SingleConnection implements ConnectionInterceptor {
        private final ConnectionInterceptor next;
        private final Map<ConnectionRequestInfo, ManagedConnection> connectionMap = new ConcurrentHashMap<>();



        public SingleConnection(final ConnectionInterceptor next) {
            this.next = next;
        }

        @Override
        public void getConnection(final ConnectionInfo connectionInfo) throws ResourceException {
            ManagedConnectionInfo mci = connectionInfo.getManagedConnectionInfo();
            if (mci.getManagedConnection() != null) {
                return;
            }

            final ConnectionRequestInfo key = mci.getConnectionRequestInfo();
            ManagedConnection mc = connectionMap.computeIfAbsent(key, cri -> {
                try {
                    return mci.getManagedConnectionFactory().createManagedConnection(mci.getSubject(), key);
                } catch (ResourceException e) {
                    return null;
                }
            });

            if (mc == null) {
                throw new ResourceException("Unable to create exception");
            }

            // TODO: proxy mc

            mci.setManagedConnection(mc);
            GeronimoConnectionEventListener listener = new GeronimoConnectionEventListener(next, mci);
            mci.setConnectionEventListener(listener);
            mc.addConnectionEventListener(listener);
        }

        @Override
        public void returnConnection(final ConnectionInfo connectionInfo, final ConnectionReturnAction connectionReturnAction) {
            // no-op
        }

        @Override
        public void destroy() {
            connectionMap.forEach((k,v) -> {
                try {
                    v.destroy();
                } catch (ResourceException e) {
                    // not much we can do...
                }
            });
        }

        @Override
        public void info(final StringBuilder s) {

        }
    }
}
