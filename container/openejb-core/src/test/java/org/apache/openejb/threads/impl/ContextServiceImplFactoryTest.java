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
package org.apache.openejb.threads.impl;

import jakarta.enterprise.concurrent.ContextServiceDefinition;
import jakarta.enterprise.concurrent.spi.ThreadContextProvider;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ContextServiceImplFactoryTest {

    @Test
    public void testShouldCreateAContextServiceImpl() throws Exception {
        final ContextServiceImplFactory contextServiceImplFactory = new ContextServiceImplFactory();
        contextServiceImplFactory.setCleared(ContextServiceDefinition.SECURITY + "," + ContextServiceDefinition.TRANSACTION);
        contextServiceImplFactory.setPropagated(ContextServiceDefinition.APPLICATION);
        final ContextServiceImpl contextService = contextServiceImplFactory.create();

        final List<ThreadContextProvider> propagated = contextService.getPropagated();
        Assert.assertNotNull(propagated);
        Assert.assertEquals(1, propagated.size());
        {
            final ThreadContextProvider actual = contextService.getPropagated().get(0);
            Assert.assertTrue(actual instanceof ApplicationThreadContextProvider);
            Assert.assertEquals(ContextServiceDefinition.APPLICATION, actual.getThreadContextType());
        }
        final List<ThreadContextProvider> cleared = contextService.getCleared();
        Assert.assertNotNull(cleared);
        Assert.assertEquals(2, cleared.size());
        {
            final ThreadContextProvider actual = contextService.getCleared().get(0);
            Assert.assertTrue(actual instanceof SecurityThreadContextProvider);
            Assert.assertEquals(ContextServiceDefinition.SECURITY, actual.getThreadContextType());
        }
        {
            final ThreadContextProvider actual = contextService.getCleared().get(1);
            Assert.assertTrue(actual instanceof TxThreadContextProvider);
            Assert.assertEquals(ContextServiceDefinition.TRANSACTION, actual.getThreadContextType());
        }

        final List<ThreadContextProvider> unchanged = contextService.getUnchanged();
        Assert.assertNotNull(unchanged);
        Assert.assertEquals(0, unchanged.size());
    }

    public void testContextProvidersShouldBeInTheCorrectOrder() throws Exception {

    }

    public void testMissingContextProvidersShouldBeIgnored() throws Exception {

    }

    public void testAllRemainingOnPropagatedTakesPrecedenceOverCleared() throws Exception {

    }

    public void testAllRemainingOnClearedTakesPrecedenceOverUnchanged() throws Exception {

    }

}