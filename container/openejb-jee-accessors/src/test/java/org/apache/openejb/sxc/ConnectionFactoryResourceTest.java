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
package org.apache.openejb.sxc;

import org.apache.openejb.jee.Application;
import org.apache.openejb.jee.ConnectionFactoryResource;
import org.apache.openejb.jee.EjbJar;
import org.apache.openejb.jee.EnterpriseBean;
import org.apache.openejb.jee.JaxbJavaee;
import org.apache.openejb.jee.TransactionSupportType;
import org.apache.openejb.jee.WebApp;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The {@code <connection-factory>} element (connection-factory-resourceType) of the jndiEnvironmentRefsGroup.
 */
public class ConnectionFactoryResourceTest {
    private static final String CONNECTION_FACTORY = "" +
        "  <connection-factory>\n" +
        "    <description>EIS connection factory</description>\n" +
        "    <name>java:app/eis/cf</name>\n" +
        "    <interface-name>com.eis.ConnectionFactory</interface-name>\n" +
        "    <resource-adapter>MyEISRA</resource-adapter>\n" +
        "    <max-pool-size>10</max-pool-size>\n" +
        "    <min-pool-size>2</min-pool-size>\n" +
        "    <transaction-support>LocalTransaction</transaction-support>\n" +
        "    <property>\n" +
        "      <name>greeting</name>\n" +
        "      <value>hola</value>\n" +
        "    </property>\n" +
        "  </connection-factory>\n" +
        "  <connection-factory>\n" +
        "    <name>java:comp/env/eis/minimal</name>\n" +
        "    <interface-name>com.eis.ConnectionFactory</interface-name>\n" +
        "    <resource-adapter>MyEISRA</resource-adapter>\n" +
        "  </connection-factory>\n";

    private static final String WEB_XML = "" +
        "<web-app xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" version=\"6.1\">\n" +
        "  <data-source>\n" +
        "    <name>java:app/ds</name>\n" +
        "    <class-name>org.hsqldb.jdbc.JDBCDataSource</class-name>\n" +
        "  </data-source>\n" +
        CONNECTION_FACTORY +
        "</web-app>\n";

    private static final String EJB_JAR_XML = "" +
        "<ejb-jar xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" version=\"4.0\">\n" +
        "  <enterprise-beans>\n" +
        "    <session>\n" +
        "      <ejb-name>Client</ejb-name>\n" +
        "      <ejb-class>org.acme.Client</ejb-class>\n" +
        "      <session-type>Singleton</session-type>\n" +
        CONNECTION_FACTORY +
        "    </session>\n" +
        "    <message-driven>\n" +
        "      <ejb-name>Listener</ejb-name>\n" +
        "      <ejb-class>org.acme.Listener</ejb-class>\n" +
        CONNECTION_FACTORY +
        "    </message-driven>\n" +
        "  </enterprise-beans>\n" +
        "</ejb-jar>\n";

    private static final String APPLICATION_XML = "" +
        "<application xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" version=\"11\">\n" +
        "  <module>\n" +
        "    <web>\n" +
        "      <web-uri>app.war</web-uri>\n" +
        "      <context-root>/app</context-root>\n" +
        "    </web>\n" +
        "  </module>\n" +
        CONNECTION_FACTORY +
        "</application>\n";

    @Test
    public void webXml() throws Exception {
        final WebApp webApp = WebXml.unmarshal(stream(WEB_XML));
        assertConnectionFactories(webApp.getConnectionFactories());
        assertEquals(1, webApp.getDataSource().size());
    }

    @Test
    public void webXmlRoundTrip() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        WebXml.marshal(WebXml.unmarshal(stream(WEB_XML)), out);
        assertConnectionFactories(WebXml.unmarshal(new ByteArrayInputStream(out.toByteArray())).getConnectionFactories());
    }

    @Test
    public void webXmlWithJaxb() throws Exception { // the reflective JAXB binding of the model
        final WebApp webApp = (WebApp) JaxbJavaee.unmarshalJavaee(WebApp.class, stream(WEB_XML));
        assertConnectionFactories(webApp.getConnectionFactories());
    }

    @Test
    public void ejbJarXml() throws Exception {
        final EjbJar ejbJar = EjbJarXml.unmarshal(stream(EJB_JAR_XML));
        final EnterpriseBean session = ejbJar.getEnterpriseBean("Client");
        assertConnectionFactories(session.getConnectionFactories());
        final EnterpriseBean mdb = ejbJar.getEnterpriseBean("Listener");
        assertConnectionFactories(mdb.getConnectionFactories());
    }

    @Test
    public void applicationXml() throws Exception {
        final Application application = ApplicationXml.unmarshal(stream(APPLICATION_XML));
        assertConnectionFactories(application.getConnectionFactories());
    }

    private static void assertConnectionFactories(final Collection<ConnectionFactoryResource> factories) {
        assertEquals(2, factories.size());

        final ConnectionFactoryResource full = find(factories, "java:app/eis/cf");
        assertEquals("EIS connection factory", full.getDescription());
        assertEquals("com.eis.ConnectionFactory", full.getInterfaceName());
        assertEquals("MyEISRA", full.getResourceAdapter());
        assertEquals(Integer.valueOf(10), full.getMaxPoolSize());
        assertEquals(Integer.valueOf(2), full.getMinPoolSize());
        assertEquals(TransactionSupportType.LOCAL_TRANSACTION, full.getTransactionSupport());
        assertEquals(1, full.getProperty().size());
        assertEquals("greeting", full.getProperty().get(0).getName());
        assertEquals("hola", full.getProperty().get(0).getValue());

        final ConnectionFactoryResource minimal = find(factories, "java:comp/env/eis/minimal");
        assertEquals("com.eis.ConnectionFactory", minimal.getInterfaceName());
        assertEquals("MyEISRA", minimal.getResourceAdapter());
        assertNull(minimal.getMaxPoolSize());
        assertNull(minimal.getMinPoolSize());
        assertNull(minimal.getTransactionSupport());
        assertEquals(0, minimal.getProperty().size());
    }

    private static ConnectionFactoryResource find(final Collection<ConnectionFactoryResource> factories, final String name) {
        for (final ConnectionFactoryResource factory : factories) {
            if (name.equals(factory.getName())) {
                return factory;
            }
        }
        throw new AssertionError("No connection factory named " + name + " in " + factories);
    }

    private static InputStream stream(final String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
