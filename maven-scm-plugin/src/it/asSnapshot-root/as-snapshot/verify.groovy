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

File plexusArchiverDirectory = new File( basedir, "../plexus-archiver")
assert plexusArchiverDirectory.exists()
File plexusArchiverPom = new File( plexusArchiverDirectory, "pom.xml" )
assert plexusArchiverPom.exists()
Model model = PomHelper.getRawModel( plexusArchiverPom )
MavenProject plexusArchiverProject = new MavenProject( model )
assert "org.codehaus.plexus" == plexusArchiverProject.getGroupId()
assert "plexus-archiver" == plexusArchiverProject.getArtifactId()
assert "2.8.2-SNAPSHOT" == plexusArchiverProject.getVersion()

File mavenArchiverDirectory = new File( basedir, "../maven-archiver")
assert mavenArchiverDirectory.exists()
File mavenArchiverPom = new File( mavenArchiverDirectory, "pom.xml" )
assert mavenArchiverPom.exists()
model = PomHelper.getRawModel( mavenArchiverPom )
MavenProject mavenArchiverProject = new MavenProject( model )
assert "org.apache.maven" == mavenArchiverProject.getGroupId()
assert "maven-archiver" == mavenArchiverProject.getArtifactId()
assert "2.7-SNAPSHOT" == mavenArchiverProject.getVersion()
assert null != mavenArchiverProject.getDependencies().find {
    "org.codehaus.plexus" == it.getGroupId()  &&
    "plexus-archiver" == it.getArtifactId() &&
    "2.8.2-SNAPSHOT" == it.getVersion()
}

File projectPom = new File( basedir, "pom.xml" )
assert projectPom.exists()
model = PomHelper.getRawModel( projectPom )
MavenProject project = new MavenProject( model )
assert null != project.getDependencies().find {
    "org.apache.maven" == it.getGroupId() &&
    "maven-archiver" == it.getArtifactId() &&
    "2.7-SNAPSHOT" == it.getVersion()
}

File aggregatorPom = new File( basedir, "../pom.xml" )
assert aggregatorPom.exists()
model = PomHelper.getRawModel( aggregatorPom )
MavenProject aggregatorProject = new MavenProject( model )
List modules = aggregatorProject.modules
assert 3 == modules.size()
assert "plexus-archiver" == modules.get( 0 )
assert "maven-archiver" == modules.get( 1 )
assert "as-snapshot" == modules.get( 2 )

