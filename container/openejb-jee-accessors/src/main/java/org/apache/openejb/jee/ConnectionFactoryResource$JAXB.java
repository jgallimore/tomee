/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.openejb.jee;

import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import jakarta.xml.bind.annotation.adapters.CollapsedStringAdapter;
import org.metatype.sxc.jaxb.JAXBObject;
import org.metatype.sxc.jaxb.LifecycleCallback;
import org.metatype.sxc.jaxb.RuntimeContext;
import org.metatype.sxc.util.Attribute;
import org.metatype.sxc.util.XoXMLStreamReader;
import org.metatype.sxc.util.XoXMLStreamWriter;


import static org.apache.openejb.jee.Property$JAXB.readProperty;
import static org.apache.openejb.jee.Property$JAXB.writeProperty;
import static org.apache.openejb.jee.Text$JAXB.readText;
import static org.apache.openejb.jee.Text$JAXB.writeText;
import static org.apache.openejb.jee.TransactionSupportType$JAXB.parseTransactionSupportType;
import static org.apache.openejb.jee.TransactionSupportType$JAXB.toStringTransactionSupportType;

@SuppressWarnings({
    "StringEquality"
})
public class ConnectionFactoryResource$JAXB
    extends JAXBObject<org.apache.openejb.jee.ConnectionFactoryResource>
{


    public ConnectionFactoryResource$JAXB() {
        super(org.apache.openejb.jee.ConnectionFactoryResource.class, null, new QName("http://java.sun.com/xml/ns/javaee".intern(), "connection-factory-resourceType".intern()), Text$JAXB.class, Property$JAXB.class, TransactionSupportType$JAXB.class);
    }

    public static org.apache.openejb.jee.ConnectionFactoryResource readConnectionFactoryResource(final XoXMLStreamReader reader, final RuntimeContext context)
        throws Exception
    {
        return _read(reader, context);
    }

    public static void writeConnectionFactoryResource(final XoXMLStreamWriter writer, final org.apache.openejb.jee.ConnectionFactoryResource connectionFactoryResource, final RuntimeContext context)
        throws Exception
    {
        _write(writer, connectionFactoryResource, context);
    }

    public void write(final XoXMLStreamWriter writer, final org.apache.openejb.jee.ConnectionFactoryResource connectionFactoryResource, final RuntimeContext context)
        throws Exception
    {
        _write(writer, connectionFactoryResource, context);
    }

    public static final org.apache.openejb.jee.ConnectionFactoryResource _read(final XoXMLStreamReader reader, final RuntimeContext runtimeContext)
        throws Exception
    {

        // Check for xsi:nil
        if (reader.isXsiNil()) {
            return null;
        }

        final RuntimeContext context = runtimeContext == null ? new RuntimeContext() : runtimeContext;

        final org.apache.openejb.jee.ConnectionFactoryResource connectionFactoryResource = new org.apache.openejb.jee.ConnectionFactoryResource();
        context.beforeUnmarshal(connectionFactoryResource, LifecycleCallback.NONE);

        ArrayList<Text> descriptions = null;
        List<Property> property = null;

        // Check xsi:type
        final QName xsiType = reader.getXsiType();
        if (xsiType!= null) {
            if (("connection-factory-resourceType"!= xsiType.getLocalPart())||("http://java.sun.com/xml/ns/javaee"!= xsiType.getNamespaceURI())) {
                return context.unexpectedXsiType(reader, org.apache.openejb.jee.ConnectionFactoryResource.class);
            }
        }

        // Read attributes
        for (final Attribute attribute: reader.getAttributes()) {
            if (("id" == attribute.getLocalName())&&(("" == attribute.getNamespace())||(attribute.getNamespace() == null))) {
                // ATTRIBUTE: id
                final String id = Adapters.collapsedStringAdapterAdapter.unmarshal(attribute.getValue());
                context.addXmlId(reader, id, connectionFactoryResource);
                connectionFactoryResource.id = id;
            } else if (XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI!= attribute.getNamespace()) {
                context.unexpectedAttribute(attribute, new QName("", "id"));
            }
        }

        // Read elements
        for (final XoXMLStreamReader elementReader: reader.getChildElements()) {
            if (("description" == elementReader.getLocalName())&&("http://java.sun.com/xml/ns/javaee" == elementReader.getNamespaceURI())) {
                // ELEMENT: descriptions
                final Text descriptionsItem = readText(elementReader, context);
                if (descriptions == null) {
                    descriptions = new ArrayList<>();
                }
                descriptions.add(descriptionsItem);
            } else if (("name" == elementReader.getLocalName())&&("http://java.sun.com/xml/ns/javaee" == elementReader.getNamespaceURI())) {
                // ELEMENT: name
                final String name = readString(elementReader, context);
                if (name == null) {
                    continue;
                }
                connectionFactoryResource.name = name;
            } else if (("interface-name" == elementReader.getLocalName())&&("http://java.sun.com/xml/ns/javaee" == elementReader.getNamespaceURI())) {
                // ELEMENT: interfaceName
                final String interfaceName = readString(elementReader, context);
                if (interfaceName == null) {
                    continue;
                }
                connectionFactoryResource.interfaceName = interfaceName;
            } else if (("resource-adapter" == elementReader.getLocalName())&&("http://java.sun.com/xml/ns/javaee" == elementReader.getNamespaceURI())) {
                // ELEMENT: resourceAdapter
                final String resourceAdapter = readString(elementReader, context);
                if (resourceAdapter == null) {
                    continue;
                }
                connectionFactoryResource.resourceAdapter = resourceAdapter;
            } else if (("max-pool-size" == elementReader.getLocalName())&&("http://java.sun.com/xml/ns/javaee" == elementReader.getNamespaceURI())) {
                // ELEMENT: maxPoolSize
                final Integer maxPoolSize = Integer.valueOf(elementReader.getElementText().trim());
                connectionFactoryResource.maxPoolSize = maxPoolSize;
            } else if (("min-pool-size" == elementReader.getLocalName())&&("http://java.sun.com/xml/ns/javaee" == elementReader.getNamespaceURI())) {
                // ELEMENT: minPoolSize
                final Integer minPoolSize = Integer.valueOf(elementReader.getElementText().trim());
                connectionFactoryResource.minPoolSize = minPoolSize;
            } else if (("transaction-support" == elementReader.getLocalName())&&("http://java.sun.com/xml/ns/javaee" == elementReader.getNamespaceURI())) {
                // ELEMENT: transactionSupport
                final TransactionSupportType transactionSupport = parseTransactionSupportType(elementReader, context, elementReader.getElementText().trim());
                if (transactionSupport!= null) {
                    connectionFactoryResource.transactionSupport = transactionSupport;
                }
            } else if (("property" == elementReader.getLocalName())&&("http://java.sun.com/xml/ns/javaee" == elementReader.getNamespaceURI())) {
                // ELEMENT: property
                final Property propertyItem = readProperty(elementReader, context);
                if (property == null) {
                    property = connectionFactoryResource.property;
                    if (property!= null) {
                        property.clear();
                    } else {
                        property = new ArrayList<>();
                    }
                }
                property.add(propertyItem);
            } else {
                context.unexpectedElement(elementReader, new QName("http://java.sun.com/xml/ns/javaee", "description"), new QName("http://java.sun.com/xml/ns/javaee", "name"), new QName("http://java.sun.com/xml/ns/javaee", "interface-name"), new QName("http://java.sun.com/xml/ns/javaee", "resource-adapter"), new QName("http://java.sun.com/xml/ns/javaee", "max-pool-size"), new QName("http://java.sun.com/xml/ns/javaee", "min-pool-size"), new QName("http://java.sun.com/xml/ns/javaee", "transaction-support"), new QName("http://java.sun.com/xml/ns/javaee", "property"));
            }
        }
        if (descriptions!= null) {
            try {
                connectionFactoryResource.setDescriptions(descriptions.toArray(new Text[descriptions.size()]));
            } catch (final Exception e) {
                context.setterError(reader, org.apache.openejb.jee.ConnectionFactoryResource.class, "setDescriptions", Text[].class, e);
            }
        }
        if (property!= null) {
            connectionFactoryResource.property = property;
        }

        context.afterUnmarshal(connectionFactoryResource, LifecycleCallback.NONE);

        return connectionFactoryResource;
    }

    private static String readString(final XoXMLStreamReader elementReader, final RuntimeContext context)
        throws Exception
    {
        final String raw = elementReader.getElementText();
        try {
            return Adapters.collapsedStringAdapterAdapter.unmarshal(raw);
        } catch (final Exception e) {
            context.xmlAdapterError(elementReader, CollapsedStringAdapter.class, String.class, String.class, e);
            return null;
        }
    }

    public final org.apache.openejb.jee.ConnectionFactoryResource read(final XoXMLStreamReader reader, final RuntimeContext context)
        throws Exception
    {
        return _read(reader, context);
    }

    public static final void _write(final XoXMLStreamWriter writer, final org.apache.openejb.jee.ConnectionFactoryResource connectionFactoryResource, final RuntimeContext runtimeContext)
        throws Exception
    {
        if (connectionFactoryResource == null) {
            writer.writeXsiNil();
            return ;
        }

        final RuntimeContext context = runtimeContext == null ? new RuntimeContext() : runtimeContext;

        final String prefix = writer.getUniquePrefix("http://java.sun.com/xml/ns/javaee");
        if (org.apache.openejb.jee.ConnectionFactoryResource.class!= connectionFactoryResource.getClass()) {
            context.unexpectedSubclass(writer, connectionFactoryResource, org.apache.openejb.jee.ConnectionFactoryResource.class);
            return ;
        }

        context.beforeMarshal(connectionFactoryResource, LifecycleCallback.NONE);


        // ATTRIBUTE: id
        final String idRaw = connectionFactoryResource.id;
        if (idRaw!= null) {
            String id = null;
            try {
                id = Adapters.collapsedStringAdapterAdapter.marshal(idRaw);
            } catch (final Exception e) {
                context.xmlAdapterError(connectionFactoryResource, "id", CollapsedStringAdapter.class, String.class, String.class, e);
            }
            writer.writeAttribute("", "", "id", id);
        }

        // ELEMENT: descriptions
        Text[] descriptions = null;
        try {
            descriptions = connectionFactoryResource.getDescriptions();
        } catch (final Exception e) {
            context.getterError(connectionFactoryResource, "descriptions", org.apache.openejb.jee.ConnectionFactoryResource.class, "getDescriptions", e);
        }
        if (descriptions!= null) {
            for (final Text descriptionsItem: descriptions) {
                if (descriptionsItem!= null) {
                    writer.writeStartElement(prefix, "description", "http://java.sun.com/xml/ns/javaee");
                    writeText(writer, descriptionsItem, context);
                    writer.writeEndElement();
                }
            }
        }

        // ELEMENT: name
        final String name = writeString(connectionFactoryResource, "name", connectionFactoryResource.name, context);
        if (name!= null) {
            writer.writeStartElement(prefix, "name", "http://java.sun.com/xml/ns/javaee");
            writer.writeCharacters(name);
            writer.writeEndElement();
        } else {
            context.unexpectedNullValue(connectionFactoryResource, "name");
        }

        // ELEMENT: interfaceName
        final String interfaceName = writeString(connectionFactoryResource, "interfaceName", connectionFactoryResource.interfaceName, context);
        if (interfaceName!= null) {
            writer.writeStartElement(prefix, "interface-name", "http://java.sun.com/xml/ns/javaee");
            writer.writeCharacters(interfaceName);
            writer.writeEndElement();
        } else {
            context.unexpectedNullValue(connectionFactoryResource, "interfaceName");
        }

        // ELEMENT: resourceAdapter
        final String resourceAdapter = writeString(connectionFactoryResource, "resourceAdapter", connectionFactoryResource.resourceAdapter, context);
        if (resourceAdapter!= null) {
            writer.writeStartElement(prefix, "resource-adapter", "http://java.sun.com/xml/ns/javaee");
            writer.writeCharacters(resourceAdapter);
            writer.writeEndElement();
        } else {
            context.unexpectedNullValue(connectionFactoryResource, "resourceAdapter");
        }

        // ELEMENT: maxPoolSize
        final Integer maxPoolSize = connectionFactoryResource.maxPoolSize;
        if (maxPoolSize!= null) {
            writer.writeStartElement(prefix, "max-pool-size", "http://java.sun.com/xml/ns/javaee");
            writer.writeCharacters(Integer.toString(maxPoolSize));
            writer.writeEndElement();
        }

        // ELEMENT: minPoolSize
        final Integer minPoolSize = connectionFactoryResource.minPoolSize;
        if (minPoolSize!= null) {
            writer.writeStartElement(prefix, "min-pool-size", "http://java.sun.com/xml/ns/javaee");
            writer.writeCharacters(Integer.toString(minPoolSize));
            writer.writeEndElement();
        }

        // ELEMENT: transactionSupport
        final TransactionSupportType transactionSupport = connectionFactoryResource.transactionSupport;
        if (transactionSupport!= null) {
            writer.writeStartElement(prefix, "transaction-support", "http://java.sun.com/xml/ns/javaee");
            writer.writeCharacters(toStringTransactionSupportType(connectionFactoryResource, null, context, transactionSupport));
            writer.writeEndElement();
        }

        // ELEMENT: property
        final List<Property> property = connectionFactoryResource.property;
        if (property!= null) {
            for (final Property propertyItem: property) {
                writer.writeStartElement(prefix, "property", "http://java.sun.com/xml/ns/javaee");
                if (propertyItem!= null) {
                    writeProperty(writer, propertyItem, context);
                } else {
                    writer.writeXsiNil();
                }
                writer.writeEndElement();
            }
        }

        context.afterMarshal(connectionFactoryResource, LifecycleCallback.NONE);
    }

    private static String writeString(final Object bean, final String fieldName, final String raw, final RuntimeContext context)
        throws Exception
    {
        try {
            return Adapters.collapsedStringAdapterAdapter.marshal(raw);
        } catch (final Exception e) {
            context.xmlAdapterError(bean, fieldName, CollapsedStringAdapter.class, String.class, String.class, e);
            return null;
        }
    }

}
