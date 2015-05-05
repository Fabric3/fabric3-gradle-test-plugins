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
package org.fabric3.gradle.plugin.itest.resolver;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.fabric3.api.host.contribution.ContributionSource;
import org.fabric3.api.host.contribution.FileContributionSource;
import org.fabric3.plugin.resolver.Resolver;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;

/**
 * Returns the set of artifacts a project depends on.
 */
public class ProjectDependencies {

    /**
     * The set of artifacts a project depends on.
     *
     * @param project  the project
     * @param filter   artifacts to filter from the result
     * @param resolver the artifact resolver
     * @return the dependencies
     * @throws GradleException if there is an error
     */
    public static Set<URL> calculateProjectDependencies(Project project, Set<Artifact> filter, Resolver resolver) {
        Set<URL> artifacts = new HashSet<>();
        for (Configuration configuration : project.getConfigurations()) {
            for (Dependency dependency : configuration.getDependencies()) {
                if (dependency instanceof ProjectDependency) {
                    Project dependencyProject = ((ProjectDependency) dependency).getDependencyProject();
                    try {
                        File artifact = findArtifact(dependencyProject);
                        artifacts.add(artifact.toURI().toURL());
                    } catch (MalformedURLException e) {
                        throw new GradleException(e.getMessage(), e);
                    }

                } else {
                    Artifact artifact = new DefaultArtifact(dependency.getGroup(), dependency.getName(), "jar", dependency.getVersion());
                    if (!filter.contains(artifact)) {
                        try {
                            Set<Artifact> list = resolver.resolveTransitively(artifact);
                            for (Artifact resolved : list) {
                                File pathElement = resolved.getFile();
                                URL url = pathElement.toURI().toURL();
                                artifacts.add(url);
                            }

                        } catch (DependencyResolutionException | MalformedURLException e) {
                            throw new GradleException(e.getMessage(), e);
                        }
                    }

                }
            }
        }
        return artifacts;
    }

    /**
     * Creates a contribution source for a project.
     *
     * @param project the project
     * @return the source
     * @throws GradleException if there is an error
     */
    public static ContributionSource createSource(Project project) throws GradleException {
        File artifact = findArtifact(project);
        try {
            URI uri = URI.create(artifact.getName());
            return new FileContributionSource(uri, artifact.toURI().toURL(), -1, false);
        } catch (MalformedURLException e) {
            throw new GradleException(e.getMessage(), e);
        }
    }

    public static File findArtifact(Project project) throws GradleException {
        File[] files = new File(project.getBuildDir() + File.separator + "libs").listFiles();
        File source;
        if (files == null || files.length == 0) {
            throw new GradleException("Archive not found for contribution project: " + project.getName());
        } else if (files.length > 1) {
            // More than one archive. Check if a WAR is produced and use that as sometimes the JAR task may not be disabled in a webapp project, resulting
            // in multiple artifacts.
            Map<String, File> sorted = new HashMap<>();
            for (File file : files) {
                String name = file.getName();
                if (name.contains("-sources") || name.contains("-javadoc")) {
                    continue;
                }
                int pos = name.lastIndexOf(".");
                if (pos <= 0) {
                    continue;
                }
                sorted.put(name.substring(pos + 1), file);
            }
            source = sorted.get("war");
            if (source == null) {
                source = sorted.get("jar");
            }
            if (source == null) {
                source = sorted.get("zip");
            }
            if (source == null) {
                throw new GradleException("No suitable library archive found for project: " + project.getName());
            }
        } else {
            source = files[0];
        }
        return source;
    }

}
