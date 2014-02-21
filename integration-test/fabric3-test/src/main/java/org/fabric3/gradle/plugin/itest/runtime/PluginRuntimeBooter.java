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
package org.fabric3.gradle.plugin.itest.runtime;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.fabric3.api.host.Names;
import org.fabric3.api.host.contribution.ContributionSource;
import org.fabric3.api.host.os.OperatingSystem;
import org.fabric3.api.host.runtime.BootConfiguration;
import org.fabric3.api.host.runtime.BootstrapFactory;
import org.fabric3.api.host.runtime.BootstrapHelper;
import org.fabric3.api.host.runtime.BootstrapService;
import org.fabric3.api.host.runtime.InitializationException;
import org.fabric3.api.host.runtime.RuntimeConfiguration;
import org.fabric3.api.host.runtime.RuntimeCoordinator;
import org.fabric3.api.host.runtime.ShutdownException;
import org.fabric3.api.host.stream.InputStreamSource;
import org.fabric3.api.host.stream.Source;
import org.fabric3.api.host.util.FileHelper;
import org.fabric3.gradle.plugin.api.PluginHostInfo;
import org.fabric3.gradle.plugin.api.PluginRuntime;
import org.gradle.api.logging.Logger;
import org.w3c.dom.Document;

/**
 *
 */
public class PluginRuntimeBooter {
    private static final String PLUGIN_RUNTIME_IMPL = "org.fabric3.gradle.plugin.runtime.impl.PluginRuntimeImpl";

    private File outputDirectory;
    private String systemConfig;
    private ClassLoader bootClassLoader;
    private ClassLoader hostClassLoader;
    private Set<URL> moduleDependencies;
    private Logger logger;

    private RuntimeCoordinator coordinator;
    private List<ContributionSource> contributions;

    public PluginRuntimeBooter(PluginBootConfiguration configuration) {
        outputDirectory = configuration.getOutputDirectory();
        systemConfig = configuration.getSystemConfig();
        bootClassLoader = configuration.getBootClassLoader();
        hostClassLoader = configuration.getHostClassLoader();
        moduleDependencies = configuration.getModuleDependencies();
        contributions = configuration.getExtensions();
        logger = configuration.getLogger();
    }

    public PluginRuntime boot() throws InitializationException {
        BootstrapService bootstrapService = BootstrapFactory.getService(bootClassLoader);
        Document systemConfig = getSystemConfig(bootstrapService);

        PluginRuntime runtime = createRuntime(bootstrapService, systemConfig);

        Map<String, String> exportedPackages = new HashMap<>();
        exportedPackages.put("org.fabric3.test.spi", Names.VERSION);
        exportedPackages.put("org.fabric3.runtime.maven", Names.VERSION);
        exportedPackages.put("org.junit", PluginConstants.JUNIT_VERSION);

        BootConfiguration configuration = new BootConfiguration();

        configuration.setRuntime(runtime);
        configuration.setHostClassLoader(hostClassLoader);
        configuration.setBootClassLoader(bootClassLoader);

        configuration.setSystemConfig(systemConfig);
        configuration.setExtensionContributions(contributions);
        configuration.setExportedPackages(exportedPackages);

        coordinator = bootstrapService.createCoordinator(configuration);
        coordinator.start();
        String environment = runtime.getHostInfo().getEnvironment();
        logger.info("Fabric3 started [Environment: " + environment + "]");
        return runtime;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private PluginRuntime createRuntime(BootstrapService bootstrapService, Document systemConfig) throws InitializationException {
        String environment = bootstrapService.parseEnvironment(systemConfig);

        File tempDir = new File(System.getProperty("java.io.tmpdir"), ".f3");
        if (tempDir.exists()) {
            try {
                FileHelper.cleanDirectory(tempDir);
            } catch (IOException e) {
                logger.warn("Error cleaning temporary directory: " + e.getMessage());
            }
        }
        tempDir.mkdir();

        URI domain = URI.create(PluginConstants.DOMAIN);
        // FIXME directory name
        File baseDir = new File(outputDirectory, "test-classes");
        OperatingSystem os = BootstrapHelper.getOperatingSystem();

        PluginHostInfo hostInfo = new PluginHostInfoImpl(domain, environment, moduleDependencies, baseDir, tempDir, os);

        MBeanServer mBeanServer = MBeanServerFactory.createMBeanServer(PluginConstants.DOMAIN);

        PluginDestinationRouter router = new PluginDestinationRouter(logger);
        RuntimeConfiguration configuration = new RuntimeConfiguration(hostInfo, mBeanServer, router);

        return instantiateRuntime(configuration, bootClassLoader);
    }

    private Document getSystemConfig(BootstrapService bootstrapService) throws InitializationException {
        Source source = null;
        if (systemConfig != null) {
            try {
                InputStream stream = new ByteArrayInputStream(systemConfig.getBytes("UTF-8"));
                source = new InputStreamSource("systemConfig", stream);
            } catch (UnsupportedEncodingException e) {
                throw new InitializationException("Error loading system configuration", e);
            }
        }
        Document systemConfig;
        systemConfig = source == null ? bootstrapService.createDefaultSystemConfig() : bootstrapService.loadSystemConfig(source);
        return systemConfig;
    }

    public void shutdown() throws ShutdownException, InterruptedException, ExecutionException {
        coordinator.shutdown();
        logger.lifecycle("Fabric3 stopped");
    }

    private PluginRuntime instantiateRuntime(RuntimeConfiguration configuration, ClassLoader cl) {
        try {
            Class<?> implClass = cl.loadClass(PLUGIN_RUNTIME_IMPL);
            return PluginRuntime.class.cast(implClass.getConstructor(RuntimeConfiguration.class).newInstance(configuration));
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            // programming error
            throw new AssertionError(e);
        }
    }

}
