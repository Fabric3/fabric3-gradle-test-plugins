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

import org.gradle.logging.ProgressLogger;

/**
 * Creates {@link IntegrationTests}s that run integration tests.
 */
public interface IntegrationTestsFactory {

    /**
     * Creates an integration test suite.
     *
     * @param progressLogger the progress logger for reporting test run information
     * @return the test suite
     */
    IntegrationTests createTests(ProgressLogger progressLogger);

}
