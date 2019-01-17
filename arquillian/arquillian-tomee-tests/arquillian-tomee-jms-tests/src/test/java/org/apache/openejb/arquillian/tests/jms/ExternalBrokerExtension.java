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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.openejb.arquillian.tests.jms;

import org.apache.activemq.broker.BrokerService;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.core.spi.LoadableExtension;
import org.jboss.arquillian.test.spi.event.suite.AfterClass;
import org.jboss.arquillian.test.spi.event.suite.BeforeClass;

public class ExternalBrokerExtension implements LoadableExtension {
    @Override
    public void register(final ExtensionBuilder builder) {
        builder.observer(ExternalBrokerObserver.class);
    }


    public static class ExternalBrokerObserver {


        private BrokerService broker = null;

        public void beforeClass(@Observes final BeforeClass beforeClass) {
            if (! beforeClass.getTestClass().isAnnotationPresent(RequireExternalBroker.class)) {
                return;
            }

            broker = createBroker();
            try {
                broker.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void afterClass(@Observes final AfterClass afterClass) {
            if (! afterClass.getTestClass().isAnnotationPresent(RequireExternalBroker.class)) {
                return;
            }

            if (broker != null) {
                try {
                    broker.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                broker = null;
            }
        }

        protected BrokerService createBroker() {
            BrokerService broker = new BrokerService();
            broker.setPersistent(false);
            broker.setUseJmx(false);
            broker.setAdvisorySupport(false);
            broker.setSchedulerSupport(false);

            return broker;
        }
    }


}
