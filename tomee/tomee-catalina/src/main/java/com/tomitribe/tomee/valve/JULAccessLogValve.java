/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2025
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tomee.valve;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.valves.AbstractAccessLogValve;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.io.CharArrayWriter;

public class JULAccessLogValve extends AbstractAccessLogValve {

    private Log logger;

    private Log getLogger() {
        return LogFactory.getLog(JULAccessLogValve.class);
    }

    @Override
    protected synchronized void startInternal() throws LifecycleException {
        logger = getLogger();
        setState(LifecycleState.STARTING);
    }

    @Override
    public void log(final CharArrayWriter message) {
        logger.info(message);
    }

}