/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.openejb.activemq;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQXAConnectionFactory;
import org.apache.activemq.ra.ActiveMQConnectionRequestInfo;
import org.apache.openejb.jee.EjbJar;
import org.apache.openejb.jee.MessageDrivenBean;
import org.apache.openejb.junit.ApplicationComposer;
import org.apache.openejb.resource.activemq.jms2.TomEEManagedConnectionFactory;
import org.apache.openejb.testing.Classes;
import org.apache.openejb.testing.Configuration;
import org.apache.openejb.testing.Module;
import org.apache.openejb.testing.SimpleLog;
import org.apache.openejb.testng.PropertiesBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.MessageDriven;
import javax.ejb.Singleton;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

@SimpleLog
@RunWith(ApplicationComposer.class)
public class JMSSingleConnectionMultiThreadTest {

    @Configuration
    public Properties config() {
        return new PropertiesBuilder()

                .p("amq", "new://Resource?type=ActiveMQResourceAdapter")
                .p("amq.DataSource", "")
                .p("amq.BrokerXmlConfig", "broker:(vm://localhost)")
                .p("amq.ServerUrl", "vm://localhost?waitForStart=20000&jms.prefetchPolicy.all=1")

                .p("count", "new://Resource?type=Queue")

                .p("mdbs", "new://Container?type=MESSAGE")
                .p("mdbs.ResourceAdapter", "amq")
                .p("mdbs.activation.ConnectionFactoryLookup", "incf")

                .p("cf", "new://Resource?type=" + ConnectionFactory.class.getName() + "&class-name=" + TestTomEEConnectionFactory.class.getName())
                .p("cf.ResourceAdapter", "amq")
                .p("cf.singleton", "true")
                .p("cf.TransactionSupport", "xa")
                .p("cf.PoolMaxSize", "10")
                .p("cf.PoolMinSize", "0")
                .p("cf.ConnectionMaxWaitTime", "5 seconds")
                .p("cf.ConnectionMaxIdleTime", "15 minutes")

                .p("incf", "new://Resource?type=" + ConnectionFactory.class.getName() + "&class-name=" + TestTomEEConnectionFactory.class.getName())
                .p("incf.ResourceAdapter", "amq")
                .p("incf.singleton", "true")
                .p("incf.TransactionSupport", "xa")
                .p("incf.PoolMaxSize", "10")
                .p("incf.PoolMinSize", "0")
                .p("incf.ConnectionMaxWaitTime", "5 seconds")
                .p("incf.ConnectionMaxIdleTime", "15 minutes")

                .build();
    }

    @Module
    @Classes(cdi = true, value = { JMSSingleConnectionMultiThreadTest.SendToCounterQueueBean.class })
    public EjbJar jar() {
        final EjbJar ejbJar = new EjbJar();
        ejbJar.addEnterpriseBean(new MessageDrivenBean(JMSSingleConnectionMultiThreadTest.Counter.class));
        return ejbJar;
    }

    @EJB
    private SendToCounterQueueBean sendToCounterQueueBean;

    @Test
    public void multithreadMdbTest() throws Exception {

        long start = System.currentTimeMillis();

        final int numberOfThreads = 10;
        final int numberOfMessagesPerThread = 100;

        final int totalMessages = numberOfMessagesPerThread * numberOfThreads;

        Counter.reset(totalMessages);

        final CountDownLatch setup = new CountDownLatch(numberOfThreads);
        final CountDownLatch startingGun = new CountDownLatch(1);

        final Runnable r = () -> {
            try {
                setup.countDown();
                startingGun.await(1, TimeUnit.MINUTES);

                for (int i = 0; i < numberOfMessagesPerThread; i++) {
                    sendToCounterQueueBean.send();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        for (int i = 0; i < numberOfThreads; i++) {
            new Thread(r).start();
        }

        setup.await(1, TimeUnit.MINUTES);
        startingGun.countDown();

        Counter.LATCH.await(1, TimeUnit.MINUTES);
        assertEquals(totalMessages, Counter.getCount());

        long end = System.currentTimeMillis();
        System.out.println("Test completed in " + (end - start) + " ms");
    }

    @MessageDriven(activationConfig = {
            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
            @ActivationConfigProperty(propertyName = "destination", propertyValue = "count")
    })
    public static class Counter implements MessageListener {

        private static final AtomicInteger COUNTER = new AtomicInteger(0);
        private static CountDownLatch LATCH = null;

        public static void reset(final int target) {
            COUNTER.set(0);
            LATCH = new CountDownLatch(target);
        }

        public static int getCount() {
            return COUNTER.get();
        }

        @Override
        public void onMessage(Message message) {
            COUNTER.incrementAndGet();
            if (LATCH != null) {
                LATCH.countDown();
            }
        }
    }

    public static class TestTomEEConnectionFactory extends TomEEManagedConnectionFactory {
        private static final Set<Integer> CONNECTIONS_CREATED = Collections.synchronizedSet(new HashSet<>());

        public static void reset() {
            CONNECTIONS_CREATED.clear();
        }

        public static int getConnectionsCreatedCount() {
            return CONNECTIONS_CREATED.size();
        }

        @Override
        public ActiveMQConnection makeConnection(ActiveMQConnectionRequestInfo connectionRequestInfo, ActiveMQConnectionFactory connectionFactory) throws JMSException {
            final ActiveMQConnection activeMQConnection = super.makeConnection(connectionRequestInfo, connectionFactory);
            CONNECTIONS_CREATED.add(activeMQConnection.hashCode());
            return activeMQConnection;
        }
    }

    @Singleton
    @Lock(LockType.READ)
    public static class SendToCounterQueueBean {
        @Resource(name = "cf")
        private ConnectionFactory cf;

        @Resource(name = "count")
        private Queue countQueue;

        public void send() throws Exception {

            Connection connection = null;
            Session session = null;
            MessageProducer producer = null;

            try {
                connection = cf.createConnection();
                session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
                producer = session.createProducer(countQueue);

                final TextMessage textMessage = session.createTextMessage("Hello from test!");
                producer.send(textMessage);
            } catch (Exception e) {
                System.out.println("Caught exception here");
                e.printStackTrace();
                throw e;
            } finally {
                if (producer != null) {
                    producer.close();
                }

                if (session != null) {
                    session.close();
                }

                if (connection != null) {
                    connection.close();
                }
            }
        }
    }
}
