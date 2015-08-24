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
import java.io.IOException;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmResult;
import org.apache.maven.scm.repository.ScmRepository;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

/**
 * Get a fresh copy of the latest source from the configured scm url.
 *
 * @author <a href="evenisse@apache.org">Emmanuel Venisse</a>
 */
@Mojo( name = "checkout", requiresProject = false )
public class CheckoutMojo
    extends AbstractScmMojo
{
    @Component
    private ArtifactFactory artifactFactory;

    @Component
    private ArtifactResolver artifactResolver;

    @Parameter( defaultValue = "${localRepository}", readonly = true )
    private ArtifactRepository localRepository;

    @Parameter( defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true )
    protected List<ArtifactRepository> remoteRepositories;

    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    protected MavenProject project;

    /**
     * Use Export instead of checkout
     */
    @Parameter( property = "useExport", defaultValue = "false" )
    private boolean useExport;

    /**
     * The directory to checkout the sources to for the bootstrap and checkout goals.
     */
    @Parameter( property = "checkoutDirectory", defaultValue = "${project.build.directory}/checkout" )
    private File checkoutDirectory;

    /**
     * Skip checkout if checkoutDirectory exists.
     */
    @Parameter( property = "skipCheckoutIfExists", defaultValue = "false" )
    private boolean skipCheckoutIfExists = false;

    /**
     * The version type (branch/tag/revision) of scmVersion.
     */
    @Parameter( property = "scmVersionType" )
    private String scmVersionType;

    /**
     * The version (revision number/branch name/tag name).
     */
    @Parameter( property = "scmVersion" )
    private String scmVersion;

    /**
     * The {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>} of the artifact's SCM to checkout
     *
     * Only used for checking out via artifact coords.
     *
     * @since 1.9.5
     */
    @Parameter( property = "artifactCoords", readonly = true )
    private String artifactCoords;

    /**
     * allow extended mojo (ie BootStrap ) to see checkout result
     */
    private ScmResult checkoutResult;

    /** {@inheritDoc} */
    public void execute()
        throws MojoExecutionException
    {
        if ( StringUtils.isNotEmpty( artifactCoords ) )
        {
            setConnectionUrlFromArtifactCoords();
        }

        super.execute();

        //skip checkout if checkout directory is already created. See SCM-201
        checkoutResult = null;
        if ( !getCheckoutDirectory().isDirectory() || !this.skipCheckoutIfExists )
        {
            checkoutResult = checkout();
        }
    }

    protected void setConnectionUrlFromArtifactCoords() throws MojoExecutionException
    {
        String groupId = null;
        String artifactId = null;
        String version = null;
        String[] tokens = StringUtils.split( artifactCoords, ":" );
        if ( tokens.length < 2 || tokens.length > 5 )
        {
            throw new MojoExecutionException(
                "Invalid artifact, you must specify groupId:artifactId[:version][:packaging][:classifier] "
                    + artifactCoords );
        }
        groupId = tokens[0];
        artifactId = tokens[1];
        if ( tokens.length >= 3 )
        {
            version = tokens[2];
        }
        else
        {
            version = "LATEST";
        }

        getLog().info( "remoteRepositories= FOO!" );
        for ( int i = 0; i < remoteRepositories.size(); i++ )
        {
          getLog().info( "remoteRepositories[ " + i + "]=" + remoteRepositories.get( i ).getClass().toString() );
        }
        getLog().info( "localRepository=" + localRepository.getClass().toString() );
        Artifact toDownload = artifactFactory.createProjectArtifact( groupId, artifactId, version );
        try
        {
            artifactResolver.resolve( toDownload, remoteRepositories, localRepository );
        }
        catch ( AbstractArtifactResolutionException  e )
        {
            throw new MojoExecutionException( "Couldn't download artifact: " + e.getMessage(), e );
        }
        File pomfile = toDownload.getFile();

        Model model = null;
        FileReader reader = null;
        MavenXpp3Reader mavenreader = new MavenXpp3Reader();
        try
        {
            reader = new FileReader( pomfile );
            model = mavenreader.read( reader );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Could not load pom file=" + pomfile, e );
        }
        finally
        {
            try
            {
                reader.close();
            }
            catch ( IOException e )
            {
                getLog().warn( "Failed to close reader: " + e.getMessage(), e );
            }
        }

        MavenProject project = new MavenProject( model );
        setDeveloperConnectionUrl( project.getScm().getDeveloperConnection() );
    }

    protected File getCheckoutDirectory()
    {
        if ( this.checkoutDirectory.getPath().contains( "${project.basedir}" ) )
        {
            //project.basedir is not set under maven 3.x when run without a project
            this.checkoutDirectory = new File( this.getBasedir(), "target/checkout" );
        }
        return this.checkoutDirectory;
    }

    public void setCheckoutDirectory( File checkoutDirectory )
    {
        this.checkoutDirectory = checkoutDirectory;
    }

    protected ScmResult checkout()
        throws MojoExecutionException
    {
        try
        {
            ScmRepository repository = getScmRepository();

            this.prepareOutputDirectory( getCheckoutDirectory() );

            ScmResult result = null;

            ScmFileSet fileSet = new ScmFileSet( getCheckoutDirectory().getAbsoluteFile() );
            if ( useExport )
            {
                result = getScmManager().export( repository, fileSet, getScmVersion( scmVersionType, scmVersion ) );
            }
            else
            {
                result = getScmManager().checkOut( repository, fileSet, getScmVersion( scmVersionType, scmVersion ) );
            }

            checkResult( result );

            handleExcludesIncludesAfterCheckoutAndExport( this.checkoutDirectory );

            return result;
        }
        catch ( ScmException e )
        {
            throw new MojoExecutionException( "Cannot run checkout command : ", e );
        }
    }

    private void prepareOutputDirectory( File ouputDirectory )
        throws MojoExecutionException
    {
        try
        {
            this.getLog().info( "Removing " + ouputDirectory );

            FileUtils.deleteDirectory( getCheckoutDirectory() );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Cannot remove " + ouputDirectory );
        }

        if ( !getCheckoutDirectory().mkdirs() )
        {
            throw new MojoExecutionException( "Cannot create " + ouputDirectory );
        }
    }

    protected ScmResult getCheckoutResult()
    {
        return checkoutResult;
    }


}
