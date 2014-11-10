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
package org.fabric3.gradle.plugin.api.test;

import java.util.ArrayList;
import java.util.List;

/**
 * Records test results.
 */
public class TestRecorder {
    private List<TestSuiteResult> results = new ArrayList<>();
    private long startTime;
    private long elapsedTime = -1;

    public void result(TestSuiteResult result) {
        results.add(result);
    }

    public boolean hasFailures() {
        for (TestSuiteResult result : results) {
            for (TestResult testResult : result.getTestResults()) {
                if (testResult.getType() == TestResult.Type.FAILED) {
                    return true;
                }
            }
        }
        return false;
    }

    public void start() {
        startTime = System.currentTimeMillis();
    }

    public void stop() {
        elapsedTime = System.currentTimeMillis() - startTime;
    }

    public List<TestSuiteResult> getResults() {
        return results;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getElapsedTime() {
        return elapsedTime;
    }

    public int getSuccessfulTests() {
        int success = 0;
        for (TestSuiteResult result : results) {
            success = success + result.getSuccessfulTests();
        }
        return success;
    }

    public int getFailedTests() {
        int failed = 0;
        for (TestSuiteResult result : results) {
            failed = failed + result.getFailedTests();
        }
        return failed;
    }
}
