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
package org.fabric3.gradle.plugin.test;

import org.fabric3.gradle.plugin.api.test.TestRecorder;
import org.fabric3.gradle.plugin.api.test.TestResult;
import org.fabric3.gradle.plugin.api.test.TestSuiteResult;
import org.fabric3.spi.container.invocation.Message;
import org.fabric3.spi.container.invocation.MessageCache;
import org.fabric3.spi.container.invocation.WorkContext;
import org.fabric3.spi.container.invocation.WorkContextCache;
import org.fabric3.spi.container.wire.InvocationChain;
import org.fabric3.spi.container.wire.Wire;

/**
 * Executes a set of integration tests.
 */
public class TestSet {
    private String testClassName;
    private Wire wire;
    private TestRecorder recorder;

    public TestSet(String testClassName, Wire wire, TestRecorder recorder) {
        this.testClassName = testClassName;
        this.wire = wire;
        this.recorder = recorder;
    }

    public void execute() {
        Message message = MessageCache.getAndResetMessage();
        WorkContext workContext = WorkContextCache.getAndResetThreadWorkContext();
        TestSuiteResult suiteResult = new TestSuiteResult(testClassName);
        suiteResult.start();
        for (InvocationChain chain : wire.getInvocationChains()) {
            message.setWorkContext(workContext);
            long start = System.currentTimeMillis();
            Message response = chain.getHeadInterceptor().invoke(message);
            long elapsed = System.currentTimeMillis() - start;
            TestResult result;
            if (response.isFault()) {
                result = new TestResult(testClassName, chain.getPhysicalOperation().getName(), (Throwable) response.getBody(), start, elapsed);
            } else {
                result = new TestResult(testClassName, chain.getPhysicalOperation().getName(), TestResult.Type.SUCCESS, start, elapsed);
            }
            suiteResult.add(result);
            message.reset();
            workContext.reset();
        }
        suiteResult.stop();
        recorder.result(suiteResult);
    }
}
