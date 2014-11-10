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
package org.fabric3.gradle.plugin.itest.report;

import java.io.OutputStream;

import org.fabric3.gradle.plugin.api.test.TestRecorder;

/**
 * Writes a JUnit report to a stream.
 */
public interface JUnitReportWriter {

    /**
     * Writes a JUnit report based on the recorder data to a stream.
     *
     * @param recorder the recorder
     * @param stream   the stream
     * @throws ReportException if there is an error
     */
    void write(TestRecorder recorder, OutputStream stream) throws ReportException;

}
