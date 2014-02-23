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
package org.fabric3.gradle.plugin.itest.config;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * Defines configuration conventions for the Fabric3 test plugin.
 */
public class TestPluginConvention {
    public static final String FABRIC3_TEST_CONVENTION = "fabric3Test";

    private String systemConfig;

    private String runtimeVersion = "2.5.0-SNAPSHOT";
    private String compositeNamespace = "urn:fabric3.org";
    private String compositeName = "TestComposite";
    private String errorText;

    private Set<Artifact> extensions = new HashSet<>();
    private Set<Artifact> profiles = new HashSet<>();
    private Set<Artifact> exclusions = new HashSet<>();
    private Set<Artifact> shared = new HashSet<>();

    public String getRuntimeVersion() {
        return runtimeVersion;
    }

    public void setRuntimeVersion(String runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    public String getCompositeNamespace() {
        return compositeNamespace;
    }

    public void setCompositeNamespace(String compositeNamespace) {
        this.compositeNamespace = compositeNamespace;
    }

    public String getCompositeName() {
        return compositeName;
    }

    public void setCompositeName(String compositeName) {
        this.compositeName = compositeName;
    }

    public String getErrorText() {
        return errorText;
    }

    public void setErrorText(String errorText) {
        this.errorText = errorText;
    }

    public void extension(Map<String, String> extension) {
        extensions.add(convert(extension));
    }

    public void extension(String extension) {
        extensions.add(new DefaultArtifact(extension));
    }

    public void exclude(Map<String, String> exclusion) {
        exclusions.add(convert(exclusion));
    }

    public void exclude(String exclusion) {
        exclusions.add(new DefaultArtifact(exclusion));
    }

    public void profile(Map<String, String> profile) {
        profiles.add(convert(profile));
    }

    public void profile(String profile) {
        profiles.add(new DefaultArtifact(profile));
    }

    public void shared(Map<String, String> artifact) {
        shared.add(convert(artifact));
    }

    public void shared(String artifact) {
        shared.add(new DefaultArtifact(artifact));
    }

    public Set<Artifact> getExtensions() {
        return extensions;
    }

    public Set<Artifact> getProfiles() {
        return profiles;
    }

    public Set<Artifact> getExclusions() {
        return exclusions;
    }

    public Set<Artifact> getShared() {
        return shared;
    }

    private Artifact convert(Map<String, String> extension) {
        String group = extension.get("group");
        if (group == null) {
            throw new IllegalArgumentException("A group must be specified on an Fabric3 artifact definition");
        }
        String name = extension.get("name");
        if (name == null) {
            throw new IllegalArgumentException("A name must be specified on an Fabric3 artifact definition");
        }
        String version = extension.get("version");
        if (version == null) {
            throw new IllegalArgumentException("A version must be specified on an Fabric3 artifact definition");
        }
        return new DefaultArtifact(group, name, "jar", version);
    }

}