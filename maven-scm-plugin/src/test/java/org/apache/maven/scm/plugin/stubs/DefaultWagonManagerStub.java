package org.apache.maven.scm.plugin.stubs;

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

/**
 * @author <a href="mailto:baerrach@apache.org">Barrie Treloar</a>
 */
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.manager.DefaultWagonManager;
import org.apache.maven.artifact.manager.WagonConfigurationException;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.UnsupportedProtocolException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.repository.Repository;
import org.apache.maven.wagon.repository.RepositoryPermissions;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public class DefaultWagonManagerStub
    implements WagonManager
{
    WagonManager delegate;

    /**
     * List of files to "pretend" to be retrieved from the wagon manager
     */
    public Map<Artifact, File> artifacts = new HashMap<Artifact, File>();

    public DefaultWagonManagerStub( WagonManager delegate )
    {
        this.delegate = delegate;
    }

    public void getArtifact( Artifact artifact, ArtifactRepository repository )
        throws TransferFailedException, ResourceDoesNotExistException
    {
        File remoteFile = artifacts.get( artifact );
        if ( remoteFile == null )
        {
            throw new TransferFailedException( "Failed to setup DefaultWagonManagerStub for artifact=" + artifact );
        }

        try
        {
            FileUtils.copyFile( remoteFile, artifact.getFile() );
        }
        catch ( IOException e )
        {
            throw new TransferFailedException( "Error copying temporary file to the final destination: "
                + e.getMessage(), e );
        }

        artifact.setResolved( true );
    }

    public void getArtifact( Artifact artifact, List remoteRepositories )
                    throws TransferFailedException, ResourceDoesNotExistException
    {
        getArtifact( artifact, (ArtifactRepository) null );
    }
    
    public Wagon getWagon( String protocol )
        throws UnsupportedProtocolException
    {
        return delegate.getWagon( protocol );
    }

    public Wagon getWagon( Repository repository )
        throws UnsupportedProtocolException, WagonConfigurationException
    {
        return delegate.getWagon( repository );
    }

    public void putArtifact( File source, Artifact artifact, ArtifactRepository deploymentRepository )
        throws TransferFailedException
    {
        delegate.putArtifact( source, artifact, deploymentRepository );
    }

    public void putArtifactMetadata( File source, ArtifactMetadata artifactMetadata, ArtifactRepository repository )
        throws TransferFailedException
    {
        delegate.putArtifactMetadata( source, artifactMetadata, repository );
    }

    public void getArtifactMetadata( ArtifactMetadata metadata, ArtifactRepository remoteRepository, File destination,
                                     String checksumPolicy )
        throws TransferFailedException, ResourceDoesNotExistException
    {
        delegate.getArtifactMetadata( metadata, remoteRepository, destination, checksumPolicy );
    }

    public void getArtifactMetadataFromDeploymentRepository( ArtifactMetadata metadata,
                                                             ArtifactRepository remoteRepository, File file,
                                                             String checksumPolicyWarn )
        throws TransferFailedException, ResourceDoesNotExistException
    {
        delegate.getArtifactMetadataFromDeploymentRepository( metadata, remoteRepository, file, checksumPolicyWarn );
    }

    public void setOnline( boolean online )
    {
        delegate.setOnline( online );
    }

    public boolean isOnline()
    {
        return delegate.isOnline();
    }

    public void addProxy( String protocol, String host, int port, String username, String password, String nonProxyHosts )
    {
        delegate.addProxy( protocol, host, port, username, password, nonProxyHosts );
    }

    public void addAuthenticationInfo( String repositoryId, String username, String password, String privateKey,
                                       String passphrase )
    {
        delegate.addAuthenticationInfo( repositoryId, username, password, privateKey, passphrase );
    }

    public void addMirror( String id, String mirrorOf, String url )
    {
        delegate.addMirror( id, mirrorOf, url );
    }

    public void setDownloadMonitor( TransferListener downloadMonitor )
    {
        delegate.setDownloadMonitor( downloadMonitor );
    }

    public void addPermissionInfo( String repositoryId, String filePermissions, String directoryPermissions )
    {
        delegate.addPermissionInfo( repositoryId, filePermissions, directoryPermissions );
    }

    public ProxyInfo getProxy( String protocol )
    {
        return delegate.getProxy( protocol );
    }

    public AuthenticationInfo getAuthenticationInfo( String id )
    {
        return delegate.getAuthenticationInfo( id );
    }

    public void addConfiguration( String repositoryId, Xpp3Dom configuration )
    {
        delegate.addConfiguration( repositoryId, configuration );
    }

    public void setInteractive( boolean interactive )
    {
        delegate.setInteractive( interactive );
    }

    public void registerWagons( Collection wagons, PlexusContainer extensionContainer )
    {
        delegate.registerWagons( wagons, extensionContainer );
    }

    public void setDefaultRepositoryPermissions( RepositoryPermissions permissions )
    {
        delegate.setDefaultRepositoryPermissions( permissions );
    }

    public ArtifactRepository getMirrorRepository( ArtifactRepository repository )
    {
        return delegate.getMirrorRepository( repository );
    }

}
