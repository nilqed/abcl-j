/*
 * SshLoadProcess.java
 *
 * Copyright (C) 2002 Peter Graves
 * $Id: SshLoadProcess.java,v 1.1.1.1 2002-09-24 16:09:00 piso Exp $
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.armedbear.j;

import javax.swing.SwingUtilities;

public final class SshLoadProcess extends LoadProcess implements BackgroundProcess,
    Runnable, Cancellable
{
    private boolean fileIsDirectory;
    private String listing;

    public SshLoadProcess(Buffer buffer, SshFile file)
    {
        super(buffer, file);
    }

    public final String getListing()
    {
        return listing;
    }

    public final boolean fileIsDirectory()
    {
        return fileIsDirectory;
    }

    public void run()
    {
        buffer.setBackgroundProcess(this);
        _run();
        buffer.setBackgroundProcess(null);
    }

    private void _run()
    {
        Debug.assertTrue(file instanceof SshFile);
        SshSession session = SshSession.getSession((SshFile)file);
        Debug.assertTrue(session.isLocked());
        if (!session.isConnected())
            session.setOutputBuffer(buffer);
        if (!session.connect()) {
            session.dispose();
            if (errorRunnable != null) {
                errorRunnable.setMessage("Couldn't connect");
                SwingUtilities.invokeLater(errorRunnable);
            }
            return;
        }
        if (file.canonicalPath() == null || file.canonicalPath().length() == 0)
            file.setCanonicalPath(session.getLoginDirectory());
        if (file.getPassword() == null)
            file.setPassword(session.getPassword());
        if (session.isDirectory(file.canonicalPath())) {
            fileIsDirectory = true;
            listing = session.getDirectoryListing(file.canonicalPath());
            session.unlock();
        } else {
            session.unlock();
            cache = Utilities.getTempFile();
            Ssh ssh = new Ssh();
            if (!ssh.copy(file, cache)) {
                // Report error!
                if (errorRunnable != null) {
                    errorRunnable.setMessage(ssh.getErrorText());
                    SwingUtilities.invokeLater(errorRunnable);
                }
                return;
            }
            if (!cache.isFile()) {
                // Report error!
                if (errorRunnable != null) {
                    errorRunnable.setMessage("File not found");
                    SwingUtilities.invokeLater(errorRunnable);
                }
                return;
            }
        }
        session.setOutputBuffer(null);
        if (successRunnable != null)
            SwingUtilities.invokeLater(successRunnable);
    }
}
