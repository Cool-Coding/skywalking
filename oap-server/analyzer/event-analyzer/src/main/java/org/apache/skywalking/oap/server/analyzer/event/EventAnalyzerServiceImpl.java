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
 *
 */

package org.apache.skywalking.oap.server.analyzer.event;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.event.v3.Event;
import org.apache.skywalking.oap.server.analyzer.event.listener.EventAnalyzerListener;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.analyzer.event.listener.EventAnalyzerListenerFactoryManager;

@Slf4j
@RequiredArgsConstructor
public class EventAnalyzerServiceImpl implements EventAnalyzerService, EventAnalyzerListenerFactoryManager {
    private final ModuleManager moduleManager;

    private final List<EventAnalyzerListener.Factory> factories = new ArrayList<>();

    @Override
    public void analyze(final Event event) {
        final Event.Builder eb = event.toBuilder();
        if (event.getStartTime() <= 0 && event.getEndTime() <= 0) {
            log.warn(
                "Event start time {} and end time {} are both invalid, they will be set to current time, eventId: {}",
                event.getStartTime(),
                event.getEndTime(),
                event.getUuid());
            eb.setStartTime(System.currentTimeMillis());
            eb.setEndTime(System.currentTimeMillis());
        }

        final EventAnalyzer analyzer = new EventAnalyzer(moduleManager, this);
        analyzer.analyze(eb.build());
    }

    @Override
    public void add(final EventAnalyzerListener.Factory factory) {
        factories.add(factory);
    }

    @Override
    public List<EventAnalyzerListener.Factory> factories() {
        return factories;
    }
}
