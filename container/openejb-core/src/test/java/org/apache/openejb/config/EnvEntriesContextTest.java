/**
 * Licensed to the Apache Software Foundation (ASF) under context or more
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
package org.apache.openejb.config;

import junit.framework.TestCase;
import org.apache.openejb.jee.EjbJar;
import org.apache.openejb.jee.EnvEntry;
import org.apache.openejb.jee.SingletonBean;
import org.apache.openejb.junit.ApplicationComposer;
import org.apache.openejb.testing.Module;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ejb.EJB;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;

/**
 * @version $Rev$ $Date$
 */
@RunWith(ApplicationComposer.class)
public class EnvEntriesContextTest extends TestCase {

    @EJB(name = "Red")
    public Color red;

    @EJB(name = "Blue")
    public Color blue;

    @EJB(name = "None")
    public Color none;

    @EJB
    public Colors colors;

    @Module
    public EjbJar foo() {
        final EjbJar ejbJar = new EjbJar();

        final SingletonBean redBean = new SingletonBean("Red", Color.class);
        redBean.getEnvEntry().add(new EnvEntry().name("color").type(String.class).value("red"));
        ejbJar.addEnterpriseBean(redBean);

        final SingletonBean blueBean = new SingletonBean("Blue", Color.class);
        blueBean.getEnvEntry().add(new EnvEntry().name("color").type(String.class).value("blue"));
        ejbJar.addEnterpriseBean(blueBean);

        final SingletonBean noneBean = new SingletonBean("None", Color.class);
        ejbJar.addEnterpriseBean(noneBean);

        final SingletonBean colorsBean = new SingletonBean("Colors", Colors.class);
        colorsBean.getEnvEntry().add(new EnvEntry().name("color").type(String.class).value("yellow"));
        ejbJar.addEnterpriseBean(colorsBean);

        return ejbJar;
    }

    @Test
    public void test() throws Exception {
        assertEquals("red", red.getColor());
        assertEquals("blue", blue.getColor());

        try {
            none.getColor();
            fail("Expected exception NameNotFoundException not thrown");
        } catch (NameNotFoundException e) {
            // expected
        }

        assertEquals("red", colors.getRed());
        assertEquals("blue", colors.getBlue());

        try {
            colors.getNone();
            fail("Expected exception NameNotFoundException not thrown");
        } catch (NameNotFoundException e) {
            // expected
        }
    }

    public static class Color {

        public String getColor() throws Exception {
            return (String) new InitialContext().lookup("java:comp/env/color");
        }

    }

    public static class Colors {

        @EJB(name = "Red")
        public Color red;

        @EJB(name = "Blue")
        public Color blue;

        @EJB(name = "None")
        public Color none;

        public String getRed() throws Exception {
            return red.getColor();
        }

        public String getBlue() throws Exception {
            return blue.getColor();
        }

        public String getNone() throws Exception {
            return none.getColor();
        }

    }
}
