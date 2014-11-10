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

import java.util.Map;

import org.fabric3.gradle.plugin.api.test.IntegrationTests;
import org.fabric3.gradle.plugin.api.test.IntegrationTestsFactory;
import org.fabric3.gradle.plugin.api.test.TestRecorder;
import org.fabric3.spi.container.wire.Wire;
import org.fabric3.test.spi.TestWireHolder;
import org.gradle.logging.ProgressLogger;
import org.oasisopen.sca.annotation.Reference;

/**
 *
 */
public class IntegrationTestsFactoryImpl implements IntegrationTestsFactory {
    private TestWireHolder wireHolder;

    public IntegrationTestsFactoryImpl(@Reference TestWireHolder wireHolder) {
        this.wireHolder = wireHolder;
    }

    public IntegrationTests createTests(ProgressLogger progressLogger) {
        TestRecorder recorder = new TestRecorder();
        IntegrationTestsImpl suite = new IntegrationTestsImpl(recorder);
        for (Map.Entry<String, Wire> entry : wireHolder.getWires().entrySet()) {
            TestSet testSet = new TestSet(entry.getKey(), entry.getValue(), recorder);
            suite.add(testSet);
        }
        return suite;
    }
}
