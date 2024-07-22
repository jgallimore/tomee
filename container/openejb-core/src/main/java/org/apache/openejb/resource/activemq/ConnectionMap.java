/**
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
package org.apache.openejb.resource.activemq;

import org.apache.activemq.ActiveMQConnection;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionMap {

    private final Map<ConnectionKey, ActiveMQConnection> connections = new ConcurrentHashMap<>();
    private static final AtomicInteger CONNECTIONS_CREATED = new AtomicInteger(0);

    public synchronized ActiveMQConnection getConnection(String username, String password, String clientID, Callable<ActiveMQConnection> connectionSupplier) throws Exception {
        final ConnectionKey key = new ConnectionKey(username, password, clientID);
        ActiveMQConnection connection = connections.get(key);

        if (connection != null && connection.isClosed()) {
            connections.remove(key);
            connection = null;
        }

        if (connection == null) {
            connection = connectionSupplier.call();
            CONNECTIONS_CREATED.incrementAndGet();
            connections.put(key, connection);
        }

        return connection;
    }

    public static int getConnectionsCreatedCount() {
        return CONNECTIONS_CREATED.get();
    }

    public static class ConnectionKey {
        private final String username;
        private final String password;
        private final String clientID;

        public ConnectionKey(String username, String password, String clientID) {
            this.username = username;
            this.password = password;
            this.clientID = clientID;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public String getClientID() {
            return clientID;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConnectionKey that = (ConnectionKey) o;
            return Objects.equals(username, that.username) && Objects.equals(password, that.password) && Objects.equals(clientID, that.clientID);
        }

        @Override
        public int hashCode() {
            return Objects.hash(username, password, clientID);
        }
    }
}
