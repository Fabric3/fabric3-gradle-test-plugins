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
