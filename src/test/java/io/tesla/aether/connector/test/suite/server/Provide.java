package io.tesla.aether.connector.test.suite.server;

/*
 * Copyright (c) 2010-2011 Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0, 
 * and you may not use this file except in compliance with the Apache License Version 2.0. 
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the Apache License Version 2.0 is distributed on an 
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.util.log.Log;

/**
 * @author Benjamin Hanzelmann
 *
 */
public class Provide
    implements Behaviour
{
    private static final Pattern RANGE = Pattern.compile("bytes=([0-9]+)-");

    private final Map<String, byte[]> db = new ConcurrentHashMap<String, byte[]>();

    private int latency = -1;

    public void addPath( String path, byte[] content )
    {
        this.db.put( path, content );
    }

    public boolean execute( HttpServletRequest request, HttpServletResponse response, Map<Object, Object> ctx )
        throws Exception
    {
        String path = request.getPathInfo().substring( 1 );
        Log.getLog().debug( request.getMethod() + " " + path );

        if ( "GET".equals( request.getMethod() ) )
        {
            byte[] ba = db.get( path );
            if ( ba == null )
            {
                ba = new byte[0];
            }

            int lowerBound = 0;

            String range = request.getHeader("Range");
            if (range != null && range.matches(RANGE.pattern())) {
              Matcher m = RANGE.matcher(range);
              m.matches();
              lowerBound = Integer.parseInt(m.group(1));
            }
            
            //
            // We need to response correctly. Something like the following:
            //
            // 206 Partial Content
            // Content-Type: video/mp4
            // Content-Length: 64656927
            // Accept-Ranges: bytes
            // Content-Range: bytes 100-64656926/64656927
            //                        
            response.setStatus((lowerBound > 0) ? HttpURLConnection.HTTP_PARTIAL : HttpURLConnection.HTTP_OK);
            response.setHeader("Accept-Ranges", "bytes");
            response.setContentType( "application/octet-stream" );
            int length = ba.length - lowerBound;            
            // 
            // content:
            // 0123456789
            //
            // client sends:
            // 01234
            // client fails
            //
            // client resumes and sends:
            // 56789
            //
            // Range: lowerBound-
            // 
            // length = content - lowerBound
            //
            response.setContentLength( length );

            ServletOutputStream out = response.getOutputStream();
            for ( int i = lowerBound; i < ba.length; i++ )
            {
                out.write( ba[i] );
                out.flush();
                if ( latency != -1 )
                {
                    Thread.sleep( latency );
                }
            }
            out.close();
            return false;
        }

        return true;
    }

    public void setLatency( int i )
    {
        this.latency = i;

    }

}
