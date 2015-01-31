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
package org.fabric3.gradle.plugin.itest.impl;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.fabric3.api.host.ContainerException;
import org.fabric3.api.host.Fabric3Exception;
import org.fabric3.api.host.Names;
import org.fabric3.api.host.classloader.DelegatingResourceClassLoader;
import org.fabric3.api.host.classloader.MaskingClassLoader;
import org.fabric3.api.host.contribution.ContributionService;
import org.fabric3.api.host.contribution.ContributionSource;
import org.fabric3.api.host.contribution.FileContributionSource;
import org.fabric3.api.host.domain.Domain;
import org.fabric3.api.host.monitor.DestinationRouter;
import org.fabric3.api.host.runtime.HiddenPackages;
import org.fabric3.api.host.util.IOHelper;
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
import org.fabric3.gradle.plugin.itest.stopwatch.NoOpStopWatch;
import org.fabric3.gradle.plugin.itest.stopwatch.StopWatch;
import org.fabric3.gradle.plugin.itest.stopwatch.StreamStopWatch;
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
    private static final String FABRIC3_GRADLE = "org.fabric3.gradle";
    private ProgressLoggerFactory progressLoggerFactory;
    private StyledTextOutput output;
    private JUnitReportWriterImpl reportWriter;
    private StopWatch stopWatch;

    @Inject
    public Fabric3TestTask(ProgressLoggerFactory progressLoggerFactory, StyledTextOutputFactory outputFactory) {
        this.progressLoggerFactory = progressLoggerFactory;
        this.output = outputFactory.create("fabric3");
        reportWriter = new JUnitReportWriterImpl();
        if (Boolean.parseBoolean(System.getProperty("fabric3.performance"))) {
            stopWatch = new StreamStopWatch("gradle", TimeUnit.MILLISECONDS, System.out);
        } else {
            stopWatch = new NoOpStopWatch();
        }
    }

    @TaskAction
    public void fabric3Test() throws ContainerException, Fabric3PluginException {
        stopWatch.start();

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

        RepositoryPolicy repoPolicy = new RepositoryPolicy(convention.isRemoteRepositoryEnabled(),
                                                           convention.getUpdatePolicy(),
                                                           RepositoryPolicy.CHECKSUM_POLICY_WARN);
        RepositoryPolicy snapshotPolicy = new RepositoryPolicy(convention.isRemoteSnapshotRepositoryEnabled(),
                                                               convention.getSnapshotUpdatePolicy(),
                                                               RepositoryPolicy.CHECKSUM_POLICY_WARN);

        List<RemoteRepository> repositories = AetherBootstrap.getRepositories(registry, repoPolicy, snapshotPolicy);

        Resolver resolver = new Resolver(system, session, repositories, convention.getRuntimeVersion());

        PluginBootConfiguration configuration = createBootConfiguration(convention, resolver, system, session);

        GradleRuntimeBooter booter = new GradleRuntimeBooter(configuration);

        stopWatch.split("Gradle setup");

        PluginRuntime runtime = booter.boot();

        stopWatch.split("Fabric3 boot");

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

            stopWatch.split("Fabric3 deploy contributions");

            File buildDirectory = project.getBuildDir();
            String namespace = convention.getCompositeNamespace();
            String name = convention.getCompositeName();
            GradleDeployer deployer = new GradleDeployer(namespace, name, buildDirectory, logger);
            String errorText = convention.getErrorText();
            aborted = !deployer.deploy(runtime, errorText);
            if (aborted) {
                return;
            }

            stopWatch.split("Fabric3 deploy test composite");

            progressLogger.progress("Running Fabric3 tests");
            IntegrationTestsFactory integrationTestsFactory = runtime.getComponent(IntegrationTestsFactory.class);
            integrationTests = integrationTestsFactory.createTests(progressLogger);
            integrationTests.execute();

            stopWatch.split("Fabric3 run tests");

            tryLatch(runtime);

            stopWatch.stop();

            stopWatch.flush();
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
        Set<File> fileContributions = convention.getFileContributions();
        if (!fileContributions.isEmpty()) {
            for (File file : fileContributions) {
                URI uri = URI.create(file.getName());
                try {
                    ContributionSource source = new FileContributionSource(uri, file.toURI().toURL(), -1, true);
                    sources.add(source);
                } catch (MalformedURLException e) {
                    throw new GradleException(e.getMessage(), e);
                }
            }
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
            ContributionSource source = ProjectDependencies.createSource(project);
            projectSources.add(source);
        }
        try {
            List<URI> uris = contributionService.store(projectSources);
            contributionService.install(uris);
            domain.include(uris);
        } catch (Fabric3Exception e) {
            throw new GradleException(e.getMessage(), e);
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

        configureWeb(convention);

        try {
            Set<Artifact> shared = convention.getShared();
            Set<Project> sharedProjects = convention.getSharedProjects();
            Set<Artifact> extensions = convention.getExtensions();
            Set<Artifact> profiles = convention.getProfiles();

            Set<Artifact> hostArtifacts = resolver.resolveHostArtifacts(shared);
            Set<Artifact> runtimeArtifacts = resolver.resolveRuntimeArtifacts();

            Artifact testExtension = new DefaultArtifact(FABRIC3_GRADLE, "test-extension", "jar", convention.getRuntimeVersion());
            extensions.add(testExtension);

            List<ContributionSource> runtimeExtensions = resolver.resolveRuntimeExtensions(extensions, profiles);

            Set<URL> moduleDependencies = ProjectDependencies.calculateProjectDependencies(project, hostArtifacts, resolver);
            ClassLoader parentClassLoader = createParentClassLoader();

            URL[] sharedUrls = getSharedUrls(hostArtifacts, sharedProjects);

            ClassLoader hostClassLoader = new DelegatingResourceClassLoader(sharedUrls, parentClassLoader);
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
            if (convention.getSystemConfig() != null) {
                configuration.setSystemConfig(convention.getSystemConfig());
            } else if (convention.getSystemConfigFile() != null) {
                InputStream is = new FileInputStream(convention.getSystemConfigFile());
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                IOHelper.copy(is, os);
                configuration.setSystemConfig(new String(os.toByteArray()));
            }
            configuration.setRepositorySession(session);
            configuration.setRepositorySystem(system);
            configuration.setBuildDir(project.getBuildDir());
            return configuration;
        } catch (DependencyResolutionException | ArtifactResolutionException | IOException e) {
            throw new GradleException(e.getMessage(), e);
        }
    }

    private URL[] getSharedUrls(Set<Artifact> shared, Set<Project> sharedProjects) throws MalformedURLException {
        Set<URL> sharedUrls = new HashSet<>();
        for (Artifact artifact : shared) {
            if (artifact.getFile() == null) {
                throw new GradleException("Archive not found for shared project: " + artifact.getGroupId() + ":" + artifact.getArtifactId());
            }
            sharedUrls.add(artifact.getFile().toURI().toURL());
        }
        for (Project sharedProject : sharedProjects) {
            sharedUrls.add(ProjectDependencies.findArtifact(sharedProject).toURI().toURL());
        }
        return sharedUrls.toArray(new URL[sharedUrls.size()]);
    }

    private void configureWeb(TestPluginConvention convention) {
        Set<Artifact> extensions = convention.getExtensions();
        Set<Artifact> profiles = convention.getProfiles();
        for (Artifact extension : extensions) {
            if (extension.getArtifactId().equals("fabric3-jetty")) {
                return;
            }
        }

        for (Artifact profile : profiles) {
            String id = profile.getArtifactId();
            if (id.equals("profile-ws") || id.equals("profile-rs") || id.equals("profile-web")) {
                extensions.add(new DefaultArtifact(profile.getGroupId(), "fabric3-jetty", "jar", profile.getVersion()));
                return;
            }
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
