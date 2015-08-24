package org.apache.maven.scm.plugin;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.FileReader;
import java.util.Collections;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.ScmTestCase;
import org.apache.maven.scm.plugin.stubs.DefaultWagonManagerStub;
import org.apache.maven.scm.provider.ScmProviderRepositoryWithHost;
import org.apache.maven.scm.provider.svn.SvnScmTestUtils;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

/**
 * @author <a href="mailto:evenisse@apache.org">Emmanuel Venisse</a>
 *
 */
public class CheckoutMojoTest
    extends AbstractMojoTestCase
{
    File checkoutDir;

    File repository;

    protected void setUp()
        throws Exception
    {
        super.setUp();

        checkoutDir = getTestFile( "target/checkout" );

        repository = getTestFile( "target/repository" );

        FileUtils.forceDelete( checkoutDir );
    }

    public void testSkipCheckoutWhenCheckoutDirectoryExistsAndSkip()
        throws Exception
    {
        FileUtils.forceDelete( checkoutDir );
        checkoutDir.mkdirs();

        CheckoutMojo mojo = (CheckoutMojo) lookupMojo( "checkout", getTestFile(
            "src/test/resources/mojos/checkout/checkoutWhenCheckoutDirectoryExistsAndSkip.xml" ) );

        mojo.setCheckoutDirectory( checkoutDir );

        mojo.execute();

        assertEquals( 0, checkoutDir.listFiles().length );
    }

    public void testSkipCheckoutWithConnectionUrl()
        throws Exception
    {
        if ( !ScmTestCase.isSystemCmd( SvnScmTestUtils.SVNADMIN_COMMAND_LINE ) )
        {
            System.err.println( "'" + SvnScmTestUtils.SVNADMIN_COMMAND_LINE
                + "' is not a system command. Ignored " + getName() + "." );
            return;
        }

        FileUtils.forceDelete( checkoutDir );

        SvnScmTestUtils.initializeRepository( repository );

        CheckoutMojo mojo = (CheckoutMojo) lookupMojo( "checkout", getTestFile(
            "src/test/resources/mojos/checkout/checkoutWithConnectionUrl.xml" ) );
        mojo.setWorkingDirectory( new File( getBasedir() ) );

        String connectionUrl = mojo.getConnectionUrl();
        connectionUrl = StringUtils.replace( connectionUrl, "${basedir}", getBasedir() );
        connectionUrl = StringUtils.replace( connectionUrl, "\\", "/" );
        mojo.setConnectionUrl( connectionUrl );

        mojo.setCheckoutDirectory( checkoutDir );

        mojo.execute();
    }

    public void testSkipCheckoutWithoutConnectionUrl()
        throws Exception
    {
        FileUtils.forceDelete( checkoutDir );

        checkoutDir.mkdirs();
        CheckoutMojo mojo = (CheckoutMojo) lookupMojo( "checkout", getTestFile(
            "src/test/resources/mojos/checkout/checkoutWithoutConnectionUrl.xml" ) );

        try
        {
            mojo.execute();

            fail( "mojo execution must fail." );
        }
        catch ( MojoExecutionException e )
        {
            assertTrue( true );
        }
    }

    public void testUseExport()
        throws Exception
    {
        FileUtils.forceDelete( checkoutDir );

        checkoutDir.mkdirs();

        CheckoutMojo mojo = (CheckoutMojo) lookupMojo( "checkout", getTestFile(
            "src/test/resources/mojos/checkout/checkoutUsingExport.xml" ) );

        mojo.setCheckoutDirectory( checkoutDir );

        mojo.execute();

        assertTrue( checkoutDir.listFiles().length > 0  );
        assertFalse( new File( checkoutDir, ".svn" ).exists() );
    }

    public void testExcludeInclude()
        throws Exception
    {
        FileUtils.forceDelete( checkoutDir );

        checkoutDir.mkdirs();

        SvnScmTestUtils.initializeRepository( repository );

        CheckoutMojo mojo = (CheckoutMojo) lookupMojo(
                                                       "checkout",
                                                       getTestFile( "src/test/resources/mojos/checkout/checkoutWithExcludesIncludes.xml" ) );

        mojo.setCheckoutDirectory( checkoutDir );

        mojo.execute();

        assertTrue( checkoutDir.listFiles().length > 0 );
        assertTrue( new File( checkoutDir, ".svn").exists() );
        assertTrue( new File( checkoutDir, "pom.xml" ).exists() );
        assertFalse( new File( checkoutDir, "readme.txt" ).exists() );
        assertFalse( new File( checkoutDir, "src/test" ).exists() );
        assertTrue( new File( checkoutDir, "src/main/java" ).exists() );
        // olamy those files not exists anymore with svn 1.7
        //assertTrue( new File( checkoutDir, "src/main/java/.svn" ).exists() );
        //assertTrue( new File( checkoutDir, "src/main/.svn" ).exists() );
    }

    public void testEncryptedPasswordFromSettings()
        throws Exception
    {
        File pom = getTestFile( "src/test/resources/mojos/checkout/checkoutEncryptedPasswordFromSettings.xml" );
        CheckoutMojo mojo = (CheckoutMojo) lookupMojo( "checkout", pom );
        ScmProviderRepositoryWithHost repo =
            (ScmProviderRepositoryWithHost) mojo.getScmRepository().getProviderRepository();

        assertEquals( "testuser", repo.getUser() );
        assertEquals( "testpass", repo.getPassword() );
        assertEquals( "testphrase", repo.getPassphrase() );
    }

    public void testCheckoutViaArtifactCoords()
        throws Exception
    {
        File pom = getTestFile( "src/test/resources/mojos/checkout/checkoutViaArtifactCoords.xml" );
        CheckoutMojo mojo = (CheckoutMojo) lookupMojo( "checkout", pom );

        ArtifactFactory artifactFactory = (ArtifactFactory) getVariableValueFromObject( mojo, "artifactFactory" );
        ArtifactResolver artifactResolver = (ArtifactResolver) getVariableValueFromObject( mojo, "artifactResolver" );
        WagonManager wagonManager = (WagonManager) getVariableValueFromObject( artifactResolver, "wagonManager" );

        DefaultWagonManagerStub wagonManagerStub = new DefaultWagonManagerStub( wagonManager );
        setVariableValueToObject( artifactResolver, "wagonManager", wagonManagerStub );

        ArtifactRepository repository =
            new DefaultArtifactRepository( "central", "http://repo1.maven.org/maven2", new DefaultRepositoryLayout() );
        setVariableValueToObject( mojo, "remoteRepositories", Collections.singletonList( repository ) );

        Artifact artifact =
            artifactFactory.createProjectArtifact( "org.apache.maven.plugins", "maven-clean-plugin", "2.5" );
        wagonManagerStub.artifacts.put( artifact,
                                        getTestFile( "src/test/resources/repo/org/apache/maven/plugins/maven-clean-plugin/2.5/maven-clean-plugin-2.5.pom" ) );

        mojo.execute();

        assertEquals( "scm:svn:https://svn.apache.org/repos/asf/maven/plugins/tags/maven-clean-plugin-2.5",
                      mojo.getConnectionUrl() );

        File checkedOutPom = getTestFile( "target/checkout-via-artifact_coords/pom.xml" );
        assertTrue( checkedOutPom + " does not exist", checkedOutPom.exists() );
        MavenXpp3Reader mavenreader = new MavenXpp3Reader();
        FileReader reader = new FileReader( checkedOutPom );
        Model model = mavenreader.read( reader );
        MavenProject project = new MavenProject( model );
        assertEquals( "org.apache.maven.plugins", project.getGroupId() );
        assertEquals( "maven-clean-plugin", project.getArtifactId() );
        assertEquals( "2.5", project.getVersion() );
    }

}
