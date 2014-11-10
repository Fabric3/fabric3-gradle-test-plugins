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

/**
 * A test result.
 */
public class TestResult {
    public enum Type {
        SUCCESS, FAILED
    }

    private String testClassName;
    private String testMethodName;
    private Type type;
    private Throwable throwable;
    private long startTime;
    private long elapsedTime;

    public TestResult(String testClassName, String testMethodName, Type type, long startTime, long elapsedTime) {
        this.testClassName = testClassName;
        this.testMethodName = testMethodName;
        this.type = type;
        this.startTime = startTime;
        this.elapsedTime = elapsedTime;
    }

    public TestResult(String testClassName, String testMethodName, Throwable throwable, long startTime, long elapsedTime) {
        this.testClassName = testClassName;
        this.testMethodName = testMethodName;
        this.throwable = throwable;
        this.startTime = startTime;
        this.type = Type.FAILED;
        this.elapsedTime = elapsedTime;
    }

    public String getTestClassName() {
        return testClassName;
    }

    public String getTestMethodName() {
        return testMethodName;
    }

    public Type getType() {
        return type;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getElapsedTime() {
        return elapsedTime;
    }
}
