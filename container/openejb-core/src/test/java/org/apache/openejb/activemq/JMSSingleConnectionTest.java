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
import org.apache.commons.lang3.SerializationUtils;
import org.apache.openejb.jee.EjbJar;
import org.apache.openejb.jee.MessageDrivenBean;
import org.apache.openejb.junit.ApplicationComposer;
import org.apache.openejb.resource.activemq.jms2.TomEEManagedConnectionFactory;
import org.apache.openejb.testing.Classes;
import org.apache.openejb.testing.Configuration;
import org.apache.openejb.testing.Module;
import org.apache.openejb.testing.SimpleLog;
import org.apache.openejb.testng.PropertiesBuilder;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.spi.ContextsService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.ejb.Singleton;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.jms.ConnectionFactory;
import javax.jms.JMSConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.TextMessage;
import javax.jms.XAConnectionFactory;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.TransactionScoped;
import javax.transaction.UserTransaction;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Thread.sleep;
import static org.junit.Assert.*;

@SimpleLog
@RunWith(ApplicationComposer.class)
public class JMSSingleConnectionTest {
    private static final String TEXT = "foo";

    @Configuration
    public Properties config() {
        return new PropertiesBuilder()

                .p("amq", "new://Resource?type=ActiveMQResourceAdapter")
                .p("amq.DataSource", "")
                .p("amq.BrokerXmlConfig", "broker:(vm://localhost)")
                .p("amq.ServerUrl", "vm://localhost?waitForStart=20000&jms.prefetchPolicy.all=1")

                .p("target", "new://Resource?type=Queue")
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

                .p("xaCf", "new://Resource?class-name=" + ActiveMQXAConnectionFactory.class.getName())
                .p("xaCf.BrokerURL", "vm://localhost")

                .build();
    }

    @Module
    @Classes(cdi = true, value = { JMSSingleConnectionTest.JustHereToCheckDeploymentIsOk.class, JMSSingleConnectionTest.ProducerBean.class })
    public EjbJar jar() {
        final EjbJar ejbJar = new EjbJar();
        ejbJar.addEnterpriseBean(new MessageDrivenBean(JMSSingleConnectionTest.Listener.class));
        return ejbJar;
    }

    @Resource(name = "target")
    private Queue destination;

    @Resource(name = "target2")
    private Queue destination2;

    @Resource(name = "target3")
    private Queue destination3;

    @Resource(name = "xaCf")
    private XAConnectionFactory xacf;

    @Resource(name = "cf")
    private ConnectionFactory cf;

    @Resource(name = "incf")
    private ConnectionFactory incf;

    @Inject
    @JMSConnectionFactory("cf")
    private JMSContext context;

    @Inject
    @JMSConnectionFactory("incf")
    private JMSContext inContext;

    @Inject // just there to ensure the injection works and we don't require @JMSConnectionFactory
    private JMSContext defaultContext;

    @Inject
    private JMSSingleConnectionTest.JustHereToCheckDeploymentIsOk session;

    @Resource
    private UserTransaction ut;

    @EJB
    private ProducerBean pb;
    
    @Before
    public void resetLatch() {
        Listener.reset();
        TestTomEEConnectionFactory.reset();
    }

    @Test
    public void serialize() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        final JMSContext c = SerializationUtils.deserialize(SerializationUtils.serialize(Serializable.class.cast(context)));
        ut.begin();
        session.ok();
        ut.commit();
    }

    @Test
    public void cdi() throws InterruptedException {
        final String text = TEXT + "3";

        final AtomicReference<Throwable> error = new AtomicReference<>();
        final CountDownLatch ready = new CountDownLatch(1);
        final CountDownLatch over = new CountDownLatch(1);
        new Thread() {
            {
                setName(JMSSingleConnectionTest.class.getName() + ".cdi#receiver");
            }

            @Override
            public void run() {
                final ContextsService contextsService = WebBeansContext.currentInstance().getContextsService();
                contextsService.startContext(RequestScoped.class, null); // spec defines it for request scope an transaction scope
                try {
                    ready.countDown();
                    assertEquals(text, inContext.createConsumer(destination3).receiveBody(String.class, TimeUnit.MINUTES.toMillis(1)));

                    // ensure we dont do a NPE if there is nothing to read
                    assertNull(context.createConsumer(destination3).receiveBody(String.class, 100));
                } catch (final Throwable t) {
                    error.set(t);
                } finally {
                    contextsService.endContext(RequestScoped.class, null);
                    over.countDown();
                }
            }
        }.start();

        ready.await(1, TimeUnit.MINUTES);
        sleep(150); // just to ensure we called receive already

        // now send the message
        try (final JMSContext context = cf.createContext()) {
            context.createProducer().send(destination3, text);
        } catch (final JMSRuntimeException ex) {
            fail(ex.getMessage());
        }

        over.await(1, TimeUnit.MINUTES);

        // ensure we got the message and no exception
        final Throwable exception = error.get();
        if (exception != null) {
            exception.printStackTrace();
        }
        assertNull(exception == null ? "ok" : exception.getMessage(), exception);
        assertEquals(2, TestTomEEConnectionFactory.getConnectionsCreatedCount());
    }

    @Test
    public void cdiListenerAPI() throws InterruptedException {
        final String text = TEXT + "4";

        final AtomicReference<Throwable> error = new AtomicReference<>();
        final CountDownLatch ready = new CountDownLatch(1);
        final CountDownLatch over = new CountDownLatch(1);
        new Thread() {
            {
                setName(JMSSingleConnectionTest.class.getName() + ".cdiListenerAPI#receiver");
            }

            @Override
            public void run() {
                final ContextsService contextsService = WebBeansContext.currentInstance().getContextsService();
                contextsService.startContext(RequestScoped.class, null);
                try {
                    final JMSConsumer consumer = inContext.createConsumer(destination3);
                    consumer.setMessageListener(new MessageListener() {
                        @Override
                        public void onMessage(final Message message) {
                            try {
                                assertEquals(text, message.getBody(String.class));
                            } catch (final Throwable e) {
                                error.set(e);
                            } finally {
                                over.countDown();
                                consumer.close();
                            }
                        }
                    });
                    ready.countDown();
                } catch (final Throwable t) {
                    error.set(t);
                } finally {
                    try {
                        over.await(1, TimeUnit.MINUTES);
                    } catch (final InterruptedException e) {
                        Thread.interrupted();
                    }
                    contextsService.endContext(RequestScoped.class, null);
                }
            }
        }.start();

        ready.await(1, TimeUnit.MINUTES);

        // now send the message
        try (final JMSContext context = cf.createContext()) {
            context.createProducer().send(destination3, text);
        } catch (final JMSRuntimeException ex) {
            fail(ex.getMessage());
        }

        over.await(1, TimeUnit.MINUTES);

        // ensure we got the message and no exception
        final Throwable exception = error.get();
        if (exception != null) {
            exception.printStackTrace();
        }
        assertNull(exception == null ? "ok" : exception.getMessage(), exception);
        assertEquals(2, TestTomEEConnectionFactory.getConnectionsCreatedCount());
    }

    @Test
    public void sendToMdb() throws Exception {
        try (final JMSContext context = cf.createContext()) {
            context.createProducer().send(destination, TEXT);
            assertTrue(JMSSingleConnectionTest.Listener.sync());
            assertEquals(1, TestTomEEConnectionFactory.getConnectionsCreatedCount());
        } catch (final JMSRuntimeException ex) {
            fail(ex.getMessage());
        }
    }

    @Test
    public void sendMessageToMdb() throws Exception {
        try (final JMSContext context = cf.createContext()) {
            Message message = context.createMessage();
            message.setStringProperty("text", TEXT);
            context.createProducer().send(destination, message);
            assertTrue(JMSSingleConnectionTest.Listener.sync());
            assertEquals(1, TestTomEEConnectionFactory.getConnectionsCreatedCount());
        } catch (final JMSRuntimeException ex) {
            fail(ex.getMessage());
        }
    }

    @Test
    public void sendToMdbWithDefaultCf() throws Exception {
        defaultContext.createProducer().send(destination, TEXT);
        assertTrue(JMSSingleConnectionTest.Listener.sync());
        assertEquals(1, TestTomEEConnectionFactory.getConnectionsCreatedCount());
    }

    @Test
    public void sendToMdbWithTxAndCheckLeaks() throws Exception {
        for (int i = 0; i < 50; i++) {
            pb.sendInNewTx();
        }

        assertTrue(JMSSingleConnectionTest.Listener.sync());
        assertEquals(1, TestTomEEConnectionFactory.getConnectionsCreatedCount());

        final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> objs = mBeanServer.queryNames(new ObjectName("org.apache.activemq:type=Broker,brokerName=localhost,endpoint=dynamicProducer,*"), null);
        Assert.assertEquals(0, objs.size());
    }

    @Test
    public void receive() throws InterruptedException {
        final String text = TEXT + "2";
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final CountDownLatch ready = new CountDownLatch(1);
        final CountDownLatch over = new CountDownLatch(1);
        new Thread() {
            @Override
            public void run() {
                {
                    setName(JMSSingleConnectionTest.class.getName() + ".receive#receiver");
                }

                try (final JMSContext context = incf.createContext()) {
                    try (final JMSConsumer consumer = context.createConsumer(destination2)) {
                        ready.countDown();
                        assertEquals(text, consumer.receiveBody(String.class, TimeUnit.MINUTES.toMillis(1)));
                    }
                } catch (final Throwable ex) {
                    error.set(ex);
                } finally {
                    over.countDown();
                }
            }
        }.start();

        ready.await(1, TimeUnit.MINUTES);
        sleep(150); // just to ensure we called receive already

        // now send the message
        try (final JMSContext context = cf.createContext()) {
            context.createProducer().send(destination2, text);
        } catch (final JMSRuntimeException ex) {
            fail(ex.getMessage());
        }

        over.await(1, TimeUnit.MINUTES);

        // ensure we got the message and no exception
        final Throwable exception = error.get();
        if (exception != null) {
            exception.printStackTrace();
        }
        assertNull(exception == null ? "ok" : exception.getMessage(), exception);
        assertEquals(2, TestTomEEConnectionFactory.getConnectionsCreatedCount());
    }

    @Test
    public void receiveGetBody() throws InterruptedException {
        final String text = TEXT + "2";
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final CountDownLatch ready = new CountDownLatch(1);
        final CountDownLatch over = new CountDownLatch(1);
        new Thread() {
            @Override
            public void run() {
                {
                    setName(JMSSingleConnectionTest.class.getName() + ".receiveGetBody#receiver");
                }

                try (final JMSContext context = incf.createContext()) {
                    try (final JMSConsumer consumer = context.createConsumer(destination2)) {
                        ready.countDown();
                        final Message receive = consumer.receive(TimeUnit.MINUTES.toMillis(1));
                        assertEquals(text, receive.getBody(String.class));
                    }
                } catch (final Throwable ex) {
                    error.set(ex);
                } finally {
                    over.countDown();
                }
            }
        }.start();

        ready.await(1, TimeUnit.MINUTES);
        sleep(150); // just to ensure we called receive already

        // now send the message
        try (final JMSContext context = cf.createContext()) {
            context.createProducer().send(destination2, text);
        } catch (final JMSRuntimeException ex) {
            fail(ex.getMessage());
        }

        over.await(1, TimeUnit.MINUTES);

        // ensure we got the message and no exception
        final Throwable exception = error.get();
        if (exception != null) {
            exception.printStackTrace();
        }
        assertNull(exception == null ? "ok" : exception.getMessage(), exception);
        assertEquals(2, TestTomEEConnectionFactory.getConnectionsCreatedCount());
    }

    @MessageDriven(activationConfig = {
            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
            @ActivationConfigProperty(propertyName = "destination", propertyValue = "target")
    })
    public static class Listener implements MessageListener {
        public static volatile CountDownLatch latch;
        public static volatile boolean ok = false;

        @Override
        public void onMessage(final Message message) {
            try {
                try {
                    ok = (TextMessage.class.isInstance(message)
                            && TEXT.equals(TextMessage.class.cast(message).getText())
                            && TEXT.equals(message.getBody(String.class)))
                            || message.getStringProperty("text").equals(TEXT);
                } catch (final JMSException e) {
                    // no-op
                }
            } finally {
                latch.countDown();
            }
        }

        public static void reset() {
            latch = new CountDownLatch(1);
            ok = false;
        }

        public static boolean sync() throws InterruptedException {
            latch.await(1, TimeUnit.MINUTES);
            return ok;
        }
    }

    @TransactionScoped
    public static class JustHereToCheckDeploymentIsOk implements Serializable {
        @Inject
        private JMSContext context;

        public void ok() {
            assertNotNull(context);
        }
    }

    @Singleton
    public static class ProducerBean {
        @Inject
        @JMSConnectionFactory("cf")
        private JMSContext context;

        @Resource(name = "target")
        private Queue destination;

        @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
        public void sendInNewTx() {
            context.createProducer().send(destination, TEXT);
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
}
