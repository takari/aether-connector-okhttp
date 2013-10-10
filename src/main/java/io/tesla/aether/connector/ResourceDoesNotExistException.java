/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package io.tesla.aether.connector;

/**
 * Simple exception when a resource doesn't exist.
 */
class ResourceDoesNotExistException
    extends Exception
{

    public ResourceDoesNotExistException( final String message )
    {
        super( message );
    }

    public ResourceDoesNotExistException( final String message, final Throwable cause )
    {
        super( message, cause );
    }
}