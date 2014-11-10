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
 * A result for a test suite.
 */
public class TestSuiteResult {
    private String testClassName;
    private long startTime;
    private long elapsedTime;

    private List<TestResult> testResults = new ArrayList<>();

    public TestSuiteResult(String testClassName) {
        this.testClassName = testClassName;
    }

    public void start() {
        startTime = System.currentTimeMillis();
    }

    public void stop() {
        elapsedTime = System.currentTimeMillis() - startTime;
    }

    public void add(TestResult result) {
        testResults.add(result);
    }

    public String getTestClassName() {
        return testClassName;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getElapsedTime() {
        return elapsedTime;
    }

    public List<TestResult> getTestResults() {
        return testResults;
    }

    public int getSuccessfulTests() {
        int success = 0;
        for (TestResult result : testResults) {
            if (TestResult.Type.SUCCESS == result.getType()) {
                success++;
            }
        }
        return success;
    }

    public int getFailedTests() {
        int failed = 0;
        for (TestResult result : testResults) {
            if (TestResult.Type.FAILED == result.getType()) {
                failed++;
            }
        }
        return failed;
    }

}
