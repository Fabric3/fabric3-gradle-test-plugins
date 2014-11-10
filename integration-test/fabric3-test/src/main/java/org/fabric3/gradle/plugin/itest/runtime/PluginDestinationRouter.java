/*
 * Fabric3
 * Copyright (c) 2009-2015 Metaform Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fabric3.gradle.plugin.itest.runtime;

import org.fabric3.api.annotation.monitor.MonitorLevel;
import org.fabric3.api.host.monitor.DestinationRouter;
import org.fabric3.api.host.monitor.MessageFormatter;
import org.gradle.api.logging.Logger;

/**
 * Forwards monitor events to the Gradle logger.
 */
public class PluginDestinationRouter implements DestinationRouter {
    private Logger logger;

    public PluginDestinationRouter(Logger logger) {
        this.logger = logger;
    }

    public int getDestinationIndex(String name) {
        return 0;
    }

    public void send(MonitorLevel level, int destinationIndex, long timestamp, String source, String message, boolean parse, Object... args) {
        message = MessageFormatter.format(message, args);

        if (MonitorLevel.SEVERE == level) {
            if (logger.isErrorEnabled()) {
                Throwable e = null;
                for (Object o : args) {
                    if (o instanceof Throwable) {
                        e = (Throwable) o;
                    }
                }
                if (message != null) {
                    logger.error(message, e);
                } else {
                    logger.error(e.getMessage(), e);
                }
            }
        } else if (MonitorLevel.WARNING == level) {
            if (logger.isWarnEnabled()) {
                logger.warn(message);
            }
        } else if (MonitorLevel.INFO == level) {
            if (logger.isInfoEnabled()) {
                logger.lifecycle(message);
            }
        } else if (MonitorLevel.DEBUG == level) {
            if (logger.isDebugEnabled()) {
                logger.debug(message);
            }
        } else if (MonitorLevel.TRACE == level) {
            if (logger.isDebugEnabled()) {
                logger.debug(message);
            }
        }
    }

}