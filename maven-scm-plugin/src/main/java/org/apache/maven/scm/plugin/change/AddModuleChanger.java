package org.apache.maven.scm.plugin.change;

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

import java.util.Stack;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.mojo.versions.change.AbstractVersionChanger;
import org.codehaus.mojo.versions.change.VersionChange;
import org.codehaus.mojo.versions.rewriting.ModifiedPomXMLEventReader;

/**
 * @author Barrie Treloar <baerrach@gmail.com>
 */
public class AddModuleChanger
    extends AbstractVersionChanger
{

    public AddModuleChanger( Model model, ModifiedPomXMLEventReader pom, Log reporter )
    {
        super( model, pom, reporter );
    }

    public void apply( VersionChange versionChange )
        throws XMLStreamException
    {
        if ( addModule( getPom(), versionChange.getArtifactId() ) )
        {
            info( "    Adding module " + versionChange.getArtifactId() );
        }
    }

    private boolean addModule( final ModifiedPomXMLEventReader pom, final String artifactId )
        throws XMLStreamException
    {
        Stack<String> stack = new Stack<String>();
        String path = "";
        final Pattern modulesRegex = Pattern.compile( "/project/modules" );
        final Pattern moduleRegex = Pattern.compile( "/project/modules/module" );
        boolean madeReplacement = false;

        pom.rewind();

        while ( pom.hasNext() )
        {
            XMLEvent event = pom.nextEvent();
            if ( event.isStartElement() )
            {
                stack.push( path );
                path = path + "/" + event.asStartElement().getName().getLocalPart();

                if ( modulesRegex.matcher( path ).matches() )
                {
                    pom.mark( 0 );
                }
                if ( moduleRegex.matcher( path ).matches() )
                {
                    pom.mark( 1 );
                    String original = pom.getBetween( 0, 1 );
                    pom.replaceBetween( 0, 1, "\n    <module>" + artifactId + "</module>" + original );
                    madeReplacement = true;
                    pom.clearMark( 0 );
                    pom.clearMark( 1 );

                    break;
                }
            }
            if ( event.isEndElement() )
            {
                path = stack.pop();
            }
        }

        return madeReplacement;
    }
}
