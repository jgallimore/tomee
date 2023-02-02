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
package org.apache.openejb.assembler.classic;

import jakarta.ejb.EJB;
import jakarta.ejb.Singleton;
import jakarta.enterprise.concurrent.ContextService;
import jakarta.enterprise.concurrent.ContextServiceDefinition;
import org.apache.openejb.junit.ApplicationComposer;
import org.apache.openejb.testing.Module;
import org.apache.openejb.threads.impl.ContextServiceImpl;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import static jakarta.enterprise.concurrent.ContextServiceDefinition.APPLICATION;
import static jakarta.enterprise.concurrent.ContextServiceDefinition.TRANSACTION;
import static org.junit.Assert.assertNotNull;

@RunWith(ApplicationComposer.class)
public class ContextServiceDefinitionTest {

    @EJB
    private ContextServiceDefinitionBean singleContextDefinition;

    @Module
    public Class<?>[] app() throws Exception {
        return new Class<?>[]{ContextServiceDefinitionBean.class};
    }

    @ContextServiceDefinition(name = "java:app/concurrent/Orange",
            propagated = { APPLICATION, "Orange" },
            cleared = "Blue",
            unchanged = TRANSACTION)
    @Singleton
    public static class ContextServiceDefinitionBean {
        public ContextService lookupContextService() throws NamingException {
            return InitialContext.doLookup("java:app/concurrent/Orange");
        }

    }

    @Test
    public void assertContextServiceDefinitionCreated() throws Exception {
        final ContextService contextService = singleContextDefinition.lookupContextService();
        assertNotNull(contextService);
        Assert.assertTrue(contextService instanceof ContextServiceImpl);

        final ContextServiceImpl impl = (ContextServiceImpl) contextService;
        Assert.assertEquals("Application,Orange", impl.getPropagated());
        Assert.assertEquals("Blue", impl.getCleared());
        Assert.assertEquals("Transaction", impl.getUnchanged());
    }

}
