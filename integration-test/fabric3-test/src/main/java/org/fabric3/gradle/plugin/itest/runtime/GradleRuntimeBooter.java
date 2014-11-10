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
package org.fabric3.gradle.plugin.itest.runtime;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.fabric3.api.host.Names;
import org.fabric3.api.host.os.OperatingSystem;
import org.fabric3.api.host.runtime.BootstrapHelper;
import org.fabric3.plugin.api.runtime.PluginHostInfo;
import org.fabric3.plugin.runtime.AbstractPluginRuntimeBooter;
import org.fabric3.plugin.runtime.PluginBootConfiguration;
import org.fabric3.plugin.runtime.PluginConstants;
import org.fabric3.plugin.runtime.PluginHostInfoImpl;

/**
 * Boots a plugin runtime in a Gradle process.
 */
public class GradleRuntimeBooter extends AbstractPluginRuntimeBooter {
    private static final String PLUGIN_RUNTIME_IMPL = "org.fabric3.plugin.runtime.impl.PluginRuntimeImpl";

    public GradleRuntimeBooter(PluginBootConfiguration configuration) {
        super(configuration);
    }

    protected String getPluginClass() {
        return PLUGIN_RUNTIME_IMPL;
    }

    protected Map<String, String> getExportedPackages() {
        Map<String, String> exportedPackages = new HashMap<>();
        exportedPackages.put("org.fabric3.gradle.plugin.api", Names.VERSION);
        return exportedPackages;
    }

    protected PluginHostInfo createHostInfo(String environment, Set<URL> moduleDependencies, File outputDirectory, File buildDir) {
        File tempDir = new File(System.getProperty("java.io.tmpdir"), ".f3");

        URI domain = URI.create(PluginConstants.DOMAIN);
        File baseDir = new File(outputDirectory, "main");
        OperatingSystem os = BootstrapHelper.getOperatingSystem();

        File classes = new File(buildDir, "classes");
        File mainDir = new File(classes, "main");
        File testDir = new File(classes, "test");

        File resources = new File(buildDir, "resources");
        File mainResources = new File(resources, "main");
        File testResources = new File(resources, "test");
        return new PluginHostInfoImpl(domain, environment, moduleDependencies, baseDir, tempDir, buildDir, mainDir, mainResources, testDir, testResources, os);
    }

}
