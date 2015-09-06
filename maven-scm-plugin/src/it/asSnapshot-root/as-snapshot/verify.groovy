/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.apache.maven.project.MavenProject
import org.codehaus.mojo.versions.api.PomHelper

File buildLog = new File( basedir, 'build.log')
assert buildLog.exists()

File archiverDirectory = new File( basedir, "../maven-archiver")
assert archiverDirectory.exists()

File archiverPom = new File( archiverDirectory, "pom.xml" )
assert archiverPom.exists()

Model model = PomHelper.getRawModel( archiverPom )
MavenProject archiverProject = new MavenProject( model )
assert "org.apache.maven" == archiverProject.getGroupId()
assert "maven-archiver" == archiverProject.getArtifactId()
assert "2.7-SNAPSHOT" == archiverProject.getVersion()

File projectPom = new File( basedir, "pom.xml" )
assert projectPom.exists()
model = PomHelper.getRawModel( projectPom )
MavenProject project = new MavenProject( model )
Dependency mavenArchiverDependency = new Dependency()
mavenArchiverDependency.setGroupId( "org.apache.maven" )
mavenArchiverDependency.setArtifactId( "maven-archiver" )
mavenArchiverDependency.setVersion( "2.7-SNAPSHOT" )
assert null != project.getDependencies().find {
    mavenArchiverDependency.getGroupId().equals( it.getGroupId() ) &&
    mavenArchiverDependency.getArtifactId().equals( it.getArtifactId() ) &&
    mavenArchiverDependency.getVersion().equals( it.getVersion() )
}

File modulePom = new File( basedir, "../pom.xml" )
assert modulePom.exists()
model = PomHelper.getRawModel( modulePom )
MavenProject moduleProject = new MavenProject( model )
List modules = moduleProject.modules
assert 0 != modules.size()
assert "maven-archiver" == modules.get( 0 )
assert "as-snapshot" == modules.get( 1 )

