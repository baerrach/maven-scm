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
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmResult;
import org.apache.maven.scm.repository.ScmRepository;
import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.codehaus.mojo.versions.api.PomHelper;
import org.codehaus.mojo.versions.change.AbstractVersionChanger;
import org.codehaus.mojo.versions.change.DependencyVersionChanger;
import org.codehaus.mojo.versions.change.ProjectVersionChanger;
import org.codehaus.mojo.versions.change.VersionChange;
import org.codehaus.mojo.versions.rewriting.ModifiedPomXMLEventReader;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.stax2.XMLInputFactory2;

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
     * <p>
     * <b>Default value is</b>: ${project.build.directory}/checkout
     * </p>
     * <p>
     * <b>Default value (<em>when usng artifactCoords</em>) is</b>: ${artifactId}
     * </p>
     */
    @Parameter( property = "checkoutDirectory" )
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
     * Set to true to checkout the project's dependency specified by {@code artifactCoords}
     * and bump to snapshot, updating (and creating) an aggregator pom to include the dependency
     * as a module.
     *
     * @since 1.9.5
     */
    @Parameter( property = "asSnapshot", readonly = true, defaultValue = "false" )
    private boolean asSnapshot = false;

    /**
     * allow extended mojo (ie BootStrap ) to see checkout result
     */
    private ScmResult checkoutResult;

    /** {@inheritDoc} */
    public void execute()
        throws MojoExecutionException
    {
        if ( asSnapshot )
        {
            checkoutDependencyAsSnapshot();
            return;
        }

        if ( StringUtils.isNotEmpty( artifactCoords ) )
        {
            setConnectionUrlFromArtifactCoords();
        }

        if ( checkoutDirectory == null )
        {
            // set a default value. setConnectionUrlFromArtifactCoords may have already configured a default value.
            checkoutDirectory = new File( project.getBuild().getDirectory(), "checkout" );
        }

        super.execute();

        doCheckout();
    }

    private void doCheckout()
        throws MojoExecutionException
    {
        //skip checkout if checkout directory is already created. See SCM-201
        checkoutResult = null;
        if ( !getCheckoutDirectory().isDirectory() || !this.skipCheckoutIfExists )
        {
            checkoutResult = checkout();
        }
    }

    protected Artifact parseArtifactCoords( String artifactCoords )
        throws MojoExecutionException
    {
        String groupId = null;
        String artifactId = null;
        String version = null;
        String[] tokens = StringUtils.split( artifactCoords, ":" );
        if ( tokens.length < 2 || tokens.length > 5 )
        {
            // CHECKSTYLE_OFF: LineLength
            throw new MojoExecutionException( "Invalid artifact, you must specify groupId:artifactId[:version][:packaging][:classifier] "
                + artifactCoords );
            // CHECKSTYLE_ON: LineLength
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

        return artifactFactory.createProjectArtifact( groupId, artifactId, version );
    }

    /**
     * Reconfigure this mojo's <code>connectionType</code>, <code>connectionUrl</code>,
     * <code>developerConnectionUrl</code> from the artifact coords' SCM section.
     *
     * @throws MojoExecutionException failures
     */
    protected void setConnectionUrlFromArtifactCoords() throws MojoExecutionException
    {
        try
        {
            Artifact toDownload = parseArtifactCoords( artifactCoords );
            artifactResolver.resolve( toDownload, remoteRepositories, localRepository );

            if ( null == checkoutDirectory )
            {
                checkoutDirectory = new File( this.getBasedir(), toDownload.getArtifactId() );
                getLog().debug( "Reconfiguring mojo checkoutDirectory = " + checkoutDirectory );
            }

            Model model = PomHelper.getRawModel( toDownload.getFile() );
            MavenProject p = new MavenProject( model );
            Scm scm = p.getScm();
            if ( scm == null )
            {
                throw new MojoExecutionException( "Project does not contain a scm section" );
            }

            setConnectionType( "developerConnection" );
            getLog().info( "Reconfiguring mojo connectionType = developerConnection" );

            setConnectionUrl( scm.getConnection() );
            getLog().debug( "Reconfiguring mojo connectionUrl = " + scm.getConnection() );

            setDeveloperConnectionUrl( scm.getDeveloperConnection() );
            getLog().debug( "Reconfiguring mojo developerConnectionUrl = " + scm.getDeveloperConnection() );
        }
        catch ( Exception  e )
        {
            throw new MojoExecutionException( "Couldn't set connectionUrl from artifactCoords: " + e.getMessage(), e );
        }
    }

    protected File getCheckoutDirectory()
    {
        if ( this.checkoutDirectory.getPath().contains( "${project.basedir}" ) )
        {
            // project.basedir is not set under maven 3.x when run without a project
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

    /**
     * <ul>
     *   <li>Get the pom for released version
     *   <li>Checkout the released version
     *   <li>Bump the checkedout version to SNAPSHOT
     *   <li>Create (or update) the aggregator pom with the checked out dependency as a module
     * </ul>
     */
    private void checkoutDependencyAsSnapshot()
        throws MojoExecutionException
    {
        if ( project == null )
        {
            throw new MojoExecutionException( "Requires a pom.xml in the directory you are running this command from" );
        }

        if ( StringUtils.isEmpty( artifactCoords ) )
        {
            throw new MojoExecutionException( "Must specify artifactCoords to checkout dependency as snapshot."  );
        }

        try
        {
            Artifact dependency = findDependency();
            setConnectionUrlFromArtifactCoords();

            checkoutDirectory = new File( this.getBasedir(), "../" + dependency.getArtifactId() );
            getLog().debug( "Reconfiguring mojo checkoutDirectory = " + checkoutDirectory );
            doCheckout();

            // Update checkout to next snapshot version
            // CHECKSTYLE_OFF: LineLength
            String newVersion =
                            new DefaultVersionInfo( dependency.getVersion() ).getNextVersion().getSnapshotVersionString();
            // CHECKSTYLE_ON: LineLength
            VersionChange versionChange =
                            new VersionChange( dependency.getGroupId(), dependency.getArtifactId(),
                                               dependency.getVersion(), newVersion );
            File checkedOutPom = new File( checkoutDirectory, "pom.xml" );
            updateCheckoutToSnapshot( checkedOutPom, versionChange );

            // Update current pom to use new snapshot version of dependency
            File projectPom = new File( this.getBasedir(), "pom.xml" );
            updateProjectToUseNewSnapshot( projectPom, versionChange );

            // Update/Create aggregator pom for current project and checkout
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Unable to checkout dependency as snapshot", e );
        }
    }

    private void updateProjectToUseNewSnapshot( File pom, VersionChange versionChange )
        throws MojoExecutionException, IOException
    {
        ModifiedPomXMLEventReader newPom = newModifiablePom( pom );
        Model model = PomHelper.getRawModel( pom );
        MavenProject p = new MavenProject( model );

        DependencyVersionChanger changer = new DependencyVersionChanger( p.getModel(), newPom, getLog() );
        applyChangesToPom( pom, newPom, changer, versionChange );

    }

    private void updateCheckoutToSnapshot( File pom, VersionChange versionChange )
        throws MojoExecutionException, IOException
    {
        ModifiedPomXMLEventReader newPom = newModifiablePom( pom );
        Model model = PomHelper.getRawModel( pom );
        MavenProject p = new MavenProject( model );

        ProjectVersionChanger changer = new ProjectVersionChanger( p.getModel(), newPom, getLog() );
        applyChangesToPom( pom, newPom, changer, versionChange );
    }

    private ModifiedPomXMLEventReader newModifiablePom( File pom )
        throws MojoExecutionException
    {
        try
        {
            StringBuilder input = PomHelper.readXmlFile( pom );
            XMLInputFactory inputFactory = XMLInputFactory2.newInstance();
            inputFactory.setProperty( XMLInputFactory2.P_PRESERVE_LOCATION, Boolean.TRUE );
            ModifiedPomXMLEventReader newPom = new ModifiedPomXMLEventReader( input, inputFactory );
            return newPom;
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Unable to create modifiable pom", e );
        }
    }

    private void applyChangesToPom( File pom, ModifiedPomXMLEventReader newPom, AbstractVersionChanger changer,
                                    VersionChange versionChange )
        throws MojoExecutionException
    {
        try
        {
            getLog().debug( "Applying changes to pom.xml=" + pom.getCanonicalPath() );
            changer.apply( versionChange );

            Writer writer = WriterFactory.newXmlWriter( pom );
            try
            {
                IOUtil.copy( newPom.asStringBuilder().toString(), writer );
            }
            finally
            {
                IOUtil.close( writer );
            }

        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
    }

    /**
     * @return the dependency specified in artifactCoords
     * @throws MojoExecutionException if the dependency does not exist in pom
     */
    private Artifact findDependency()
        throws MojoExecutionException
    {
        Artifact toMakeSnapshot = parseArtifactCoords( artifactCoords );
        @SuppressWarnings( "unchecked" )
        Set<Artifact> dependencyArtifacts = project.getDependencyArtifacts();

        Artifact dependency = null;
        for ( Artifact artifact : dependencyArtifacts )
        {
            if ( toMakeSnapshot.getArtifactId().equals( artifact.getArtifactId() )
                && toMakeSnapshot.getGroupId().equals( artifact.getGroupId() ) )
            {
                dependency = artifact;
                break;
            }
        }

        if ( null == dependency )
        {
            throw new MojoExecutionException( "Pom does not contain dependency for " + artifactCoords );
        }

        return dependency;
    }

}
