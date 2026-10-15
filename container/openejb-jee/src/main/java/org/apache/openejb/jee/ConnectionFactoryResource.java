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
package org.apache.openejb.jee;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlID;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.CollapsedStringAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.ArrayList;
import java.util.List;

/**
 * javaee_7.xsd / jakartaee_11.xsd
 *
 * <p>Java class for connection-factory-resourceType complex type: the
 * {@code <connection-factory>} element, equivalent of
 * {@link jakarta.resource.ConnectionFactoryDefinition}.
 *
 * <pre>
 * &lt;complexType name="connection-factory-resourceType"&gt;
 *   &lt;sequence&gt;
 *     &lt;element name="description" type="{http://java.sun.com/xml/ns/javaee}descriptionType" minOccurs="0"/&gt;
 *     &lt;element name="name" type="{http://java.sun.com/xml/ns/javaee}jndi-nameType"/&gt;
 *     &lt;element name="interface-name" type="{http://java.sun.com/xml/ns/javaee}fully-qualified-classType"/&gt;
 *     &lt;element name="resource-adapter" type="{http://java.sun.com/xml/ns/javaee}string"/&gt;
 *     &lt;element name="max-pool-size" type="{http://java.sun.com/xml/ns/javaee}xsdIntegerType" minOccurs="0"/&gt;
 *     &lt;element name="min-pool-size" type="{http://java.sun.com/xml/ns/javaee}xsdIntegerType" minOccurs="0"/&gt;
 *     &lt;element name="transaction-support" type="{http://java.sun.com/xml/ns/javaee}transaction-supportType" minOccurs="0"/&gt;
 *     &lt;element name="property" type="{http://java.sun.com/xml/ns/javaee}propertyType" maxOccurs="unbounded" minOccurs="0"/&gt;
 *   &lt;/sequence&gt;
 *   &lt;attribute name="id" type="{http://www.w3.org/2001/XMLSchema}ID" /&gt;
 * &lt;/complexType&gt;
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "connection-factory-resourceType", propOrder = {
    "descriptions",
    "name",
    "interfaceName",
    "resourceAdapter",
    "maxPoolSize",
    "minPoolSize",
    "transactionSupport",
    "property"
})
public class ConnectionFactoryResource implements Keyable<String> {
    @XmlTransient
    protected TextMap description = new TextMap();
    @XmlElement(required = true)
    protected String name;
    @XmlElement(name = "interface-name", required = true)
    protected String interfaceName;
    @XmlElement(name = "resource-adapter", required = true)
    protected String resourceAdapter;
    @XmlElement(name = "max-pool-size")
    protected Integer maxPoolSize;
    @XmlElement(name = "min-pool-size")
    protected Integer minPoolSize;
    @XmlElement(name = "transaction-support")
    protected TransactionSupportType transactionSupport;
    protected List<Property> property;
    @XmlAttribute
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlID
    @XmlSchemaType(name = "ID")
    protected String id;

    @XmlElement(name = "description")
    public Text[] getDescriptions() {
        return description.toArray();
    }

    public void setDescriptions(final Text[] text) {
        description.set(text);
    }

    public String getDescription() {
        return description.get();
    }

    public ConnectionFactoryResource property(final String name, final String value) {
        getProperty().add(new Property(name, value));
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(final String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getResourceAdapter() {
        return resourceAdapter;
    }

    public void setResourceAdapter(final String resourceAdapter) {
        this.resourceAdapter = resourceAdapter;
    }

    public Integer getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(final Integer maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public Integer getMinPoolSize() {
        return minPoolSize;
    }

    public void setMinPoolSize(final Integer minPoolSize) {
        this.minPoolSize = minPoolSize;
    }

    public TransactionSupportType getTransactionSupport() {
        return transactionSupport;
    }

    public void setTransactionSupport(final TransactionSupportType transactionSupport) {
        this.transactionSupport = transactionSupport;
    }

    public List<Property> getProperty() {
        if (property == null) {
            property = new ArrayList<>();
        }
        return this.property;
    }

    public String getId() {
        return id;
    }

    public void setId(final String value) {
        this.id = value;
    }

    @Override
    public String getKey() {
        final String name = getName();
        if (name == null || name.startsWith("java:")) return name;
        return "java:comp/env/" + name;
    }
}
