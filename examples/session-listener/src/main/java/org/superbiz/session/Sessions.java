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
package org.superbiz.session;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Sessions {

    private static final Map<String, String> SESSION_PRINCIPALS = new ConcurrentHashMap<>();

    private Sessions() {
        //NO-OP
    }

    public static void inSession(final String sessionId, final String principal) {
        SESSION_PRINCIPALS.put(sessionId, principal);
    }

    public static int userCount() {
        return new HashSet<>(SESSION_PRINCIPALS.values()).size();
    }


    public static void sessionDestroyed(final String sessionId) {
        SESSION_PRINCIPALS.remove(sessionId);
    }
}
