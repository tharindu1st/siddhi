/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.siddhi.extension.string;

import junit.framework.Assert;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;
import org.wso2.siddhi.core.ExecutionPlanRuntime;
import org.wso2.siddhi.core.SiddhiManager;
import org.wso2.siddhi.core.event.Event;
import org.wso2.siddhi.core.query.output.callback.QueryCallback;
import org.wso2.siddhi.core.stream.input.InputHandler;
import org.wso2.siddhi.core.util.EventPrinter;
import org.wso2.siddhi.extension.string.test.util.SiddhiTestHelper;
import org.wso2.siddhi.query.api.exception.ExecutionPlanValidationException;

import java.util.concurrent.atomic.AtomicInteger;

public class LowerFunctionExtensionTestCase {
    static final Log log = LogFactory.getLog(LowerFunctionExtensionTestCase.class);
    private AtomicInteger count = new AtomicInteger(0);
    private volatile boolean eventArrived;

    @Before
    public void init() {
        count.set(0);
        eventArrived = false;
    }

    @Test
    public void testLowerFunctionExtension() throws InterruptedException {
        log.info("LowerFunctionExtension TestCase");
        SiddhiManager siddhiManager = new SiddhiManager();

        String inStreamDefinition = "define stream inputStream (symbol string, price long, volume long);";
        String query = ("@info(name = 'query1') from inputStream select symbol , "
                + "str:lower(symbol) as symbolInLowerCase " +
                "insert into outputStream;");
        ExecutionPlanRuntime executionPlanRuntime = siddhiManager.createExecutionPlanRuntime
                (inStreamDefinition + query);

        executionPlanRuntime.addCallback("query1", new QueryCallback() {
            @Override
            public void receive(long timeStamp, Event[] inEvents, Event[] removeEvents) {
                EventPrinter.print(timeStamp, inEvents, removeEvents);
                for (Event event : inEvents) {
                    count.incrementAndGet();
                    if (count.get() == 1) {
                        Assert.assertEquals("hello how are you ", event.getData(1));
                        //Note: Assertion doesn't count the spaces infront or in the back
                        eventArrived = true;
                    }
                    if (count.get() == 2) {
                        Assert.assertEquals(" my name is tharindu", event.getData(1));
                        eventArrived = true;
                    }
                    if (count.get() == 3) {
                        Assert.assertEquals("wso2 cep ", event.getData(1));
                        eventArrived = true;
                    }
                }
            }
        });

        InputHandler inputHandler = executionPlanRuntime.getInputHandler("inputStream");
        executionPlanRuntime.start();
        inputHandler.send(new Object[]{"HeLlo How Are you ", 700f, 100l});
        inputHandler.send(new Object[]{" My name Is ThArindU", 60.5f, 200l});
        inputHandler.send(new Object[]{"WSO2 cep ", 60.5f, 200l});
        SiddhiTestHelper.waitForEvents(100, 3, count, 60000);
        Assert.assertEquals(3, count.get());
        Assert.assertTrue(eventArrived);
        executionPlanRuntime.shutdown();
    }

    @Test(expected = ExecutionPlanValidationException.class)
    public void testLowerFunctionExtensionWithZeroArgument() throws InterruptedException {
        log.info("LowerFunctionExtension TestCase with zero argument");
        SiddhiManager siddhiManager = new SiddhiManager();

        String inStreamDefinition = "define stream inputStream (symbol string, price long, volume long);";
        String query = ("@info(name = 'query1') from inputStream select symbol , str:lower() as symbolInLowerCase " +
                "insert into outputStream;");
        ExecutionPlanRuntime executionPlanRuntime = siddhiManager.createExecutionPlanRuntime
                (inStreamDefinition + query);

        executionPlanRuntime.start();
        executionPlanRuntime.shutdown();
    }

    @Test(expected = ExecutionPlanValidationException.class)
    public void testLowerFunctionExtensionWithInvalidDataType() throws InterruptedException {
        log.info("LowerFunctionExtension TestCase with invalid data type");
        SiddhiManager siddhiManager = new SiddhiManager();

        String inStreamDefinition = "define stream inputStream (symbol string, price long, volume long);";
        String query = ("@info(name = 'query1') from inputStream select symbol , "
                + "str:lower(price) as symbolInLowerCase " +
                "insert into outputStream;");
        ExecutionPlanRuntime executionPlanRuntime = siddhiManager.createExecutionPlanRuntime
                (inStreamDefinition + query);

        executionPlanRuntime.start();
        executionPlanRuntime.shutdown();
    }

    @Test
    public void testLowerFunctionExtensionWithNullValue() throws InterruptedException {
        log.info("LowerFunctionExtension TestCase with null value");
        SiddhiManager siddhiManager = new SiddhiManager();

        String inStreamDefinition = "define stream inputStream (symbol string, price long, volume long);";
        String query = ("@info(name = 'query1') from inputStream select symbol , "
                + "str:lower(symbol) as symbolInLowerCase " +
                "insert into outputStream;");
        ExecutionPlanRuntime executionPlanRuntime = siddhiManager.createExecutionPlanRuntime
                (inStreamDefinition + query);

        InputHandler inputHandler = executionPlanRuntime.getInputHandler("inputStream");
        executionPlanRuntime.start();
        inputHandler.send(new Object[]{null, 700f, 100l});
        executionPlanRuntime.shutdown();
    }
}