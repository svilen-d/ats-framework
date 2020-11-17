/*
 * Copyright 2020 Axway Software
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.axway.ats.agentapp.standalone.log;

import org.apache.logging.log4j.Level;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Filer logging of ATS logging DB &quot;SYSTEM level&quot; events
 */
public class NoSystemLevelEventsFilter extends Filter {
    public final static int SYSTEM_INT = Level.FATAL_INT + 10000; // prevent reference to com.axway.ats.log.model.SystemLogLevel

    @Override
    public int decide(
                       LoggingEvent loggingEvent ) {

        if (loggingEvent.getLevel().toInt() == SYSTEM_INT) {
            return DENY;
        }

        return NEUTRAL;
    }
}
