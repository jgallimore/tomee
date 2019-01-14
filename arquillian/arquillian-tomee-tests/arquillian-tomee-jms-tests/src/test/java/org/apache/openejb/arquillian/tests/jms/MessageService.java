/**
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

package org.apache.openejb.arquillian.tests.jms;

import javax.annotation.Resource;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.jms.*;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

@Path("jms")
@Singleton
@Lock(LockType.READ)
public class MessageService {

    @Resource(name="CF")
    private ConnectionFactory cf;

    @Resource(name="testTopic")
    private Topic topic;

    @POST
    public void sendMessage(final String payload) {

        try (final Connection connection = cf.createConnection();
             final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            final MessageProducer producer = session.createProducer(topic);
            producer.send(session.createTextMessage(payload));

        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }


}
