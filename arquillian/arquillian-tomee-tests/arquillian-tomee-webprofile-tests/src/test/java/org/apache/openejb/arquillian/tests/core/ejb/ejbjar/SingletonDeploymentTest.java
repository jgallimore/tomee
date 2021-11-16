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

package org.apache.openejb.arquillian.tests.core.ejb.ejbjar;

import javassist.runtime.Desc;
import org.apache.ziplock.IO;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.beans11.BeansDescriptor;
import org.jboss.shrinkwrap.descriptor.api.ejbjar31.ConcurrencyManagementTypeType;
import org.jboss.shrinkwrap.descriptor.api.ejbjar31.EjbJarDescriptor;
import org.jboss.shrinkwrap.descriptor.api.ejbjar31.SessionTypeType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ejb.ConcurrencyManagement;
import javax.ejb.EJB;
import javax.ejb.Remote;
import javax.ejb.Singleton;
import javax.ejb.Startup;

import java.io.File;
import java.net.URL;

import static javax.ejb.ConcurrencyManagementType.BEAN;

@RunWith(Arquillian.class)
public class SingletonDeploymentTest {
    private static final String TEST_NAME = SingletonDeploymentTest.class.getSimpleName();

    @ArquillianResource
    private URL endpoint;

    @Deployment(testable = false)
    public static EnterpriseArchive createDeployment() {

        final EnterpriseArchive enterpriseArchive = ShrinkWrap.create(EnterpriseArchive.class, TEST_NAME + ".ear");

        final JavaArchive commonJarArchive = ShrinkWrap.create(JavaArchive.class, "common.jar");
        commonJarArchive.addClass(ExampleSingletonService.class)
                .addClass(SingletonService.class);

        commonJarArchive.addAsManifestResource(new StringAsset("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<beans xmlns=\"http://xmlns.jcp.org/xml/ns/javaee\"\n" +
                "       xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "       xsi:schemaLocation=\"http://xmlns.jcp.org/xml/ns/javaee\n" +
                "                           http://xmlns.jcp.org/xml/ns/javaee/beans_1_1.xsd\"\n" +
                "       version=\"1.1\" bean-discovery-mode=\"all\">\n" +
                "</beans>"), "beans.xml");

        final EjbJarDescriptor ejbJarDescriptor = Descriptors.create(EjbJarDescriptor.class)
                .getOrCreateEnterpriseBeans()
                .createSession()
                .ejbName("SingletonService")
                .ejbClass(ExampleSingletonService.class.getName())
                .sessionType(SessionTypeType._SINGLETON)
                .concurrencyManagementType(ConcurrencyManagementTypeType._BEAN)
                .initOnStartup(true)
                .businessRemote(SingletonService.class.getName())
                .up().up();


        final JavaArchive ejbJarArchive = ShrinkWrap.create(JavaArchive.class, TEST_NAME + ".jar");
        ejbJarArchive.addAsManifestResource(new StringAsset(ejbJarDescriptor.exportAsString()), "ejb-jar.xml");

        enterpriseArchive.addAsLibraries(commonJarArchive);
        enterpriseArchive.addAsModule(ejbJarArchive);

        enterpriseArchive.as(ZipExporter.class).exportTo(new File("/tmp/" + TEST_NAME + ".ear") , true);

        return enterpriseArchive;
    }

    @Test
    public void testEcho() throws Exception {
    }

    public interface SingletonService {
        String echo(final String input);
    }

    @Singleton
    @Startup
    @ConcurrencyManagement(BEAN)
    @Remote(SingletonService.class)
    public static class ExampleSingletonService implements SingletonService {

        @Override
        public String echo(String input) {
            return input;
        }
    }

}
