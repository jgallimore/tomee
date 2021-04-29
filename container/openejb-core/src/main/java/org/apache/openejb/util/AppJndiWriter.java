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
package org.apache.openejb.util;

import org.apache.openejb.BeanContext;
import org.apache.openejb.assembler.classic.event.AssemblerAfterApplicationCreated;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.observer.Observes;
import org.apache.openejb.spi.ContainerSystem;

import javax.naming.Context;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Random;

public class AppJndiWriter {
    public static final String OUTPUT_JNDI = "openejb.jndi.output";
    public static final String OUTPUT_JNDI_FOLDER = "openejb.jndi.output.folder";

    public void writeDeploymentJndiTree(@Observes final AssemblerAfterApplicationCreated aaac) {
        final boolean output = SystemInstance.get().getOptions().get(OUTPUT_JNDI, false);

        if (! output) return;

        try {
            final String appId = aaac.getApp().appId;
            final File tempFile = tempFile(appId, ".txt");
            final ContainerSystem containerSystem = SystemInstance.get().getComponent(ContainerSystem.class);
            final Context rootContext = containerSystem.getJNDIContext();
            final Context appContext = (Context) rootContext.lookup("openejb:Deployment/" + appId);

            final PrintWriter pw = new PrintWriter(tempFile);
            pw.println("Deployment Context:");
            pw.println("===================");
            dump("", appContext, pw);

            final List<BeanContext> beanContexts = aaac.getContext().getBeanContexts();
            for (final BeanContext beanContext : beanContexts) {
                pw.println("\nBean Context: " + beanContext.getId());
                pw.println("===================");
                dump("", beanContext.getJndiContext(), pw);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void dump(final String prefix, final Context context, final PrintWriter writer) {
        try {
            final NamingEnumeration<NameClassPair> list = context.list("");
            while (list.hasMore()) {
                final NameClassPair element = list.next();
                final String name = element.getName();
                Object value = "";

                try {
                    value = context.lookup(name);
                } catch (NamingException e) {
                    value = "[ unable to lookup: " + e.getMessage() + " ]";
                }

                writer.println(prefix + name + " => " + value);


                if (value instanceof Context) {
                    dump (prefix + "  ", (Context) value, writer);
                }
            }

            writer.flush();
        } catch (NamingException e) {
            // ignore
        }
    }

    private File tempFile(final String start, final String end) throws IOException {
        if (SystemInstance.get().getOptions().get(OUTPUT_JNDI_FOLDER, (String) null) != null) {
            final File tmp = new File(SystemInstance.get().getOptions().get(OUTPUT_JNDI_FOLDER, ""));
            if (!tmp.exists()) {
                if (!tmp.mkdirs()) {
                    throw new IOException("can't create " + tmp.getAbsolutePath());
                }
            }
            return new File(tmp, start + Long.toString(new Random().nextInt()) + end);
        } else {
            try {
                return File.createTempFile(start, end);
            } catch (final Throwable e) {

                final File tmp = new File("tmp");
                if (!tmp.exists() && !tmp.mkdirs()) {
                    throw new IOException("Failed to create local tmp directory: " + tmp.getAbsolutePath());
                }

                return File.createTempFile(start, end, tmp);
            }
        }
    }
}
