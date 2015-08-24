package org.apache.maven.scm.plugin.stubs;

import org.apache.maven.model.Build;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;

public class CheckoutViaArtifactCoordsProjectStub
    extends MavenProjectStub
{

    public CheckoutViaArtifactCoordsProjectStub()
    {
        Build build = new Build();
        build.setDirectory( "target" );

        setBuild( build );
    }

}
