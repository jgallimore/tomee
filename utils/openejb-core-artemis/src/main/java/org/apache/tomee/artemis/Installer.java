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
package org.apache.tomee.artemis;

import org.apache.openejb.assembler.classic.event.AssemblerCreated;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.observer.Observes;

public class Installer {

    public void install(@Observes final AssemblerCreated assemblerCreated) {
        // this gently persuades TomEE to use the service-jar.xml we have defined here

        final SystemInstance si = SystemInstance.get();
        si.setProperty("openejb.provider.default", "org.apache.tomee.artemis");
    }


}
