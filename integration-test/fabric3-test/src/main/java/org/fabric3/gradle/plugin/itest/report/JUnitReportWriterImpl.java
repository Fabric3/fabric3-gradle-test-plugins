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

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.apache.tools.ant.util.DateUtils;
import org.fabric3.gradle.plugin.api.test.TestRecorder;
import org.fabric3.gradle.plugin.api.test.TestResult;
import org.fabric3.gradle.plugin.api.test.TestSuiteResult;

/**
 *
 */
public class JUnitReportWriterImpl implements JUnitReportWriter {
    private XMLOutputFactory factory;

    public JUnitReportWriterImpl() {
        factory = XMLOutputFactory.newFactory();
    }

    public void write(TestRecorder recorder, OutputStream stream) throws ReportException {
        XMLStreamWriter writer = null;
        try {
            writer = factory.createXMLStreamWriter(stream);
            writer.writeStartDocument();
            writer.writeStartElement("testsuites");
            for (TestSuiteResult result : recorder.getResults()) {
                writer.writeStartElement("testsuite");

                int numFailed = result.getFailedTests();
                int numSuccessful = result.getSuccessfulTests();
                writer.writeAttribute("name", result.getTestClassName());
                writer.writeAttribute("failures", String.valueOf(numFailed));
                writer.writeAttribute("tests", String.valueOf(numFailed + numSuccessful));
                writer.writeAttribute("time", String.valueOf(result.getElapsedTime() / 1000.0));
                writer.writeAttribute("errors", "0");
                writer.writeAttribute("timestamp", DateUtils.format(result.getStartTime(), DateUtils.ISO8601_DATETIME_PATTERN));

                writer.writeStartElement("properties");
                writer.writeEndElement();

                writeTests(writer, result.getTestResults());

                writer.writeEndElement(); // testsuite
            }
            writer.writeEndElement(); // testsuites
            writer.writeEndDocument();
        } catch (XMLStreamException e) {
            if (writer != null) {
                try {
                    writer.close();
                } catch (XMLStreamException e1) {
                    // ignore
                }
            }
        }
    }

    private void writeTests(XMLStreamWriter writer, List<TestResult> results) throws XMLStreamException {
        for (TestResult result : results) {
            // TODO support skipped test cases
            writer.writeStartElement("testcase");
            writer.writeAttribute("name", result.getTestMethodName());
            writer.writeAttribute("time", String.valueOf(result.getElapsedTime() / 1000.0));
            if (TestResult.Type.FAILED == result.getType()) {
                writer.writeStartElement("failure");
                Throwable throwable = result.getThrowable();
                writer.writeAttribute("message", throwable.toString());
                writer.writeAttribute("type", throwable.getClass().getName());
                writer.writeCharacters(getStackTrace(throwable));
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
    }

    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter writer = new PrintWriter(sw);
        throwable.printStackTrace(writer);
        writer.close();
        return sw.toString();
    }
}
