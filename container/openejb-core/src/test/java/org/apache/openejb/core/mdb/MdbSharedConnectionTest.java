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
package org.apache.openejb.core.mdb;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.openejb.jee.EjbJar;
import org.apache.openejb.jee.MessageDrivenBean;
import org.apache.openejb.junit.ApplicationComposer;
import org.apache.openejb.testing.Configuration;
import org.apache.openejb.testing.Module;
import org.apache.openejb.testng.PropertiesBuilder;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.*;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;

@RunWith(ApplicationComposer.class)
public class MdbSharedConnectionTest {

    private static BrokerService broker;

    @Resource(name = "targetOne")
    private Topic targetOne;

    @Resource(name = "targetTwo")
    private Topic targetTwo;

    @Resource(name = "cf")
    private ConnectionFactory cf;

    @Configuration
    public Properties config() throws Exception{
        final TransportConnector tc = broker.getTransportConnectors().iterator().next();
        final int port = tc.getConnectUri().getPort();

        return new PropertiesBuilder()

                .p("amq", "new://Resource?type=ActiveMQResourceAdapter")
                .p("amq.DataSource", "")
                .p("amq.ShareMdbConnections", "true")
                .p("amq.BrokerXmlConfig", "") //connect to an external broker
                .p("amq.ServerUrl", "tcp://localhost:" + port)

                .p("targetOne", "new://Resource?type=Topic")
                .p("targetTwo", "new://Resource?type=Topic")

                .p("mdbs", "new://Container?type=MESSAGE")
                .p("mdbs.ResourceAdapter", "amq")
                .p("mdbs.pool", "false")
//                .p("mdbs.activation.ConnectionFactoryLookup", "cf")

                .p("cf", "new://Resource?type=" + ConnectionFactory.class.getName())
                .p("cf.ResourceAdapter", "amq")
//                .p("cf.Pooling", "false")


                .build();
    }

    @Module
    public EjbJar ejbs() {
        return new EjbJar()
                .enterpriseBean(new MessageDrivenBean(BeanOne.class))
                .enterpriseBean(new MessageDrivenBean(BeanTwo.class));
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        broker = new BrokerService();
        broker.setPersistent(false);
        broker.setUseJmx(true);
        broker.addConnector("tcp://localhost:0"); // pick a random available port

        broker.start();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        broker.stop();
    }

    @Test
    public void messageDrivenBeansShouldShareAConnection() throws Exception {
        sendMessageTo("targetOne");
        sendMessageTo("targetTwo");

        BeanOne.latch.await();
        BeanTwo.latch.await();

//        new CountDownLatch(1).await();

//        final MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
//        final Set<ObjectName> objectNames = platformMBeanServer.queryNames(new ObjectName("org.apache.activemq:type=Broker,brokerName=localhost,destinationType=Topic,destinationName=target,endpoint=Consumer,*"), null);
//
//        ObjectName match = null;
//
//        for (final ObjectName objectName : objectNames) {
//            if (objectName.getKeyProperty("clientId").startsWith("testMDB")) {
//                match = objectName;
//                break;
//            }
//        }
//
//        Assert.assertNotNull(match);
//
//        final String clientId = match.getKeyProperty("clientId");
//        final String uniquePart = clientId.substring(8);
//
//        Assert.assertNotNull(clientId);
//        Assert.assertNotNull(uniquePart);
//
//        final Pattern pattern = Pattern.compile("ID_.*?-\\d+-\\d+-\\d+_\\d");
//        Assert.assertTrue(pattern.matcher(uniquePart).matches());
    }

    private void sendMessageTo(final String destination) throws Exception {
        final int port = broker.getTransportConnectors().iterator().next().getConnectUri().getPort();
        final ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:" + port);
        final ActiveMQTopic topic = new ActiveMQTopic(destination);

        final Connection connection = connectionFactory.createConnection();
        connection.start();

        final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final MessageProducer producer = session.createProducer(topic);
        final TextMessage msg = session.createTextMessage("Hello");
        producer.send(msg);
        producer.close();
        session.close();
        connection.close();
    }

    @MessageDriven(activationConfig = {
            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
            @ActivationConfigProperty(propertyName = "destination", propertyValue = "targetOne"),
//            @ActivationConfigProperty(propertyName = "clientId", propertyValue = "UnitTest")
    })
    public static class BeanOne implements MessageListener {
        public static CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onMessage(final Message message) {
            latch.countDown();
        }
    }

    @MessageDriven(activationConfig = {
            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
            @ActivationConfigProperty(propertyName = "destination", propertyValue = "targetTwo"),
//            @ActivationConfigProperty(propertyName = "clientId", propertyValue = "UnitTest")
    })
    public static class BeanTwo implements MessageListener {
        public static CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onMessage(final Message message) {
            latch.countDown();
        }
    }



}