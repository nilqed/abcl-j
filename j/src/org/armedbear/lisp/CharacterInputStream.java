/*
 * CharacterInputStream.java
 *
 * Copyright (C) 2003-2004 Peter Graves
 * $Id: CharacterInputStream.java,v 1.68 2004-01-20 00:58:03 piso Exp $
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

package org.armedbear.lisp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.StringReader;
import java.math.BigInteger;

public class CharacterInputStream extends LispInputStream
{
    private final PushbackReader reader;

    protected int offset;
    protected int lineNumber;

    protected CharacterInputStream()
    {
        reader = null;
    }

    public CharacterInputStream(InputStream in)
    {
        reader = new PushbackReader(new BufferedReader(new InputStreamReader(in)),
                                    2);
    }

    public CharacterInputStream(InputStream in, boolean interactive)
    {
        this(in);
        setInteractive(interactive);
    }

    public CharacterInputStream(InputStream in, LispObject pathname)
    {
        this(in);
        setPathname(pathname);
    }

    public LispObject getElementType()
    {
        return Symbol.CHARACTER;
    }

    public int getOffset()
    {
        return offset;
    }

    public int getLineNumber()
    {
        return lineNumber;
    }

    public LispObject read(boolean eofError, LispObject eofValue,
                           boolean recursive)
        throws ConditionThrowable
    {
        try {
            LispObject result = readPreservingWhitespace(eofError, eofValue,
                                                         recursive);
            if (result != eofValue && !recursive) {
                if (ready()) {
                    int n = read();
                    if (n >= 0) {
                        char c = (char) n;
                        if (!Character.isWhitespace(c))
                            unread(c);
                    }
                }
            }
            if (_READ_SUPPRESS_.symbolValueNoThrow() != NIL)
                return NIL;
            else
                return result;
        }
        catch (IOException e) {
            return signal(new StreamError(e));
        }
    }

    public LispObject readPreservingWhitespace(boolean eofError,
                                               LispObject eofValue,
                                               boolean recursive)
        throws ConditionThrowable
    {
        while (true) {
            int n = read();
            if (n < 0) {
                if (eofError)
                    return signal(new EndOfFile());
                else
                    return eofValue;
            }
            char c = (char) n;
            if (Character.isWhitespace(c))
                continue;
            LispObject result = processChar(c);
            if (result != null)
                return result;
        }
    }

    private LispObject processChar(char c) throws ConditionThrowable
    {
        switch (c) {
            case '"':
                return readString();
            case '\'':
                return readQuote();
            case '(':
                return readList();
            case ')':
                return readRightParen();
            case ';':
                return readComment();
            case ',':
                return readComma();
            case '`':
                return readBackquote();
            case '#':
                return readSharp();
            case '|':
                return getCurrentPackage().intern(readMultipleEscape());
            case ':':
                return readKeyword();
            default:
                return readToken(c);
        }
    }

    private String _readString() throws ConditionThrowable
    {
        StringBuffer sb = new StringBuffer();
        while (true) {
            int n = read();
            if (n < 0) {
                signal(new EndOfFile());
                // Not reached.
                return null;
            }
            char c = (char) n;
            if (c == '\\') {
                // Single escape.
                n = read();
                if (n < 0) {
                    signal(new EndOfFile());
                    // Not reached.
                    return null;
                }
                sb.append((char)n);
                continue;
            }
            if (c == '"')
                break;
            // Default.
            sb.append(c);
        }
        return sb.toString();
    }

    private LispString readString() throws ConditionThrowable
    {
        return new LispString(_readString());
    }

    private LispObject readPathname() throws ConditionThrowable
    {
        LispObject obj = read(true, NIL, false);
        if (obj instanceof LispString)
            return new Pathname(((LispString)obj).getValue());
        if (obj.listp())
            return Pathname.makePathname(obj);
        return signal(new TypeError("#p requires a string or list argument."));
    }

    private LispObject readQuote() throws ConditionThrowable
    {
        return new Cons(Symbol.QUOTE, new Cons(read(true, NIL, true)));
    }

    private LispObject readList() throws ConditionThrowable
    {
        try {
            Cons first = null;
            Cons last = null;
            while (true) {
                char c = flushWhitespace();
                if (c == ')') {
                    return first == null ? NIL : first;
                }
                if (c == '.') {
                    int n = read();
                    if (n < 0)
                        return signal(new EndOfFile());
                    char nextChar = (char) n;
                    if (nextChar == ',') {
                        LispObject obj = readComma();
                        last.setCdr(obj);
                        continue;
                    } else if (isTokenDelimiter(nextChar)) {
                        if (last == null)
                            return signal(new LispError("nothing appears before . in list"));
                        LispObject obj = read(true, NIL, true);
                        last.setCdr(obj);
                        continue;
                    } else {
                        // normal token beginning with '.'
                        unread(nextChar);
                    }
                }
                LispObject obj = processChar(c);
                if (obj == null) {
                    // A comment.
                    continue;
                }
                if (first == null) {
                    first = new Cons(obj);
                    last = first;
                } else {
                    Cons newCons = new Cons(obj);
                    last.setCdr(newCons);
                    last = newCons;
                }
            }
        }
        catch (IOException e) {
            return signal(new StreamError(e));
        }
    }

    private boolean isTokenDelimiter(char c)
    {
        switch (c) {
            case '"':
            case '\'':
            case '(':
            case ')':
            case ',':
            case ';':
            case '`':
                return true;
            default:
                return Character.isWhitespace(c);
        }
    }

    private LispObject readRightParen() throws ConditionThrowable
    {
        return signal(new LispError("unmatched right parenthesis"));
    }

    private LispObject readComment() throws ConditionThrowable
    {
        while (true) {
            int n = read();
            if (n < 0)
                return null;
            if (n == '\n')
                return null;
        }
    }

    private LispObject readComma() throws ConditionThrowable
    {
        try {
            int n = read();
            if (n < 0)
                return signal(new EndOfFile());
            char c = (char) n;
            switch (c) {
                case '@':
                    return new Cons(Symbol.COMMA_ATSIGN,
                        new Cons(read(true, NIL, true), NIL));
                case '.':
                    return new Cons(Symbol.COMMA_DOT,
                        new Cons(read(true, NIL, true), NIL));
                default:
                    unread(c);
                    return new Cons(Symbol.COMMA,
                        new Cons(read(true, NIL, true), NIL));
            }
        }
        catch (IOException e) {
            return signal(new StreamError(e));
        }
    }

    private LispObject readBackquote() throws ConditionThrowable
    {
        return new Cons(Symbol.BACKQUOTE, new Cons(read(true, NIL, true)));
    }

    private LispObject readSharp() throws ConditionThrowable
    {
        int numArg = -1;
        char c;
        while (true) {
            int n = read();
            if (n < 0)
                return signal(new EndOfFile());
            c = (char) n;
            if (c < '0' || c > '9')
                break;
            if (numArg < 0)
                numArg = 0;
            numArg = numArg * 10 + c - '0';
        }
        LispObject fun =
            getCurrentReadtable().getDispatchMacroCharacter('#', c);
        if (fun != NIL) {
            LispObject[] args = new LispObject[3];
            final LispThread thread = LispThread.currentThread();
            LispObject result = funcall3(fun,
                                         this,
                                         LispCharacter.getInstance(c),
                                         (numArg < 0) ? NIL : new Fixnum(numArg),
                                         thread);
            LispObject[] values = thread.getValues();
            if (values != null && values.length == 0)
                result = null;
            thread.clearValues();
            return result;
        }
        switch (c) {
            case '\'':
                return new Cons(Symbol.FUNCTION,
                                new Cons(read(true, NIL, true)));
            case '(':
                return new Vector(readList());
            case '\\':
                return readCharacterLiteral();
            case '+':
            case '-':
                return handleFeature(c);
            case ':':
                return readUninternedSymbol();
            case '|':
                skipBalancedComment();
                return null;
            case '.':
                return eval(read(true, NIL, true), new Environment(),
                            LispThread.currentThread());
            case '*':
                return readBitVector();
            case 'a':
            case 'A':
                return readArray(numArg);
            case 'b':
            case 'B':
                return readBinary();
            case 'c':
            case 'C':
                return readComplex();
            case 'p':
            case 'P':
                return readPathname();
            case 'x':
            case 'X':
                return readHex();
            default:
                //clearInput();
                //return signal(new LispError("unsupported '#' macro character '" +
                //    c + '\'');
                return null;
        }
    }

    private LispObject readCharacterLiteral() throws ConditionThrowable
    {
        try {
            int n = read();
            if (n < 0)
                return signal(new EndOfFile());
            char c = (char) n;
            StringBuffer sb = new StringBuffer();
            sb.append(c);
            while (true) {
                n = read();
                if (n < 0)
                    break;
                c = (char) n;
                if (Character.isWhitespace(c))
                    break;
                if (c == '(' || c == ')') {
                    unread(c);
                    break;
                }
                sb.append(c);
            }
            String token = sb.toString();
            if (token.length() == 1)
                return LispCharacter.getInstance(token.charAt(0));
            n = nameToChar(token);
            if (n >= 0)
                return LispCharacter.getInstance((char)n);
            return signal(new LispError("unrecognized character name: " + token));
        }
        catch (IOException e) {
            return signal(new StreamError(e));
        }
    }

    // FIXME
    private LispObject handleFeature(char c) throws ConditionThrowable
    {
        LispObject feature = read(true, NIL, true);
        LispObject form = read(true, NIL, true);
        if (feature instanceof Symbol) {
            if (((Symbol)feature).getName().equalsIgnoreCase("armedbear")) {
                if (c == '+')
                    return form;
                else
                    return null;
            } else {
                if (c == '+')
                    return null;
                else
                    return form;
            }
        }
        return null;
    }

    private Symbol readUninternedSymbol() throws ConditionThrowable
    {
        try {
            int n = read();
            if (n < 0) {
                signal(new EndOfFile());
                // Not reached.
                return null;
            }
            char c = (char) n;
            StringBuffer sb = new StringBuffer();
            if (c == '|') {
                while (true) {
                    n = read();
                    if (n < 0) {
                        signal(new EndOfFile());
                        // Not reached.
                        return null;
                    }
                    c = (char) n;
                    if (c == '\\') {
                        // Single escape.
                        n = read();
                        if (n < 0) {
                            signal(new EndOfFile());
                            // Not reached.
                            return null;
                        }
                        sb.append((char)n);
                        continue;
                    }
                    if (c == '|')
                        break;
                    sb.append(c);
                }
            } else {
                sb.append(Utilities.toUpperCase(c));
                while (true) {
                    n = read();
                    if (n < 0)
                        break;
                    c = (char) n;
                    if (c == '\\') {
                        // Single escape.
                        n = read();
                        if (n < 0) {
                            signal(new EndOfFile());
                            // Not reached.
                            return null;
                        }
                        sb.append((char)n);
                        continue;
                    }
                    if (Character.isWhitespace(c))
                        break;
                    if (c == '(' || c == ')') {
                        unread(c);
                        break;
                    }
                    sb.append(Utilities.toUpperCase(c));
                }
            }
            return new Symbol(sb.toString());
        }
        catch (IOException e) {
            signal(new StreamError(e));
            // Not reached.
            return null;
        }
    }

    private void skipBalancedComment() throws ConditionThrowable
    {
        try {
            while (true) {
                int n = read();
                if (n < 0)
                    return;
                if (n == '|') {
                    n = read();
                    if (n == '#')
                        return;
                    else
                        unread(n);
                } else if (n == '#') {
                    n = read();
                    if (n == '|')
                        skipBalancedComment(); // Nested comment. Recurse!
                    else
                        unread(n);
                }
            }
        }
        catch (IOException e) {
            signal(new StreamError(e));
        }
    }

    private LispObject readBitVector() throws ConditionThrowable
    {
        try {
            StringBuffer sb = new StringBuffer();
            while (true) {
                int n = read();
                if (n < 0)
                    break;
                char c = (char) n;
                if (c == '0' || c == '1')
                    sb.append(c);
                else {
                    unread(c);
                    break;
                }
            }
            return new BitVector(sb.toString());
        }
        catch (IOException e) {
            return signal(new StreamError(e));
        }
    }

    private LispObject readArray(int rank) throws ConditionThrowable
    {
        LispObject obj = read(true, NIL, true);
        if (rank == 1)
            return new Vector(obj);
        return new Array(rank, obj);
    }

    private LispObject readComplex() throws ConditionThrowable
    {
        LispObject obj = read(true, NIL, true);
        if (obj instanceof Cons && obj.length() == 2)
            return Complex.getInstance(obj.car(), obj.cadr());
        return signal(new LispError("invalid complex number format #C" + obj));
    }

    private String readMultipleEscape() throws ConditionThrowable
    {
        StringBuffer sb = new StringBuffer();
        while (true) {
            int n = read();
            if (n < 0)
                break;
            char c = (char) n;
            if (c == '|')
                break;
            sb.append(c);
        }
        return sb.toString();
    }

    private LispObject readKeyword() throws ConditionThrowable
    {
        try {
            StringBuffer sb = new StringBuffer();
            while (true) {
                int n = read();
                if (n < 0)
                    break;
                char c = (char) n;
                if (Character.isWhitespace(c))
                    break;
                if (c == '(' || c == ')') {
                    unread(c);
                    break;
                }
                if (c == '|') {
                    sb.append(readMultipleEscape());
                    continue;
                }
                sb.append(Utilities.toUpperCase(c));
            }
            return PACKAGE_KEYWORD.intern(sb.toString());
        }
        catch (IOException e) {
            return signal(new StreamError(e));
        }
    }

    private LispObject readToken(char firstChar) throws ConditionThrowable
    {
        try {
            StringBuffer sb = new StringBuffer();
            sb.append(Utilities.toUpperCase(firstChar));
            while (true) {
                int n = read();
                if (n < 0)
                    return makeObject(sb.toString());
                char c = (char) n;
                if (Character.isWhitespace(c))
                    return makeObject(sb.toString());
                switch (c) {
                    case '(':
                    case ')':
                        unread(c);
                        return makeObject(sb.toString());
                    default:
                        sb.append(Utilities.toUpperCase(c));
                }
            }
        }
        catch (IOException e) {
            return signal(new StreamError(e));
        }
    }

    private LispObject makeObject(String token) throws ConditionThrowable
    {
        final LispThread thread = LispThread.currentThread();
        if (_READ_SUPPRESS_.symbolValueNoThrow(thread) != NIL)
            return NIL;
        char c = token.charAt(0);
        if ("-+0123456789".indexOf(c) >= 0) {
            LispObject number = makeNumber(token);
            if (number != null)
                return number;
        }
        if (token.equals("T"))
            return T;
        if (token.equals("NIL"))
            return NIL;
        if (c == ':')
            return PACKAGE_KEYWORD.intern(token.substring(1));
        int index = token.indexOf("::");
        if (index > 0) {
            String packageName = token.substring(0, index);
            String symbolName = token.substring(index + 2);
            Package pkg = Packages.findPackage(packageName);
            if (pkg == null)
                return signal(new LispError("package \"" + packageName +
                                                           "\" not found"));
            return pkg.intern(symbolName);
        }
        index = token.indexOf(':');
        if (index > 0) {
            String packageName = token.substring(0, index);
            String symbolName = token.substring(index + 1);
            Package pkg = Packages.findPackage(packageName);
            if (pkg == null)
                return signal(new PackageError("package \"" + packageName +
                                                              "\" not found"));
            Symbol symbol = pkg.findExternalSymbol(symbolName);
            if (symbol != null)
                return symbol;
            // Error!
            if (pkg.findInternalSymbol(symbolName) != null)
                return signal(new LispError("symbol \"" + symbolName +
                                                           "\" is not external in package " +
                                                           packageName));
            else
                return signal(new LispError("symbol \"" + symbolName +
                                                           "\" not found in package " +
                                                           packageName));
        }
        // Intern token in current package.
        return ((Package)_PACKAGE_.symbolValueNoThrow(thread)).intern(token);
    }

    private LispObject makeNumber(String token) throws ConditionThrowable
    {
        if (token.indexOf('/') >= 0)
            return makeRatio(token);
        if (token.endsWith("."))
            token = token.substring(0, token.length()-1);
        LispObject number = makeFloat(token);
        if (number != null)
            return number;
        // The first character was checked in makeObject().
        for (int i = token.length(); i-- > 1;) {
            char c = token.charAt(i);
            if (c < '0' || c > '9')
                return null;
        }
        try {
            return new Fixnum(Integer.parseInt(token));
        }
        catch (NumberFormatException e) {}
        // parseInt() failed.
        try {
            return new Bignum(new BigInteger(token));
        }
        catch (NumberFormatException e) {}
        // Not a number.
        return null;
    }

    private LispObject makeRatio(String token) throws ConditionThrowable
    {
        final int index = token.indexOf('/');
        if (index < 0)
            return null;
        try {
            BigInteger numerator = new BigInteger(token.substring(0, index));
            BigInteger denominator = new BigInteger(token.substring(index + 1));
            return number(numerator, denominator);
        }
        catch (NumberFormatException e) {}
        return null;
    }

    private LispObject makeFloat(String token) throws ConditionThrowable
    {
        final int length = token.length();
        if (length == 0)
            return null;
        StringBuffer sb = new StringBuffer();
        int i = 0;
        boolean maybe = false;
        char c = token.charAt(i);
        if (c == '-' || c == '+') {
            sb.append(c);
            ++i;
        }
        while (i < length) {
            c = token.charAt(i);
            if (c == '.' || (c >= '0' && c <= '9')) {
                if (c == '.')
                    maybe = true;
                sb.append(c);
                ++i;
            } else
                break;
        }
        if (i < length) {
            if ("esfdlESFDL".indexOf(token.charAt(i)) >= 0) {
                // Exponent marker.
                maybe = true;
                sb.append('E');
                ++i;
            }
        }
        if (!maybe)
            return null;
        // Append rest of token.
        sb.append(token.substring(i));
        try {
            return new LispFloat(Double.parseDouble(sb.toString()));
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    private LispObject readBinary() throws ConditionThrowable
    {
        try {
            StringBuffer sb = new StringBuffer();
            while (true) {
                int n = read();
                if (n < 0)
                    break;
                char c = (char) n;
                if (c == '0' || c == '1')
                    sb.append(c);
                else {
                    unread(c);
                    break;
                }
            }
            String s = sb.toString();
            try {
                return new Fixnum(Integer.parseInt(s, 2));
            }
            catch (NumberFormatException e) {}
            // parseInt() failed.
            try {
                return new Bignum(new BigInteger(s, 2));
            }
            catch (NumberFormatException e) {}
            // Not a number.
            return signal(new LispError());
        }
        catch (IOException e) {
            return signal(new StreamError(e));
        }
    }

    private LispObject readHex() throws ConditionThrowable
    {
        try {
            StringBuffer sb = new StringBuffer();
            while (true) {
                int n = read();
                if (n < 0)
                    break;
                char c = (char) n;
                if (c >= '0' && c <= '9')
                    sb.append(c);
                else if (c >= 'A' && c <= 'F')
                    sb.append(c);
                else if (c >= 'a' && c <= 'f')
                    sb.append(c);
                else {
                    unread(c);
                    break;
                }
            }
            String s = sb.toString();
            try {
                return new Fixnum(Integer.parseInt(s, 16));
            }
            catch (NumberFormatException e) {}
            // parseInt() failed.
            try {
                return new Bignum(new BigInteger(s, 16));
            }
            catch (NumberFormatException e) {}
            // Not a number.
            return signal(new LispError());
        }
        catch (IOException e) {
            return signal(new StreamError(e));
        }
    }

    private char flushWhitespace() throws ConditionThrowable
    {
        while (true) {
            int n = read();
            if (n < 0) {
                signal(new EndOfFile());
                // Not reached.
                return 0;
            }
            char c = (char) n;
            if (!Character.isWhitespace(c))
                return c;
        }
    }

    // read-line &optional stream eof-error-p eof-value recursive-p
    // => line, missing-newline-p
    // recursive-p is ignored
    public LispObject readLine(boolean eofError, LispObject eofValue)
        throws ConditionThrowable
    {
        final LispThread thread = LispThread.currentThread();
        StringBuffer sb = new StringBuffer();
        while (true) {
            int n = read();
            if (n < 0) {
                if (sb.length() == 0) {
                    if (eofError)
                        return signal(new EndOfFile());
                    return thread.setValues(eofValue, T);
                }
                return thread.setValues(new LispString(sb.toString()), T);
            }
            if (n == '\n')
                return thread.setValues(new LispString(sb.toString()), NIL);
            else
                sb.append((char)n);
        }
    }

    // read-char &optional stream eof-error-p eof-value recursive-p => char
    // recursive-p is ignored
    public LispObject readChar(boolean eofError, LispObject eofValue)
        throws ConditionThrowable
    {
        int n = read();
        if (n < 0) {
            if (eofError)
                return signal(new EndOfFile());
            else
                return eofValue;
        }
        return LispCharacter.getInstance((char)n);
    }

    // read-char-no-hang &optional stream eof-error-p eof-value recursive-p => char
    // recursive-p is ignored
    public final LispObject readCharNoHang(boolean eofError, LispObject eofValue)
        throws ConditionThrowable
    {
        return ready() ? readChar(eofError, eofValue) : NIL;
    }

    // unread-char character &optional input-stream => nil
    public LispObject unreadChar(LispCharacter c) throws ConditionThrowable
    {
        try {
            unread(c.getValue());
        }
        catch (IOException e) {
            return signal(new StreamError(e));
        }
        return NIL;
    }

    // clear-input &optional input-stream => nil
    public LispObject clearInput() throws ConditionThrowable
    {
        while (ready())
            read();
        return NIL;
    }

    // close stream &key abort => result
    // Must return true if stream was open, otherwise implementation-dependent.
    public LispObject close(LispObject abort) throws ConditionThrowable
    {
        try {
            reader.close();
            setOpen(false);
            return T;
        }
        catch (IOException e) {
            return signal(new StreamError(e));
        }
    }

    protected int read() throws ConditionThrowable
    {
        try {
            int n = reader.read();
            ++offset;
            if (n == '\n')
                ++lineNumber;
            return n;
        }
        catch (IOException e) {
            signal(new StreamError(e));
            // Not reached.
            return -1;
        }
    }

    protected void unread(int n) throws IOException
    {
        reader.unread(n);
        --offset;
        if (n == '\n')
            --lineNumber;
    }

    protected boolean ready() throws ConditionThrowable
    {
        try {
            return reader.ready();
        }
        catch (IOException e) {
            signal(new StreamError(e));
            // Not reached.
            return false;
        }
    }

    public String toString()
    {
        return unreadableString("STREAM [character input]");
    }
}
