/* ====================================================================
 *
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2002, 2003 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "The Jakarta Project", "Commons", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.commons.vfs.provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.cert.Certificate;
import java.util.ArrayList;
import org.apache.commons.vfs.FileContent;
import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.util.MonitorInputStream;
import org.apache.commons.vfs.util.MonitorOutputStream;

/**
 * The content of a file.
 *
 * @author <a href="mailto:adammurdoch@apache.org">Adam Murdoch</a>
 * @version $Revision: 1.7 $ $Date: 2002/07/05 04:08:17 $
 */
public final class DefaultFileContent
    implements FileContent
{
    private static final int STATE_NONE = 0;
    private static final int STATE_READING = 1;
    private static final int STATE_WRITING = 2;

    private final AbstractFileObject file;
    private int state = STATE_NONE;
    private final ArrayList instrs = new ArrayList();
    private FileContentOutputStream outstr;

    public DefaultFileContent( final AbstractFileObject file )
    {
        this.file = file;
    }

    /**
     * Returns the file that this is the content of.
     */
    public FileObject getFile()
    {
        return file;
    }

    /**
     * Returns the size of the content (in bytes).
     */
    public long getSize() throws FileSystemException
    {
        // Do some checking
        if ( !file.getType().hasContent() )
        {
            throw new FileSystemException( "vfs.provider/get-size-not-file.error", file );
        }
        if ( state == STATE_WRITING )
        {
            throw new FileSystemException( "vfs.provider/get-size-write.error", file );
        }

        try
        {
            // Get the size
            return file.doGetContentSize();
        }
        catch ( Exception exc )
        {
            throw new FileSystemException( "vfs.provider/get-size.error", new Object[]{file}, exc );
        }
    }

    /**
     * Returns the last-modified timestamp.
     */
    public long getLastModifiedTime() throws FileSystemException
    {
        if ( !file.getType().hasAttributes() )
        {
            throw new FileSystemException( "vfs.provider/get-last-modified-no-exist.error", file );
        }
        try
        {
            return file.doGetLastModifiedTime();
        }
        catch ( final Exception e )
        {
            throw new FileSystemException( "vfs.provider/get-last-modified.error", file, e );
        }
    }

    /**
     * Sets the last-modified timestamp.
     */
    public void setLastModifiedTime( long modTime ) throws FileSystemException
    {
        if ( !file.getType().hasAttributes() )
        {
            throw new FileSystemException( "vfs.provider/set-last-modified-no-exist.error", file );
        }
        try
        {
            file.doSetLastModifiedTime( modTime );
        }
        catch ( final Exception e )
        {
            throw new FileSystemException( "vfs.provider/set-last-modified.error", file, e );
        }
    }

    /**
     * Gets the value of an attribute.
     */
    public Object getAttribute( final String attrName )
        throws FileSystemException
    {
        try
        {
            return file.doGetAttribute( attrName );
        }
        catch ( final Exception e )
        {
            throw new FileSystemException( "vfs.provider/get-attribute.error", new Object[]{attrName, file}, e );
        }
    }

    /**
     * Sets the value of an attribute.
     */
    public void setAttribute( final String attrName, final Object value )
        throws FileSystemException
    {
        try
        {
            file.doSetAttribute( attrName, value );
        }
        catch ( final Exception e )
        {
            throw new FileSystemException( "vfs.provider/set-attribute.error", new Object[]{attrName, file}, e );
        }
    }

    /**
     * Returns the certificates used to sign this file.
     */
    public Certificate[] getCertificates() throws FileSystemException
    {
        if ( !file.exists() )
        {
            throw new FileSystemException( "vfs.provider/get-certificates-no-exist.error", file );
        }

        try
        {
            return file.doGetCertificates();
        }
        catch ( final Exception e )
        {
            throw new FileSystemException( "vfs.provider/get-certificates.error", file, e );
        }
    }

    /**
     * Returns an input stream for reading the content.
     */
    public InputStream getInputStream() throws FileSystemException
    {
        if ( state == STATE_WRITING )
        {
            throw new FileSystemException( "vfs.provider/read-in-use.error", file );
        }

        // Get the raw input stream
        final InputStream instr = file.getInputStream();
        final InputStream wrappedInstr = new FileContentInputStream( instr );
        this.instrs.add( wrappedInstr );
        state = STATE_READING;
        return wrappedInstr;
    }

    /**
     * Returns an output stream for writing the content.
     */
    public OutputStream getOutputStream() throws FileSystemException
    {
        if ( state != STATE_NONE )
        {
            throw new FileSystemException( "vfs.provider/write-in-use.error", file );
        }

        // Get the raw output stream
        final OutputStream outstr = file.getOutputStream();

        // Create wrapper
        this.outstr = new FileContentOutputStream( outstr );
        state = STATE_WRITING;
        return this.outstr;
    }

    /**
     * Closes all resources used by the content, including all streams, readers
     * and writers.
     */
    public void close() throws FileSystemException
    {
        try
        {
            // Close the input stream
            while ( instrs.size() > 0 )
            {
                final FileContentInputStream instr = (FileContentInputStream)instrs.remove( 0 );
                instr.close();
            }

            // Close the output stream
            if ( outstr != null )
            {
                outstr.close();
            }
        }
        finally
        {
            state = STATE_NONE;
        }
    }

    /**
     * Handles the end of input stream.
     */
    private void endInput( final FileContentInputStream instr )
    {
        instrs.remove( instr );
        if ( instrs.size() == 0 )
        {
            state = STATE_NONE;
        }
    }

    /**
     * Handles the end of output stream.
     */
    private void endOutput() throws Exception
    {
        outstr = null;
        state = STATE_NONE;
        file.endOutput();
    }

    /**
     * An input stream for reading content.  Provides buffering, and
     * end-of-stream monitoring.
     */
    private final class FileContentInputStream
        extends MonitorInputStream
    {
        FileContentInputStream( final InputStream instr )
        {
            super( instr );
        }

        /**
         * Closes this input stream.
         */
        public void close() throws FileSystemException
        {
            try
            {
                super.close();
            }
            catch ( final IOException e )
            {
                throw new FileSystemException( "vfs.provider/close-instr.error", file, e );
            }
        }

        /**
         * Called after the stream has been closed.
         */
        protected void onClose() throws IOException
        {
            endInput( this );
        }
    }

    /**
     * An output stream for writing content.
     */
    private final class FileContentOutputStream
        extends MonitorOutputStream
    {
        FileContentOutputStream( final OutputStream outstr )
        {
            super( outstr );
        }

        /**
         * Closes this output stream.
         */
        public void close() throws FileSystemException
        {
            try
            {
                super.close();
            }
            catch ( final FileSystemException e )
            {
                throw e;
            }
            catch ( final IOException e )
            {
                throw new FileSystemException( "vfs.provider/close-outstr.error", file, e );
            }
        }

        /**
         * Called after this stream is closed.
         */
        protected void onClose() throws IOException
        {
            try
            {
                endOutput();
            }
            catch ( final Exception e )
            {
                throw new FileSystemException( "vfs.provider/close-outstr.error", file, e );
            }
        }
    }

}
