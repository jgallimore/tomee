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
package org.apache.tomee.microprofile.metrics;

import org.apache.openejb.util.LogCategory;
import org.apache.openejb.util.Logger;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.MetricRegistry;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Set;

public class TomEEVendorMetricsExtension implements Extension {

    private final MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
    private final Logger logger = Logger.getInstance(LogCategory.MONITORING, TomEEVendorMetricsExtension.class);

    void afterValidation(@Observes final AfterDeploymentValidation validation, final BeanManager beanManager) {
        final MetricRegistry.Type registryType = MetricRegistry.Type.VENDOR;
        final Set<Bean<?>> beans = beanManager.getBeans(MetricRegistry.class, new RegistryTypeLiteral(registryType));
        final MetricRegistry registry = MetricRegistry.class.cast(
                beanManager.getReference(beanManager.resolve(beans), MetricRegistry.class, beanManager.createCreationalContext(null)));

        try {
            final Set<ObjectName> names = platformMBeanServer.queryNames(new ObjectName("Catalina:type=Manager,context=*,host=*"), null);
            names.forEach(name -> addGauges(registry, name));
        } catch (MalformedObjectNameException e) {
            logger.error("Unable to read manager objects from JMX.", e);
        }
    }

    private void addGauges(final MetricRegistry registry, final ObjectName name) {
        final String host = trimSlashes(name.getKeyProperty("host"));
        final String context = trimSlashes(name.getKeyProperty("context"));

        Arrays.stream(new String[] { "activeSessions", "expiredSessions", "rejectedSessions", "sessionAverageAliveTime", "sessionCounter", "sessionCreateRate", "sessionExpireRate" })
                .forEach(a -> addGauge(registry, name, host, context, a));
    }

    private void addGauge(final MetricRegistry registry, final ObjectName name, String host, String context, final String a) {
        registry.register(host + "." + context + "." + a, (Gauge<Integer>) () -> {
            try {
                return (Integer) platformMBeanServer.getAttribute(name, a);
            } catch (Exception e) {
                return -1;
            }
        });
    }

    private static String trimSlashes(final String input) {
        if (input == null) {
            return null;
        }

        if (input.trim().length() == 0) {
            return "";
        }

        return input.trim().replaceAll("^/*", "").replaceAll("/*$", "");
    }
}
