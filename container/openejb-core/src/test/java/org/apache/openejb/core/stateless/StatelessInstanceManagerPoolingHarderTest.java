/**
 *
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
package org.apache.openejb.core.stateless;

import junit.framework.TestCase;
import org.apache.openejb.OpenEJB;
import org.apache.openejb.assembler.classic.Assembler;
import org.apache.openejb.assembler.classic.ProxyFactoryInfo;
import org.apache.openejb.assembler.classic.SecurityServiceInfo;
import org.apache.openejb.assembler.classic.StatelessSessionContainerInfo;
import org.apache.openejb.assembler.classic.TransactionServiceInfo;
import org.apache.openejb.config.ConfigurationFactory;
import org.apache.openejb.core.LocalInitialContextFactory;
import org.apache.openejb.jee.EjbJar;
import org.apache.openejb.jee.StatelessBean;

import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.naming.InitialContext;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @version $Revision$ $Date$
 */
public class StatelessInstanceManagerPoolingHarderTest extends TestCase {

    public static final AtomicInteger instances = new AtomicInteger();

    public void testStatelessBeanPooling() throws Exception {

        final InitialContext ctx = new InitialContext();
        final Object object = ctx.lookup("CounterBeanLocal");
        assertTrue("instanceof counter", object instanceof Counter);

        final CountDownLatch startPistol = new CountDownLatch(1);
        final CountDownLatch startingLine = new CountDownLatch(20);
        final CountDownLatch finishingLine = new CountDownLatch(20);

        final Counter counter = (Counter) object;
        // Do a business method...
        final Runnable r = new Runnable() {
            public void run() {
            startingLine.countDown();

            try {
                startPistol.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            for (int i = 0; i < 100; i++) {
                counter.doWork();
            }

            finishingLine.countDown();
            }
        };

        //  -- READY --

        // How much ever the no of client invocations the count should be 10 as only 10 instances will be created.
        for (int i = 0; i < 20; i++) {
            final Thread t = new Thread(r);
            t.start();
        }

        // log out total number of instances created during the whole test. Log this every second.
        new Thread() {
            @Override
            public void run() {
                try {
                    startPistol.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                do {

                    System.out.println("Instances = " + instances.get());
                    try {
                        final boolean complete = finishingLine.await(1, TimeUnit.SECONDS);
                        if (complete) {
                            break;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                } while(true);
            }
        }.start();

        // Wait for the beans to reach the finish line
        startingLine.await(1000, TimeUnit.MILLISECONDS);

        //  -- GO --

        startPistol.countDown(); // go

        finishingLine.await(10, TimeUnit.MINUTES);

        //  -- DONE --

        assertEquals(10, instances.get());

    }

    protected void setUp() throws Exception {
        super.setUp();

        System.setProperty(javax.naming.Context.INITIAL_CONTEXT_FACTORY, LocalInitialContextFactory.class.getName());

        final ConfigurationFactory config = new ConfigurationFactory();
        final Assembler assembler = new Assembler();

        assembler.createProxyFactory(config.configureService(ProxyFactoryInfo.class));
        assembler.createTransactionManager(config.configureService(TransactionServiceInfo.class));
        assembler.createSecurityService(config.configureService(SecurityServiceInfo.class));

        // containers
        final StatelessSessionContainerInfo statelessContainerInfo = config.configureService(StatelessSessionContainerInfo.class);
        statelessContainerInfo.properties.setProperty("MaxSize", "10");
        statelessContainerInfo.properties.setProperty("MinSize", "2");
        statelessContainerInfo.properties.setProperty("StrictPooling", "true");
        assembler.createContainer(statelessContainerInfo);

        // Setup the descriptor information

        final StatelessBean bean = new StatelessBean(CounterBean.class);
        bean.addBusinessLocal(Counter.class.getName());
        bean.addBusinessRemote(RemoteCounter.class.getName());

        final EjbJar ejbJar = new EjbJar();
        ejbJar.addEnterpriseBean(bean);

        instances.set(0);
        assembler.createApplication(config.configureApplication(ejbJar));
    }

    @Override
    protected void tearDown() throws Exception {
        OpenEJB.destroy();
    }

    public interface Counter {
        void doWork();
    }

    @Remote
    public interface RemoteCounter extends Counter {

    }

    @Stateless
    public static class CounterBean implements Counter, RemoteCounter {

        public CounterBean() {
            instances.incrementAndGet();
        }

        public void doWork() {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
