/*
 * P4.java
 *
 * Copyright (C) 1998-2002 Peter Graves
 * $Id: P4.java,v 1.1.1.1 2002-09-24 16:08:28 piso Exp $
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

import gnu.regexp.RE;
import gnu.regexp.REMatch;
import gnu.regexp.UncheckedRE;
import java.util.Iterator;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.undo.CompoundEdit;

public class P4 implements Constants
{
    public static void p4()
    {
        if (!checkP4Installed())
            return;
        MessageDialog.showMessageDialog(
            "The command \"p4\" requires an argument.",
            "Error");
    }

    public static void p4(String s)
    {
        if (!checkP4Installed())
            return;
        List args = Utilities.tokenize(s);
        if (args.size() == 0)
            return;
        String command = (String) args.get(0);
        if (command.equals("submit")) {
            MessageDialog.showMessageDialog("Use \"p4Submit\".",
                "Error");
            return;
        }
        if (command.equals("change")) {
            MessageDialog.showMessageDialog("\"p4 change\" is not supported.",
                "Error");
            return;
        }
        final Editor editor = Editor.currentEditor();
        editor.setWaitCursor();
        FastStringBuffer sb = new FastStringBuffer("p4 ");
        for (Iterator it = args.iterator(); it.hasNext();) {
            String arg = (String) it.next();
            if (arg.equals("%")) {
                File file = editor.getBuffer().getFile();
                if (file != null)
                    arg = file.canonicalPath();
            }
            if (arg.indexOf(' ') >= 0) {
                sb.append('"');
                sb.append(arg);
                sb.append('"');
            } else
                sb.append(arg);
            sb.append(' ');
        }
        final String cmd = sb.toString().trim();
        Runnable commandRunnable = new Runnable() {
            public void run()
            {
                final String output =
                    command(cmd, editor.getCurrentDirectory());
                Runnable completionRunnable = new Runnable() {
                    public void run()
                    {
                        p4Completed(editor, cmd, output);
                    }
                };
                SwingUtilities.invokeLater(completionRunnable);
            }
        };
        new Thread(commandRunnable).start();
    }

    private static void p4Completed(Editor editor, String cmd, String output)
    {
        if (output != null && output.length() > 0) {
            OutputBuffer buf = OutputBuffer.getOutputBuffer(output);
            buf.setTitle(cmd);
            editor.makeNext(buf);
            editor.activateInOtherWindow(buf);
        }
    }

    public static void add()
    {
        if (!checkP4Installed())
            return;
        final Editor editor = Editor.currentEditor();
        final Buffer buffer = editor.getBuffer();
        if (buffer.getFile() == null)
            return;
        editor.setWaitCursor();
        final String name = buffer.getFile().getName();
        FastStringBuffer sb = new FastStringBuffer("p4 add ");
        // Enclose filename in double quotes in case it contains embedded
        // spaces.
        sb.append('"');
        sb.append(name);
        sb.append('"');
        final String cmd = sb.toString();
        final String output = command(cmd, buffer.getCurrentDirectory());
        OutputBuffer buf = OutputBuffer.getOutputBuffer(output);
        buf.setTitle(cmd + name);
        editor.makeNext(buf);
        editor.activateInOtherWindow(buf);
        editor.setDefaultCursor();
    }

    public static void edit()
    {
        if (!checkP4Installed())
            return;
        final Editor editor = Editor.currentEditor();
        final Buffer buffer = editor.getBuffer();
        final File file = buffer.getFile();
        if (file == null)
            return;
        buffer.setBusy(true);
        editor.setWaitCursor();
        final String canonicalPath = file.canonicalPath();
        FastStringBuffer sb = new FastStringBuffer("p4 edit ");
        // Enclose filename in double quotes in case it contains an embedded
        // space.
        sb.append('"');
        sb.append(canonicalPath);
        sb.append('"');
        final String cmd = sb.toString();
        Runnable commandRunnable = new Runnable() {
            public void run()
            {
                final String output = command(cmd, null);
                Runnable completionRunnable = new Runnable() {
                    public void run()
                    {
                        editCompleted(editor, buffer, cmd, output);
                    }
                };
                SwingUtilities.invokeLater(completionRunnable);
            }
        };
        new Thread(commandRunnable).start();
    }

    private static void editCompleted(Editor editor, Buffer buffer,
        String cmd, String output)
    {
        // Don't bother with output buffer unless there's an error.
        if (output.trim().endsWith(" - opened for edit")) {
            editor.status("File opened for edit");
        } else {
            OutputBuffer buf = OutputBuffer.getOutputBuffer(output);
            buf.setTitle(cmd + buffer.getFile().getName());
            editor.makeNext(buf);
            editor.activateInOtherWindow(buf);
        }
        // Update read-only status.
        if (editor.reactivate(buffer))
            Sidebar.repaintBufferListInAllFrames();
        buffer.setBusy(false);
        EditorIterator iter = new EditorIterator();
        while (iter.hasNext()) {
            Editor ed = iter.nextEditor();
            if (ed.getBuffer() == buffer)
                ed.setDefaultCursor();
        }
    }

    public static boolean autoEdit(Editor editor)
    {
        if (editor == null)
            return false;
        final Buffer buffer = editor.getBuffer();
        if (autoEdit(buffer.getFile())) {
            editor.status("File opened for edit");
            // Update read-only status.
            if (editor.reactivate(buffer))
                Sidebar.repaintBufferListInAllFrames();
            return true;
        }
        return false;
    }

    public static boolean autoEdit(File file)
    {
        if (file == null)
            return false;
        if (file.isRemote())
            return false;
        if (!haveP4())
            return false;
        final String cmd = "p4 edit " + file.canonicalPath();
        final String output = command(cmd, null);
        return output.trim().endsWith(" - opened for edit");
    }

    public static void revert()
    {
        if (!checkP4Installed())
            return;
        final Editor editor = Editor.currentEditor();
        final Buffer buffer = editor.getBuffer();
        final File file = buffer.getFile();
        if (file == null)
            return;
        if (buffer.isModified()) {
            String prompt = "Discard changes to " + file.canonicalPath() + "?";
            if (!editor.confirm("Revert Buffer", prompt))
                return;
        }
        final String cmd = "p4 revert " + file.canonicalPath();
        String output = command(cmd, null);
        if (output.trim().endsWith(" - was edit, reverted")) {
            editor.status("File reverted");
        } else {
            OutputBuffer buf = OutputBuffer.getOutputBuffer(output);
            buf.setTitle(cmd + file.getName());
            editor.makeNext(buf);
            editor.activateInOtherWindow(buf);
        }
        editor.reload(buffer);
        // Update read-only status.
        if (editor.reactivate(buffer))
            Sidebar.repaintBufferListInAllFrames();
    }

    public static void diff()
    {
        if (!checkP4Installed())
            return;
        final Editor editor = Editor.currentEditor();
        Buffer parentBuffer = editor.getBuffer();
        if (parentBuffer instanceof CheckinBuffer)
            parentBuffer = parentBuffer.getParentBuffer();
        final File file = parentBuffer.getFile();
        if (file == null)
            return;
        final String baseCmd = "p4 diff -du ";
        final String name = file.getName();
        final String title = baseCmd + name;
        boolean save = false;
        if (parentBuffer.isModified()) {
            int response =  ConfirmDialog.showConfirmDialogWithCancelButton(editor, VC_CHECK_SAVE_PROMPT, "P4 diff");
            switch (response) {
                case RESPONSE_YES:
                    save = true;
                    break;
                case RESPONSE_NO:
                    break;
                case RESPONSE_CANCEL:
                    return;
            }
            editor.repaintNow();
        }
        editor.setWaitCursor();
        if (!save || parentBuffer.save()) {
            // Kill existing diff output buffer if any for same parent buffer.
            for (BufferIterator it = new BufferIterator(); it.hasNext();) {
                Buffer b = it.nextBuffer();
                if (b instanceof DiffOutputBuffer) {
                    if (b.getParentBuffer() == parentBuffer) {
                        editor.maybeKillBuffer(b);
                        break; // There should be one at most.
                    }
                }
            }
            final String cmd = baseCmd + file.canonicalPath();
            final String output = command(cmd, null);
            DiffOutputBuffer buf = new DiffOutputBuffer(parentBuffer, output, VC_P4);
            buf.setTitle(title);
            editor.makeNext(buf);
            editor.activateInOtherWindow(buf);
            editor.setDefaultCursor();
        }
    }

    public static void diffDir()
    {
        if (!checkP4Installed())
            return;
        final Editor editor = Editor.currentEditor();
        final Buffer buffer = editor.getBuffer();
        editor.setWaitCursor();
        final String cmd = "p4 diff -du";
        final File directory = buffer.getCurrentDirectory();
        // Kill existing diff output buffer if any for same directory.
        for (BufferIterator it = new BufferIterator(); it.hasNext();) {
            Buffer b = it.nextBuffer();
            if (b instanceof DiffOutputBuffer) {
                if (directory.equals(((DiffOutputBuffer) b).getDirectory())) {
                    editor.maybeKillBuffer(b);
                    break; // There should be one at most.
                }
            }
        }
        final String output = command(cmd, directory);
        DiffOutputBuffer buf = new DiffOutputBuffer(directory, output, VC_P4);
        if (buf != null) {
            buf.setTitle(cmd);
            editor.makeNext(buf);
            editor.activateInOtherWindow(buf);
        }
        editor.setDefaultCursor();
    }

    public static void submit()
    {
        if (!checkP4Installed())
            return;
        final Editor editor = Editor.currentEditor();
        Buffer parentBuffer = editor.getBuffer();
        if (parentBuffer instanceof DiffOutputBuffer) {
            Log.debug("parentBuffer is DiffOutputBuffer");
            parentBuffer = parentBuffer.getParentBuffer();
            Log.debug("==> parentBuffer is " + parentBuffer);
        }
        if (parentBuffer == null)
            return;
        if (parentBuffer.getFile() == null)
            return;
        final String title = "p4 submit";
        boolean save = false;
        if (parentBuffer.isModified()) {
            int response =
                ConfirmDialog.showConfirmDialogWithCancelButton(editor,
                    VC_CHECK_SAVE_PROMPT, title);
            switch (response) {
                case RESPONSE_YES:
                    save = true;
                    break;
                case RESPONSE_NO:
                    break;
                case RESPONSE_CANCEL:
                    return;
            }
            editor.repaintNow();
        }
        if (!save || parentBuffer.save()) {
            // Look for existing checkin buffer before making a new one.
            CheckinBuffer checkinBuffer = null;
            for (BufferIterator it = new BufferIterator(); it.hasNext();) {
                Buffer buf = it.nextBuffer();
                if (buf instanceof CheckinBuffer) {
                    if (buf.getParentBuffer() == parentBuffer) {
                        checkinBuffer = (CheckinBuffer) buf;
                        break;
                    }
                }
            }
            if (checkinBuffer == null) {
                checkinBuffer = new CheckinBuffer(parentBuffer, VC_P4);
                checkinBuffer.setProperty(Property.USE_TABS, true);
                checkinBuffer.setFormatter(new P4ChangelistFormatter(checkinBuffer));
                checkinBuffer.setTitle(title);
                // Default changelist.
                ShellCommand shellCommand = new ShellCommand("p4 change -o");
                shellCommand.run();
                checkinBuffer.setText(shellCommand.getOutput());
                Position dot = findStartOfComment(checkinBuffer);
                if (dot != null) {
                    Position mark = findEndOfComment(checkinBuffer, dot);
                    View view = new View();
                    view.setDot(dot);
                    view.setCaretCol(checkinBuffer.getCol(dot));
                    if (mark != null)
                        view.setMark(mark);
                    checkinBuffer.setLastView(view);
                }
            }
            editor.makeNext(checkinBuffer);
            editor.activateInOtherWindow(checkinBuffer);
        }
    }

    public static void replaceComment(final Editor editor, final String comment)
    {
        if (!(editor.getBuffer() instanceof CheckinBuffer)) {
            Debug.bug();
            return;
        }
        final CheckinBuffer buffer = (CheckinBuffer) editor.getBuffer();
        String oldComment = extractComment(buffer);
        if (oldComment.equals(comment))
            return;
        insertComment(editor, comment);
    }

    public static String extractComment(final CheckinBuffer buffer)
    {
        Position begin = findStartOfComment(buffer);
        if (begin != null) {
            Position end = findEndOfComment(buffer, begin);
            if (end != null) {
                int offset1 = buffer.getAbsoluteOffset(begin);
                int offset2 = buffer.getAbsoluteOffset(end);
                if (offset1 >= 0 && offset2 > offset1) {
                    String s = buffer.getText().substring(offset1, offset2);
                    if (!s.equals("<enter description here>"))
                        return s;
                }
            }
        }
        return "";
    }

    private static void insertComment(final Editor editor, final String comment)
    {
        final CheckinBuffer buffer = (CheckinBuffer) editor.getBuffer();
        Position dot = findStartOfComment(buffer);
        if (dot == null)
            return;
        Position mark = findEndOfComment(buffer, dot);
        if (mark == null)
            return;
        try {
            buffer.lockWrite();
        }
        catch (InterruptedException e) {
            Log.error(e);
            return;
        }
        try {
            CompoundEdit compoundEdit = editor.beginCompoundEdit();
            editor.moveDotTo(dot);
            editor.setMark(mark);
            editor.deleteRegion();
            editor.insertString(comment);
            editor.endCompoundEdit(compoundEdit);
            buffer.modified();
        }
        finally {
            buffer.unlockWrite();
        }
        final Position end = findEndOfComment(buffer, null);
        for (EditorIterator it = new EditorIterator(); it.hasNext();) {
            Editor ed = it.nextEditor();
            if (ed.getBuffer() == buffer) {
                ed.setTopLine(buffer.getFirstLine());
                ed.setDot(end.copy()); // No undo.
                ed.moveCaretToDotCol();
                ed.setUpdateFlag(REPAINT);
                ed.updateDisplay();
            }
        }
    }

    private static Position findStartOfComment(CheckinBuffer buffer)
    {
        String s = buffer.getText();
        String lookFor = "\nDescription:\n\t";
        RE re = new UncheckedRE(lookFor);
        REMatch match = re.getMatch(s);
        if (match != null)
            return buffer.getPosition(match.getStartIndex() + lookFor.length());
        return null;
    }

    private static Position findEndOfComment(CheckinBuffer buffer, Position start)
    {
        String s = buffer.getText();
        String lookFor = "\n\nFiles:\n\t";
        RE re = new UncheckedRE(lookFor);
        int offset = -1;
        if (start != null)
            offset = buffer.getAbsoluteOffset(start);
        if (offset < 0)
            offset = 0;
        REMatch match = re.getMatch(s, offset);
        if (match != null)
            return buffer.getPosition(match.getStartIndex());
        return null;
    }

    public static void finish(Editor editor, CheckinBuffer checkinBuffer)
    {
        Buffer parentBuffer = checkinBuffer.getParentBuffer();
        if (parentBuffer.getFile() != null) {
            final String P4_OUTPUT_BUFFER_TITLE = "Output from p4 submit";
            editor.getFrame().setWaitCursor();
            final String cmd = "p4 submit -i";
            final String input = checkinBuffer.getText();
            ShellCommand shellCommand = new ShellCommand(cmd, input);
            shellCommand.run();
            if (shellCommand.exitValue() != 0) {
                // Error.
                Log.error("P4.finish input = |" + input + "|");
                Log.error("P4.finish exit value = " + shellCommand.exitValue());
                OutputBuffer buf = null;
                // Re-use existing output buffer if possible.
                for (BufferIterator it = new BufferIterator(); it.hasNext();) {
                    Buffer b = it.nextBuffer();
                    if (b instanceof OutputBuffer) {
                        if (P4_OUTPUT_BUFFER_TITLE.equals(b.getTitle())) {
                            buf = (OutputBuffer) b;
                            break; // There should be one at most.
                        }
                    }
                }
                if (buf != null)
                    buf.setText(shellCommand.getOutput());
                else
                    buf = OutputBuffer.getOutputBuffer(shellCommand.getOutput());
                buf.setTitle(P4_OUTPUT_BUFFER_TITLE);
                editor.makeNext(buf);
                editor.displayInOtherWindow(buf);
            } else {
                // Success. Kill old diff and output buffers, if any: their
                // contents are no longer correct.
                for (BufferIterator it = new BufferIterator(); it.hasNext();) {
                    Buffer b = it.nextBuffer();
                    if (b instanceof DiffOutputBuffer) {
                        if (b.getParentBuffer() == parentBuffer) {
                            Debug.assertTrue(Editor.getBufferList().contains(b));
                            Log.debug("P4.finish killing diff output buffer");
                            b.kill();
                            Debug.assertFalse(Editor.getBufferList().contains(b));
                            Debug.assertTrue(editor.getBuffer() != b);
                            Editor otherEditor = editor.getOtherEditor();
                            if (otherEditor != null)
                                Debug.assertTrue(otherEditor.getBuffer() != b);
                            break; // There should be one at most.
                        }
                    }
                }
                for (BufferIterator it = new BufferIterator(); it.hasNext();) {
                    Buffer b = it.nextBuffer();
                    if (b instanceof OutputBuffer) {
                        if (P4_OUTPUT_BUFFER_TITLE.equals(b.getTitle())) {
                            editor.maybeKillBuffer(b);
                            break; // One at most.
                        }
                    }
                }
                // Read-only status of some buffers may have changed.
                editor.getFrame().reactivate();
                editor.otherWindow();
                editor.unsplitWindow();
                checkinBuffer.kill();
            }
            editor.getFrame().setDefaultCursor();
        }
    }

    public static String getStatusString(File file)
    {
        if (file != null && haveP4()) {
            FastStringBuffer sb = null;
            String output =
                command("p4 fstat ".concat(file.canonicalPath()), null);
            String HAVE_REV = "... haveRev ";
            int begin = output.indexOf(HAVE_REV);
            if (begin >= 0) {
                begin += HAVE_REV.length();
                int end = output.indexOf('\n', begin);
                if (end > begin) {
                    if (sb == null)
                        sb = new FastStringBuffer("Perforce");
                    sb.append(" revision ");
                    sb.append(output.substring(begin, end).trim());
                }
            }
            String ACTION = "... action ";
            begin = output.indexOf(ACTION);
            if (begin >= 0) {
                begin += ACTION.length();
                int end = output.indexOf('\n', begin);
                if (end > begin) {
                    if (sb == null)
                        sb = new FastStringBuffer("Perforce");
                    sb.append(" (opened for ");
                    sb.append(output.substring(begin, end).trim());
                    sb.append(')');
                }
            }
            if (sb != null)
                return sb.toString();
        }
        return null;
    }

    // Implementation.
    private static final String command(String cmd, File workingDirectory)
    {
        if (workingDirectory != null) {
            P4Command p4Command = new P4Command(cmd, workingDirectory);
            p4Command.run();
            return p4Command.getOutput();
        } else {
            ShellCommand shellCommand = new ShellCommand(cmd);
            shellCommand.run();
            return shellCommand.getOutput();
        }
    }

    private static class P4Command
    {
        final private String cmd;
        final private File workingDirectory;
        private ShellCommand shellCommand;

        public P4Command(String cmd, File workingDirectory)
        {
            this.cmd = cmd;
            this.workingDirectory = workingDirectory;
            Log.debug("cmd = |" + cmd + "|");
            Log.debug("workingDirectory = |" + workingDirectory + "|");
        }

        public void run()
        {
            FastStringBuffer sb = new FastStringBuffer();
            if (workingDirectory != null) {
                if (Platform.isPlatformUnix())
                    sb.append('\\');
                sb.append("cd ");
                if (Platform.isPlatformWindows())
                    sb.append("/d ");
                sb.append(workingDirectory.canonicalPath());
                sb.append(" && ");
            }
            sb.append(cmd);
            Log.debug("P4Command.run |" + sb.toString() + "|");
            shellCommand = new ShellCommand(sb.toString());
            shellCommand.run();
        }

        public final String getOutput()
        {
            Debug.assertTrue(shellCommand != null);
            return shellCommand.getOutput();
        }

        public final int exitValue()
        {
            Debug.assertTrue(shellCommand != null);
            return shellCommand.exitValue();
        }
    }

    private static boolean checkP4Installed()
    {
        if (haveP4())
            return true;
        MessageDialog.showMessageDialog(
            "The Perforce command-line client does not appear to be in your PATH.",
            "Error");
        return false;
    }

    private static boolean haveP4()
    {
        return Utilities.have("p4");
    }
}
