/*
 * Fabric3
 * Copyright (c) 2009-2013 Metaform Systems
 *
 * Fabric3 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version, with the
 * following exception:
 *
 * Linking this software statically or dynamically with other
 * modules is making a combined work based on this software.
 * Thus, the terms and conditions of the GNU General Public
 * License cover the whole combination.
 *
 * As a special exception, the copyright holders of this software
 * give you permission to link this software with independent
 * modules to produce an executable, regardless of the license
 * terms of these independent modules, and to copy and distribute
 * the resulting executable under terms of your choice, provided
 * that you also meet, for each linked independent module, the
 * terms and conditions of the license of that module. An
 * independent module is a module which is not derived from or
 * based on this software. If you modify this software, you may
 * extend this exception to your version of the software, but
 * you are not obligated to do so. If you do not wish to do so,
 * delete this exception statement from your version.
 *
 * Fabric3 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the
 * GNU General Public License along with Fabric3.
 * If not, see <http://www.gnu.org/licenses/>.
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
