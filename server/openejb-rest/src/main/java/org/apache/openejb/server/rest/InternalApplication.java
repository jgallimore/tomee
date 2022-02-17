/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *     contributor license agreements.  See the NOTICE file distributed with
 *     this work for additional information regarding copyright ownership.
 *     The ASF licenses this file to You under the Apache License, Version 2.0
 *     (the "License"); you may not use this file except in compliance with
 *     the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package org.apache.openejb.server.rest;

import org.apache.openejb.util.LogCategory;
import org.apache.openejb.util.Logger;

import javax.ws.rs.core.Application;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class InternalApplication extends Application {
    public static final Logger LOGGER = Logger.getInstance(LogCategory.OPENEJB_RS, InternalApplication.class);

    private final Set<Class<?>> classes;
    private final Set<Object> singletons;
    private final Application original;

    public InternalApplication(final Application original) {
        this.original = original;

        final HashSet<Object> singletons = new HashSet<>();
        final HashSet<Class<?>> classes = new HashSet<>();

        if (original != null) {
            singletons.addAll(original.getSingletons());
            classes.addAll(original.getClasses());
        }

        this.classes = Collections.unmodifiableSet(classes);
        this.singletons = Collections.unmodifiableSet(singletons);

        LOGGER.info("Create InternalApplication@" + hashCode() + "{original=" + (original != null ? original.getClass().getName() : "null") + "}");
        int classesCount = 0;
        for (final Class<?> aClass : classes) {
            LOGGER.info("InternalApplication.classes[" + classesCount++ + "]=" + (aClass != null ? aClass.getName() : "null"));
        }
        int singletonsCount = 0;
        for (final Object object : singletons) {
            LOGGER.info("InternalApplication.singletons[" + singletonsCount++ + "]=" + (object != null ? object.getClass().getName() : "null"));
        }

        LOGGER.info("InternalApplication Created", new Throwable());
    }

    @Override
    public Set<Class<?>> getClasses() {
        return classes;
    }

    @Override
    public Set<Object> getSingletons() {
        return singletons;
    }

    @Override
    public Map<String, Object> getProperties() {
        return original == null ? Collections.<String, Object>emptyMap() : original.getProperties();
    }

    public Application getOriginal() {
        return original;
    }

}
