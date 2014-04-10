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

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.fabric3.api.host.Fabric3Exception;
import org.fabric3.api.host.Names;
import org.fabric3.api.host.classloader.MaskingClassLoader;
import org.fabric3.api.host.contribution.ContributionService;
import org.fabric3.api.host.contribution.ContributionSource;
import org.fabric3.api.host.contribution.FileContributionSource;
import org.fabric3.api.host.contribution.StoreException;
import org.fabric3.api.host.domain.DeploymentException;
import org.fabric3.api.host.domain.Domain;
import org.fabric3.api.host.monitor.DestinationRouter;
import org.fabric3.api.host.runtime.HiddenPackages;
import org.fabric3.api.host.runtime.InitializationException;
import org.fabric3.gradle.plugin.api.test.IntegrationTests;
import org.fabric3.gradle.plugin.api.test.IntegrationTestsFactory;
import org.fabric3.gradle.plugin.api.test.TestRecorder;
import org.fabric3.gradle.plugin.api.test.TestResult;
import org.fabric3.gradle.plugin.api.test.TestSuiteResult;
import org.fabric3.gradle.plugin.itest.config.TestPluginConvention;
import org.fabric3.gradle.plugin.itest.deployer.GradleDeployer;
import org.fabric3.gradle.plugin.itest.report.JUnitReportWriterImpl;
import org.fabric3.gradle.plugin.itest.resolver.AetherBootstrap;
import org.fabric3.gradle.plugin.itest.resolver.ProjectDependencies;
import org.fabric3.gradle.plugin.itest.runtime.GradleRuntimeBooter;
import org.fabric3.gradle.plugin.itest.runtime.PluginDestinationRouter;
import org.fabric3.plugin.Fabric3PluginException;
import org.fabric3.plugin.api.runtime.PluginRuntime;
import org.fabric3.plugin.resolver.Resolver;
import org.fabric3.plugin.runtime.PluginBootConfiguration;
import org.fabric3.plugin.runtime.PluginConstants;
import org.fabric3.plugin.util.ClassLoaderHelper;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.StyledTextOutputFactory;

/**
 * Boots an embedded Fabric3 runtime and runs integration tests for the current project and other configured projects.
 */
public class Fabric3TestTask extends DefaultTask {
    private static final String FABRIC3_GRADLE = "org.codehaus.fabric3.gradle";
    private ProgressLoggerFactory progressLoggerFactory;
    private StyledTextOutput output;
    private JUnitReportWriterImpl reportWriter;

    @Inject
    public Fabric3TestTask(ProgressLoggerFactory progressLoggerFactory, StyledTextOutputFactory outputFactory) {
        this.progressLoggerFactory = progressLoggerFactory;
        this.output = outputFactory.create("fabric3");
        reportWriter = new JUnitReportWriterImpl();
    }

    @TaskAction
    public void fabric3Test() throws InitializationException, Fabric3PluginException {
        ProgressLogger progressLogger = progressLoggerFactory.newOperation("fabric3");

        progressLogger.setDescription("Fabric3 tests");
        progressLogger.setLoggingHeader("Fabric3 tests");
        progressLogger.started("BOOTING");
        Logger logger = getLogger();

        Project project = getProject();

        TestPluginConvention convention = (TestPluginConvention) project.getConvention().getByName(TestPluginConvention.FABRIC3_TEST_CONVENTION);
        boolean offline = project.getGradle().getStartParameter().isOffline();

        RepositorySystem system = AetherBootstrap.getRepositorySystem();
        ServiceRegistry registry = getServices();
        RepositorySystemSession session = AetherBootstrap.getRepositorySystemSession(system, registry, offline);

        List<RemoteRepository> repositories = AetherBootstrap.getRepositories(registry);

        Resolver resolver = new Resolver(system, session, repositories, convention.getRuntimeVersion());

        PluginBootConfiguration configuration = createBootConfiguration(convention, resolver, system, session);

        GradleRuntimeBooter booter = new GradleRuntimeBooter(configuration);

        PluginRuntime runtime = booter.boot();

        String environment = runtime.getHostInfo().getEnvironment();
        logger.info("Fabric3 started [Environment: " + environment + "]");

        progressLogger.progress("BOOTED");

        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();

        boolean aborted = false;

        IntegrationTests integrationTests = null;
        try {
            Thread.currentThread().setContextClassLoader(configuration.getBootClassLoader());
            // load the contributions
            deployContributions(runtime, convention, resolver);
            File buildDirectory = project.getBuildDir();
            String namespace = convention.getCompositeNamespace();
            String name = convention.getCompositeName();
            GradleDeployer deployer = new GradleDeployer(namespace, name, buildDirectory, logger);
            String errorText = convention.getErrorText();
            aborted = !deployer.deploy(runtime, errorText);
            if (aborted) {
                return;
            }
            progressLogger.progress("Running Fabric3 tests");
            IntegrationTestsFactory integrationTestsFactory = runtime.getComponent(IntegrationTestsFactory.class);
            integrationTests = integrationTestsFactory.createTests(progressLogger);
            integrationTests.execute();
            tryLatch(runtime);
        } finally {
            try {
                booter.shutdown();
                Thread.currentThread().setContextClassLoader(oldClassLoader);
            } catch (Exception e) {
                // ignore
            }
        }
        if (aborted) {
            progressLogger.completed("ABORTED");
            throw new Fabric3PluginException("Integration tests were aborted.");
        } else {
            processResults(integrationTests, progressLogger, convention.isReport());
        }
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    private void processResults(IntegrationTests integrationTests, ProgressLogger progressLogger, boolean report) throws Fabric3PluginException {
        TestRecorder recorder = integrationTests.getRecorder();
        if (report) {
            writeReport(recorder);
        }
        if (recorder.hasFailures()) {
            for (TestSuiteResult suiteResult : recorder.getResults()) {
                for (TestResult result : suiteResult.getTestResults()) {
                    if (result.getType() != TestResult.Type.FAILED) {
                        continue;
                    }
                    output.text("\n" + result.getTestClassName() + " > " + result.getTestMethodName());
                    output.withStyle(StyledTextOutput.Style.Failure).println(" FAILED");
                    output.withStyle(StyledTextOutput.Style.Normal).println("    " + result.getThrowable().getStackTrace()[0]);
                }
            }
            displaySummary(recorder);
            progressLogger.completed("FAILED");
            throw new Fabric3PluginException("There were failing integration tests.");
        } else {
            displaySummary(recorder);
            progressLogger.completed("COMPLETED");
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void writeReport(TestRecorder recorder) throws Fabric3PluginException {
        File buildDir = getProject().getBuildDir();
        File reportsDir = new File(buildDir, "reports");
        File outputDir = new File(reportsDir, "integration-tests");
        outputDir.mkdirs();

        try (FileOutputStream stream = new FileOutputStream(new File(outputDir, "tests.xml"))) {
            reportWriter.write(recorder, stream);
        } catch (IOException e) {
            throw new Fabric3PluginException(e);
        }
    }

    private void displaySummary(TestRecorder recorder) {
        int successfulTests = recorder.getSuccessfulTests();
        int failedTests = recorder.getFailedTests();
        String test = successfulTests == 1 ? "test" : "tests";
        output.println("\n" + successfulTests + " " + test + " succeeded, " + failedTests + " failed\n");
    }

    /**
     * Waits on a latch component if one is configured for the test run.
     *
     * @param runtime the runtime
     */
    private void tryLatch(PluginRuntime runtime) {
        Object latchComponent = runtime.getComponent(Object.class, PluginConstants.TEST_LATCH_SERVICE);
        if (latchComponent != null) {
            Class<?> type = latchComponent.getClass();
            try {
                Method method = type.getDeclaredMethod("await");
                getLogger().lifecycle("Waiting on Fabric3 runtime latch");
                method.invoke(latchComponent);
                getLogger().lifecycle("Fabric3 runtime latch released");
            } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | NoSuchMethodException | SecurityException e) {
                getLogger().error("Exception attempting to wait on latch service", e);
            }
        }
    }

    /**
     * Resolves and deploys configured contributions.
     *
     * @param runtime the runtime
     */
    private void deployContributions(PluginRuntime runtime, TestPluginConvention convention, Resolver resolver) throws Fabric3PluginException {
        Set<Artifact> contributions = convention.getContributions();
        ContributionService contributionService = runtime.getComponent(ContributionService.class, Names.CONTRIBUTION_SERVICE_URI);
        Domain domain = runtime.getComponent(Domain.class, Names.APPLICATION_DOMAIN_URI);

        List<ContributionSource> sources = new ArrayList<>();
        if (!contributions.isEmpty()) {
            try {
                Set<URL> resolved = resolver.resolve(contributions);
                createSource(sources, resolved);
            } catch (ArtifactResolutionException e) {
                throw new Fabric3PluginException("Error installing contributions", e);
            }
        }
        Set<URL> urlContributions = convention.getUrlContributions();
        if (!urlContributions.isEmpty()) {
            createSource(sources, urlContributions);
        }

        // deploy the archive and URL-based contributions
        try {
            List<URI> uris = contributionService.store(sources);
            contributionService.install(uris);
            domain.include(uris);
        } catch (Fabric3Exception e) {
            throw new Fabric3PluginException("Error installing contributions", e);
        }

        Set<Project> projectContributions = convention.getProjectContributions();

        if (projectContributions.isEmpty()) {
            return;
        }

        // deploy project contributions
        List<ContributionSource> projectSources = new ArrayList<>();
        for (Project project : projectContributions) {
            File[] files = new File(project.getBuildDir() + File.separator + "libs").listFiles();
            File source;
            if (files == null || files.length == 0) {
                throw new GradleException("Archive not found for contribution project: " + project.getName());
            } else if (files.length > 1) {
                // More than one archive. Check if a WAR is produced and use that as sometimes the JAR task may not be disabled in a webapp project, resulting
                // in multiple artifacts.
                int war = -1;
                for (File file : files) {
                    if (file.getName().endsWith(".war")) {
                        war++;
                        break;
                    }
                }
                if (war == -1) {
                    throw new GradleException("Contribution project has multiple library archives: " + project.getName());
                }
                source = files[war];
            } else {
                source = files[0];
            }
            try {
                URI uri = URI.create(source.getName());
                projectSources.add(new FileContributionSource(uri, source.toURI().toURL(), -1, false));
                List<URI> uris = contributionService.store(projectSources);
                domain.include(uris);
            } catch (MalformedURLException | StoreException | DeploymentException e) {
                throw new GradleException(e.getMessage(), e);
            }
        }

    }

    private void createSource(List<ContributionSource> sources, Set<URL> resolved) {
        for (URL url : resolved) {
            URI uri = URI.create(new File(url.getFile()).getName());
            ContributionSource source = new FileContributionSource(uri, url, -1, true);
            sources.add(source);
        }
    }

    /**
     * Creates the configuration to boot the Maven runtime, including resolving dependencies.
     *
     * @return the boot configuration
     */
    private PluginBootConfiguration createBootConfiguration(TestPluginConvention convention,
                                                            Resolver resolver,
                                                            RepositorySystem system,
                                                            RepositorySystemSession session) {

        Project project = getProject();

        Set<Artifact> shared = convention.getShared();
        Set<Artifact> extensions = convention.getExtensions();
        Set<Artifact> profiles = convention.getProfiles();
        try {
            Set<Artifact> hostArtifacts = resolver.resolveHostArtifacts(shared);
            Set<Artifact> runtimeArtifacts = resolver.resolveRuntimeArtifacts();

            Artifact testExtension = new DefaultArtifact(FABRIC3_GRADLE, "test-extension", "jar", convention.getRuntimeVersion());
            extensions.add(testExtension);

            List<ContributionSource> runtimeExtensions = resolver.resolveRuntimeExtensions(extensions, profiles);

            Set<Artifact> projectDependencies = ProjectDependencies.calculateProjectDependencies(project, hostArtifacts);
            Set<URL> moduleDependencies = resolver.resolveDependencies(projectDependencies);

            ClassLoader parentClassLoader = createParentClassLoader();

            ClassLoader hostClassLoader = ClassLoaderHelper.createHostClassLoader(parentClassLoader, hostArtifacts);
            ClassLoader bootClassLoader = ClassLoaderHelper.createBootClassLoader(hostClassLoader, runtimeArtifacts);

            PluginBootConfiguration configuration = new PluginBootConfiguration();
            configuration.setBootClassLoader(bootClassLoader);
            configuration.setHostClassLoader(hostClassLoader);

            DestinationRouter router = new PluginDestinationRouter(getLogger());
            configuration.setRouter(router);

            configuration.setExtensions(runtimeExtensions);
            configuration.setModuleDependencies(moduleDependencies);

            File buildDir = project.getBuildDir();
            configuration.setOutputDirectory(buildDir);
            configuration.setSystemConfig(convention.getSystemConfig());
            configuration.setRepositorySession(session);
            configuration.setRepositorySystem(system);
            configuration.setBuildDir(project.getBuildDir());
            return configuration;
        } catch (DependencyResolutionException | ArtifactResolutionException e) {
            throw new GradleException(e.getMessage(), e);
        }
    }

    private ClassLoader createParentClassLoader() {
        ClassLoader parentClassLoader = getClass().getClassLoader();
        String[] hidden = HiddenPackages.getPackages();
        if (hidden.length > 0) {
            // mask hidden JDK and system classpath packages
            parentClassLoader = new MaskingClassLoader(parentClassLoader, hidden);
        }
        return parentClassLoader;
    }

}
