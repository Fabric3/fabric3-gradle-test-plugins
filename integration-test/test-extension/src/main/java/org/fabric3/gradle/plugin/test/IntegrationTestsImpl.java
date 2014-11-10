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

import java.util.ArrayList;
import java.util.List;

import org.fabric3.gradle.plugin.api.test.IntegrationTests;
import org.fabric3.gradle.plugin.api.test.TestRecorder;

/**
 *
 */
public class IntegrationTestsImpl implements IntegrationTests {
    private TestRecorder recorder;
    private List<TestSet> testSets = new ArrayList<>();

    public IntegrationTestsImpl(TestRecorder recorder) {
        this.recorder = recorder;
    }

    public TestRecorder getRecorder() {
        return recorder;
    }

    public void add(TestSet testSet) {
        testSets.add(testSet);
    }

    public void execute() {
        recorder.start();
        for (TestSet testSet : testSets) {
            testSet.execute();
        }
        recorder.stop();
    }

}
