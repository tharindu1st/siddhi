/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.siddhi.core.query.output.ratelimit.snapshot;


import io.siddhi.core.config.SiddhiQueryContext;
import io.siddhi.core.event.ComplexEvent;
import io.siddhi.core.event.ComplexEventChunk;
import io.siddhi.core.event.GroupedComplexEvent;
import io.siddhi.core.event.stream.StreamEventPool;
import io.siddhi.core.util.Scheduler;
import io.siddhi.core.util.parser.SchedulerParser;
import io.siddhi.core.util.snapshot.state.State;
import io.siddhi.core.util.snapshot.state.StateFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link PerSnapshotOutputRateLimiter} for queries with GroupBy, Aggregators and Windows which
 * will output all events.
 */
public class AllAggregationGroupByWindowedPerSnapshotOutputRateLimiter
        extends SnapshotOutputRateLimiter<AllAggregationGroupByWindowedPerSnapshotOutputRateLimiter.RateLimiterState> {
    private final Long value;
    private Scheduler scheduler;
    private long scheduledTime;

    public AllAggregationGroupByWindowedPerSnapshotOutputRateLimiter(Long value,
                                                                     WrappedSnapshotOutputRateLimiter
                                                                             wrappedSnapshotOutputRateLimiter,
                                                                     boolean groupBy,
                                                                     SiddhiQueryContext siddhiQueryContext) {
        super(wrappedSnapshotOutputRateLimiter, siddhiQueryContext, groupBy);
        this.value = value;
    }

    @Override
    protected StateFactory<RateLimiterState> init() {
        return () -> new RateLimiterState();
    }

    @Override
    public void process(ComplexEventChunk complexEventChunk) {
        List<ComplexEventChunk<ComplexEvent>> outputEventChunks = new ArrayList<ComplexEventChunk<ComplexEvent>>();
        complexEventChunk.reset();
        RateLimiterState state = stateHolder.getState();
        try {
            synchronized (state) {
                while (complexEventChunk.hasNext()) {
                    ComplexEvent event = complexEventChunk.next();
                    if (event.getType() == ComplexEvent.Type.TIMER) {
                        tryFlushEvents(outputEventChunks, event, state);
                    } else {
                        complexEventChunk.remove();
                        tryFlushEvents(outputEventChunks, event, state);
                        GroupedComplexEvent groupedComplexEvent = ((GroupedComplexEvent) event);
                        LastEventHolder lastEventHolder = state.groupByKeyEvents.get(groupedComplexEvent.getGroupKey());
                        if (lastEventHolder == null) {
                            lastEventHolder = new LastEventHolder();
                            state.groupByKeyEvents.put(groupedComplexEvent.getGroupKey(), lastEventHolder);
                        }
                        if (groupedComplexEvent.getType() == ComplexEvent.Type.CURRENT) {
                            lastEventHolder.addLastInEvent(groupedComplexEvent.getComplexEvent());
                        } else if (groupedComplexEvent.getType() == ComplexEvent.Type.EXPIRED) {
                            lastEventHolder.removeLastInEvent(groupedComplexEvent.getComplexEvent());
                        } else if (groupedComplexEvent.getType() == ComplexEvent.Type.RESET) {
                            state.groupByKeyEvents.clear();
                        }
                    }
                }
            }
        } finally {
            stateHolder.returnState(state);
        }
        for (ComplexEventChunk eventChunk : outputEventChunks) {
            sendToCallBacks(eventChunk);
        }
    }


    private void tryFlushEvents(List<ComplexEventChunk<ComplexEvent>> outputEventChunks, ComplexEvent event,
                                RateLimiterState state) {
        if (event.getTimestamp() >= scheduledTime) {
            ComplexEventChunk<ComplexEvent> outputEventChunk = new ComplexEventChunk<ComplexEvent>(false);
            for (Iterator<Map.Entry<String, LastEventHolder>> iterator = state.groupByKeyEvents.entrySet().iterator();
                 iterator.hasNext(); ) {
                Map.Entry<String, LastEventHolder> lastEventHolderEntry = iterator.next();

                //clearing expired events after update
                lastEventHolderEntry.getValue().checkAndClearLastInEvent();
                if (lastEventHolderEntry.getValue().lastEvent == null) {
                    iterator.remove();
                } else {
                    outputEventChunk.add(cloneComplexEvent(lastEventHolderEntry.getValue().lastEvent));
                }
            }
            outputEventChunks.add(outputEventChunk);
            scheduledTime += value;
            scheduler.notifyAt(scheduledTime);
        }
    }

    @Override
    public void start() {
        scheduler = SchedulerParser.parse(this, siddhiQueryContext);
        scheduler.setStreamEventPool(new StreamEventPool(0, 0, 0, 5));
        scheduler.init(lockWrapper, siddhiQueryContext.getName());
        long currentTime = System.currentTimeMillis();
        scheduledTime = currentTime + value;
        scheduler.notifyAt(scheduledTime);
    }

    @Override
    public void stop() {
        //Nothing to stop
    }

    private class LastEventHolder {
        long count = 0;
        ComplexEvent lastEvent = null;

        public void addLastInEvent(ComplexEvent lastEvent) {
            this.lastEvent = lastEvent;
            count++;
        }

        public void removeLastInEvent(ComplexEvent lastEvent) {
            this.lastEvent = lastEvent;
            count--;
        }

        public void checkAndClearLastInEvent() {
            if (count <= 0) {
                lastEvent = null;
            }
        }
    }

    class RateLimiterState extends State {
        private Map<String, LastEventHolder> groupByKeyEvents = new LinkedHashMap<String, LastEventHolder>();

        @Override
        public boolean canDestroy() {
            return groupByKeyEvents.isEmpty();
        }

        @Override
        public Map<String, Object> snapshot() {
            Map<String, Object> state = new HashMap<>();
            synchronized (this) {
                state.put("GroupByKeyEvents", groupByKeyEvents);
            }
            return state;
        }

        @Override
        public void restore(Map<String, Object> state) {
            groupByKeyEvents = (Map<String, LastEventHolder>) state.get("GroupByKeyEvents");
        }
    }
}
