/*
 * LispShell.java
 *
 * Copyright (C) 2002 Peter Graves
 * $Id: LispShell.java,v 1.1 2002-10-13 17:11:00 piso Exp $
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

public final class LispShell extends Shell
{
    private LispShell(String shellCommand)
    {
        super(shellCommand);
    }

    private static Shell createLispShell(String shellCommand)
    {
        LispShell shell = new LispShell(shellCommand);
        shell.startProcess();
        if (shell.getProcess() == null) {
            Editor.getBufferList().remove(shell);
            String message;
            if (Utilities.haveJpty())
                message = "Unable to start process \"" + shell.shellCommand + "\"";
            else
                message = JPTY_NOT_FOUND;
            MessageDialog.showMessageDialog(message, "Error");
            return null;
        }
        shell.needsRenumbering = true;
        return shell;
    }

    protected void initializeHistory()
    {
        history = new History("lisp.history", 30);
    }

    public static void lisp()
    {
        lisp("lisp");
    }

    public static void lisp(String shellCommand)
    {
        if (!Utilities.haveJpty()) {
            MessageDialog.showMessageDialog(JPTY_NOT_FOUND, "Error");
            return;
        }
        // Look for existing LispShell buffer.
        Buffer buf = null;
        for (BufferIterator it = new BufferIterator(); it.hasNext();) {
            Buffer b = it.nextBuffer();
            if (b instanceof LispShell) {
                if (((LispShell)b).shellCommand.equals(shellCommand)) {
                    buf = b;
                    break;
                }
            }
        }
        if (buf != null) {
            Shell shell = (Shell) buf;
            if (shell.getProcess() == null)
                shell.startProcess();
        } else
            buf = createLispShell(shellCommand);
        if (buf != null) {
            final Editor editor = Editor.currentEditor();
            editor.makeNext(buf);
            editor.switchToBuffer(buf);
        }
    }
}
