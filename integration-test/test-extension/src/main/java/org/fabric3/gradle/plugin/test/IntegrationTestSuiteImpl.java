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

import java.util.HashMap;
import java.util.Map;

import org.fabric3.gradle.plugin.api.IntegrationTestSuite;
import org.fabric3.gradle.plugin.api.TestRecorder;

/**
 *
 */
public class IntegrationTestSuiteImpl implements IntegrationTestSuite {
    private TestRecorder recorder;
    private Map<String, TestSet> testSets = new HashMap<>();
    private int testSetCount = 0;
    private int testCount = 0;

    public IntegrationTestSuiteImpl(TestRecorder recorder) {
        this.recorder = recorder;
    }

    public TestRecorder getRecorder() {
        return recorder;
    }

    public void add(TestSet testSet) {
        testSets.put(testSet.getTestClassName(), testSet);
        testSetCount += 1;
        testCount += testSet.getTestCount();
    }

    public void execute() {
        for (TestSet testSet : testSets.values()) {
            execute(testSet);
        }
    }

    public void execute(String testSetName) {
//        ReporterManager reporterManager = reporterManagerFactory.createReporterManager();
        for (TestSet testSet : testSets.values()) {
            execute(testSet);
        }
    }

    public int getNumTests() {
        return testCount;
    }

    public int getNumTestSets() {
        return testSetCount;
    }

    protected void execute(TestSet testSet) {
        //        reporterManager.testSetStarting(new ReportEntry(this, testSet.getName(), "Starting"));
        testSet.execute();
        //        reporterManager.testSetCompleted(new ReportEntry(this, testSet.getName(), "Completed"));
        //        reporterManager.reset();
    }

}
