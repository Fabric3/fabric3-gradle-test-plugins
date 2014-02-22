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
package org.fabric3.gradle.plugin.itest.impl;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.fabric3.api.host.Names;
import org.fabric3.api.host.classloader.MaskingClassLoader;
import org.fabric3.api.host.contribution.ContributionNotFoundException;
import org.fabric3.api.host.contribution.ContributionService;
import org.fabric3.api.host.contribution.ContributionSource;
import org.fabric3.api.host.contribution.InstallException;
import org.fabric3.api.host.contribution.StoreException;
import org.fabric3.api.host.domain.DeploymentException;
import org.fabric3.api.host.domain.Domain;
import org.fabric3.api.host.runtime.HiddenPackages;
import org.fabric3.api.host.runtime.InitializationException;
import org.fabric3.api.host.util.FileHelper;
import org.fabric3.gradle.plugin.api.PluginHostInfo;
import org.fabric3.gradle.plugin.api.PluginRuntime;
import org.fabric3.gradle.plugin.itest.Fabric3PluginException;
import org.fabric3.gradle.plugin.itest.aether.AetherBootstrap;
import org.fabric3.gradle.plugin.itest.resolver.ProjectDependencies;
import org.fabric3.gradle.plugin.itest.resolver.Resolver;
import org.fabric3.gradle.plugin.itest.runtime.PluginBootConfiguration;
import org.fabric3.gradle.plugin.itest.runtime.PluginConstants;
import org.fabric3.gradle.plugin.itest.runtime.PluginRuntimeBooter;
import org.fabric3.gradle.plugin.itest.util.ClassLoaderHelper;
import org.gradle.api.DefaultTask;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.service.ServiceRegistry;

/**
 * Boots an embedded Fabric3 runtime and runs integration tests for the current module and other configured modules.
 */
public class Fabric3TestTask extends DefaultTask {

    // FIXME configure properties
    private String[] hiddenPackages = HiddenPackages.getPackages();
    private String systemConfig;
    private String runtimeVersion = "2.5.0-SNAPSHOT";

    @TaskAction
    public void fabric3Test() throws InitializationException, Fabric3PluginException {
        Logger logger = getLogger();
        logger.lifecycle("Starting Fabric3");

        PluginBootConfiguration configuration = createBootConfiguration();

        Thread.currentThread().setContextClassLoader(configuration.getBootClassLoader());

        PluginRuntimeBooter booter = new PluginRuntimeBooter(configuration);

        PluginRuntime<PluginHostInfo> runtime = booter.boot();
        try {
            // load the contributions
            // TODO enable:
            //  deployContributions(runtime);
            //            TestDeployer deployer = new TestDeployer(compositeNamespace, compositeName, buildDirectory, getLog());
            //            boolean continueDeployment = deployer.deploy(runtime, errorText);
            //            if (!continueDeployment) {
            //                return;
            //            }
            //            TestRunner runner = new TestRunner(reportsDirectory, trimStackTrace, getLog());
            //            runner.executeTests(runtime);
        } finally {
            try {
                tryLatch(runtime);
                booter.shutdown();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    /**
     * Waits on a latch component if one is configured for the test run.
     *
     * @param runtime the runtime
     */
    private void tryLatch(PluginRuntime<PluginHostInfo> runtime) {
        Object latchComponent = runtime.getComponent(Object.class, PluginConstants.TEST_LATCH_SERVICE);
        if (latchComponent != null) {
            Class<?> type = latchComponent.getClass();
            try {
                Method method = type.getDeclaredMethod("await");
                getLogger().lifecycle("Waiting on Fabric3 runtime latch");
                method.invoke(latchComponent);
                getLogger().lifecycle("Fabric3 runtime latch released");
            } catch (NoSuchMethodException e) {
                getLogger().error("Found latch service " + type + " but it does not declare an await() method");
            } catch (SecurityException e) {
                getLogger().error("Security exception introspecting latch service", e);
            } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException e) {
                getLogger().error("Exception attempting to wait on latch service", e);
            }
        }
    }

    /**
     * Resolves and deploys configured contributions.
     *
     * @param runtime the runtime
     */
    private void deployContributions(PluginRuntime<PluginHostInfo> runtime) throws Fabric3PluginException {
        //        if (contributions.length <= 0) {
        //            return;
        //        }
        try {
            ContributionService contributionService = runtime.getComponent(ContributionService.class, Names.CONTRIBUTION_SERVICE_URI);
            Domain domain = runtime.getComponent(Domain.class, Names.APPLICATION_DOMAIN_URI);
            List<ContributionSource> sources = new ArrayList<>();
            //            for (Dependency contribution : contributions) {
            //                Artifact artifact = artifactHelper.resolve(contribution);
            //                URL url = artifact.getFile().toURI().toURL();
            //                URI uri = URI.create(new File(url.getFile()).getName());
            //                ContributionSource source = new FileContributionSource(uri, url, -1, true);
            //                sources.add(source);
            //            }
            List<URI> uris = contributionService.store(sources);
            contributionService.install(uris);
            domain.include(uris);
        } catch (ContributionNotFoundException | InstallException | DeploymentException | StoreException e) {
            throw new Fabric3PluginException("Error installing contributions", e);
        }
    }

    /**
     * Recursively cleans the F3 temporary directory.
     */
    private static void clearTempFiles() {
        File f3TempDir = new File(System.getProperty("java.io.tmpdir"), ".f3");
        try {
            FileHelper.deleteDirectory(f3TempDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates the configuration to boot the Maven runtime, including resolving dependencies.
     *
     * @return the boot configuration
     */
    private PluginBootConfiguration createBootConfiguration() {

        RepositorySystem system = AetherBootstrap.getRepositorySystem();

        boolean offline = getProject().getGradle().getStartParameter().isOffline();
        ServiceRegistry registry = getServices();
        RepositorySystemSession session = AetherBootstrap.getRepositorySystemSession(system, registry, offline);

        List<RemoteRepository> repositories = AetherBootstrap.getRepositories(registry);

        Resolver resolver = new Resolver(system, session, repositories, runtimeVersion);


        Set<Artifact> shared = Collections.emptySet(); // TODO FIXME add as a property
        Set<Artifact> extensions = Collections.emptySet(); // TODO FIXME add as a property
        Set<Artifact> profiles = Collections.emptySet(); // TODO FIXME add as a property
        try {
            Set<Artifact> hostArtifacts = resolver.resolveHostArtifacts(shared);
            Set<Artifact> runtimeArtifacts = resolver.resolveRuntimeArtifacts();

            List<ContributionSource> runtimeExtensions = resolver.resolveRuntimeExtensions(extensions, profiles);

            Set<Artifact> projectDependencies = ProjectDependencies.calculateProjectDependencies(getProject(), hostArtifacts);
            Set<URL> moduleDependencies = resolver.resolveDependencies(projectDependencies);

            ClassLoader parentClassLoader = createParentClassLoader();

            ClassLoader hostClassLoader = ClassLoaderHelper.createHostClassLoader(parentClassLoader, hostArtifacts);
            ClassLoader bootClassLoader = ClassLoaderHelper.createBootClassLoader(hostClassLoader, runtimeArtifacts);

            PluginBootConfiguration configuration = new PluginBootConfiguration();
            configuration.setBootClassLoader(bootClassLoader);
            configuration.setHostClassLoader(hostClassLoader);
            configuration.setLogger(getLogger());
            configuration.setExtensions(runtimeExtensions);
            configuration.setModuleDependencies(moduleDependencies);

            File buildDir = getProject().getBuildDir();
            configuration.setOutputDirectory(buildDir);
            configuration.setSystemConfig(systemConfig);
            configuration.setRepositorySession(session);
            configuration.setRepositorySystem(system);
            return configuration;
        } catch (DependencyResolutionException | ArtifactResolutionException e) {
            // FIXME  throw another exception
            throw new AssertionError(e);
        }
    }

    private ClassLoader createParentClassLoader() {
        ClassLoader parentClassLoader = getClass().getClassLoader();
        if (hiddenPackages.length > 0) {
            // mask hidden JDK and system classpath packages
            parentClassLoader = new MaskingClassLoader(parentClassLoader, hiddenPackages);
        }
        return parentClassLoader;
    }

}
