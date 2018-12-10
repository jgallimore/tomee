/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.openejb.arquillian.tests.cmp.sample;

import javax.ejb.CreateException;
import javax.ejb.EntityBean;
import javax.ejb.EntityContext;

public abstract class PersonBean implements EntityBean {

    public PersonBean() {
    }

    public Integer ejbCreate(final String name) {
        this.setName(name);
        return null;
    }

    public abstract Integer getId();

    public abstract void setId(Integer id);

    public abstract String getName();

    public abstract void setName(String name);

    public void ejbPostCreate(String name) throws CreateException {
    }

    public void setEntityContext(EntityContext ctx) {
    }

    public void unsetEntityContext() {
    }

    public void ejbRemove() {
    }

    public void ejbLoad() {
    }

    public void ejbStore() {
    }

    public void ejbPassivate() {
    }

    public void ejbActivate() {
    }
}
