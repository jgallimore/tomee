/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.openejb.core.mdb;

import junit.framework.TestCase;
import org.apache.openejb.OpenEJB;
import org.apache.openejb.assembler.classic.Assembler;
import org.apache.openejb.assembler.classic.ResourceInfo;
import org.apache.openejb.assembler.classic.SecurityServiceInfo;
import org.apache.openejb.assembler.classic.TransactionServiceInfo;
import org.apache.openejb.config.ConfigurationFactory;
import org.apache.openejb.core.ivm.naming.InitContextFactory;
import org.apache.openejb.jee.EjbJar;
import org.apache.openejb.jee.EnvEntry;
import org.apache.openejb.jee.MessageDrivenBean;
import org.apache.openejb.util.NetworkUtil;
import org.junit.AfterClass;

import javax.annotation.Resource;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @version $Rev$ $Date$
 */
public class JmsEnvEntryTest extends TestCase {

    @AfterClass
    public static void afterClass() throws Exception {
        OpenEJB.destroy();
    }

    public void test() throws Exception {
        System.setProperty(javax.naming.Context.INITIAL_CONTEXT_FACTORY, InitContextFactory.class.getName());

        final ConfigurationFactory config = new ConfigurationFactory();
        final Assembler assembler = new Assembler();

        // define props for RA in order to change the default activeMQ port
        final Properties props = new Properties();
        final String brokerAddress = NetworkUtil.getLocalAddress("tcp://", "");
        final String brokerXmlConfig = "broker:(" + brokerAddress + ")?useJmx=false";
        props.put("BrokerXmlConfig", brokerXmlConfig);
        props.put("StartupTimeout", 10000);

        assembler.createTransactionManager(config.configureService(TransactionServiceInfo.class));
        assembler.createSecurityService(config.configureService(SecurityServiceInfo.class));
        assembler.createResource(config.configureService(ResourceInfo.class, "Default Unmanaged JDBC Database",
            new Properties(), "Default Unmanaged JDBC Database", "DataSource"));
        assembler.createResource(config.configureService(ResourceInfo.class, "Default JMS Resource Adapter",
            props, "Default JMS Resource Adapter", "ActiveMQResourceAdapter"));

        // Setup the descriptor information


        final EjbJar ejbJar = new EjbJar();

        final MessageDrivenBean receiverOne = new MessageDrivenBean("ReceiverOne", Receiver.class);
        receiverOne.getEnvEntry().add(new EnvEntry().name("color").type(String.class).value("blue"));
        ejbJar.addEnterpriseBean(receiverOne);

        final MessageDrivenBean receiverTwo = new MessageDrivenBean("ReceiverTwo", Receiver.class);
        ejbJar.addEnterpriseBean(receiverTwo);

        assembler.createApplication(config.configureApplication(ejbJar));

        final InitialContext initialContext = new InitialContext();

        final ConnectionFactory connectionFactory = (ConnectionFactory) initialContext.lookup("java:openejb/Resource/Default JMS Connection Factory");

        Receiver.envEntryValue.clear();
        sendMessage(connectionFactory, "ReceiverOne", "test");
        assertEquals(1, Receiver.envEntryValue.size());
        assertEquals("blue", Receiver.envEntryValue.get(0));

        Receiver.envEntryValue.clear();
        sendMessage(connectionFactory, "ReceiverTwo", "test");
        assertEquals(1, Receiver.envEntryValue.size());
        assertNull(Receiver.envEntryValue.get(0));
    }

    private void sendMessage(final ConnectionFactory connectionFactory, final String bean, final String text) throws JMSException, InterruptedException {
        Receiver.lock.lock();

        try {
            final Connection connection = connectionFactory.createConnection();
            connection.start();

            // Create a Session
            final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            final Queue queue = session.createQueue(bean);

            // Create a MessageProducer from the Session to the Topic or Queue
            final MessageProducer producer = session.createProducer(queue);
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

            // Create a message
            final TextMessage message = session.createTextMessage(text);

            // Tell the producer to send the message
            producer.send(message);

            Receiver.messageRecieved.await();
        } finally {
            Receiver.lock.unlock();
        }
    }

    public static class Receiver implements javax.jms.MessageListener {

        public static Lock lock = new ReentrantLock();
        public static Condition messageRecieved = lock.newCondition();
        public static List<String> envEntryValue = new ArrayList<String>();

        @Resource
        ConnectionFactory connectionFactory;

        @Override
        public void onMessage(final Message message) {
            String color = null;
            try {
                color = (String) new InitialContext().lookup("java:comp/env/color");
            } catch (NamingException e) {
                // use null
            }

            envEntryValue.add(color);

            lock.lock();
            try {
                messageRecieved.signalAll();
            } finally {
                lock.unlock();
            }
        }

    }
}
