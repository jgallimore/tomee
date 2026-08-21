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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.openejb.itest.soak.ejb;

import jakarta.ejb.Remote;

/**
 * The "relay" business interface, deployed on server B. Each {@link #relay(int, int)}
 * invocation makes a remote https-ejbd call from server B to the {@link Calculator}
 * on server A — reproducing the production TomEE-to-TomEE topology.
 */
@Remote
public interface Caller {

    /**
     * Relay a computation to the remote Calculator on server A over https-ejbd.
     *
     * @return "&lt;calleeName&gt;:&lt;sum&gt;" so the driver can see the call round-tripped
     */
    String relay(int a, int b);

    /**
     * @return the number of successful relays performed by this server since start
     */
    long count();
}
