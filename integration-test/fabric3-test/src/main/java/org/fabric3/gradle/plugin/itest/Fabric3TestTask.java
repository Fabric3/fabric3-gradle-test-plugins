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
package org.fabric3.gradle.plugin.itest;

import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.fabric3.gradle.plugin.itest.aether.AetherBootstrap;
import org.gradle.api.DefaultTask;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.service.ServiceRegistry;

/**
 * Boots an embedded Fabric3 runtime and runs integration tests for the current module and other configured modules.
 */
public class Fabric3TestTask extends DefaultTask {

    @TaskAction
    public void fabric3Test() {
        getLogger().log(LogLevel.INFO, "Starting Fabric3");

        ServiceRegistry registry = getServices();
        RepositorySystem system = AetherBootstrap.getRepositorySystem();
        RepositorySystemSession session = AetherBootstrap.getRepositorySystemSession(system, registry);

        DefaultArtifact artifact = new DefaultArtifact("org.codehaus.fabric3", "fabric3-api", "jar", "1.9.5");


        List<RemoteRepository> repositories = AetherBootstrap.getRepositories(registry);

        try {
            ArtifactRequest request = new ArtifactRequest(artifact, repositories, null);
            ArtifactResult result = system.resolveArtifact(session, request);
            System.out.println(":::::::::" + result.getArtifact().getFile());
        } catch (ArtifactResolutionException e) {
            e.printStackTrace();
        }

    }

}
