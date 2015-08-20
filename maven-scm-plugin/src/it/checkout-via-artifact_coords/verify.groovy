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

import java.io.FileReader;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;

File buildLog = new File(basedir, 'build.log')
assert buildLog.exists()

File checkoutDirectory = new File(basedir, "maven-clean-plugin")
assert checkoutDirectory.exists()

File checkedOutPom = new File( checkoutDirectory, "pom.xml" );
assert checkedOutPom.exists()

MavenXpp3Reader mavenreader = new MavenXpp3Reader();
FileReader reader = new FileReader( checkedOutPom );
Model model = mavenreader.read( reader );
MavenProject project = new MavenProject( model );
assert "org.apache.maven.plugins" == project.getGroupId()
assert "maven-clean-plugin" == project.getArtifactId()
assert "2.5" == project.getVersion()