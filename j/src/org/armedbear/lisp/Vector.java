/*
 * Vector.java
 *
 * Copyright (C) 2002-2003 Peter Graves
 * $Id: Vector.java,v 1.9 2003-02-28 17:00:52 piso Exp $
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

public class Vector extends AbstractVector implements SequenceType, VectorType
{
    private final LispObject[] elements;
    private final int capacity;

    public Vector(int capacity)
    {
        elements = new LispObject[capacity];
        for (int i = capacity; i-- > 0;)
            elements[i] = NIL;
        this.capacity = capacity;
    }

    public Vector(LispObject list) throws LispError
    {
        elements = list.copyToArray();
        capacity = elements.length;
    }

    public Vector(LispObject[] array)
    {
        elements = array;
        capacity = array.length;
    }

    public LispObject typeOf()
    {
        return list(Symbol.VECTOR, T, new Fixnum(capacity));
    }

    public boolean isSimpleVector()
    {
        return fillPointer < 0;
    }

    public int capacity()
    {
        return capacity;
    }

    public int length()
    {
        return fillPointer >= 0 ? fillPointer : capacity;
    }

    public LispObject elt(long index) throws LispError
    {
        long limit = length();
        if (index < 0 || index >= limit)
            badIndex(index);
        return elements[(int)index];
    }

    public LispObject remove(LispObject item) throws LispError
    {
        throw new LispError("not implemented");
    }

    public LispObject get(int index)
    {
        return elements[index];
    }

    public void set(int index, LispObject newValue)
    {
        elements[index] = newValue;
    }

    public void fill(LispObject obj) throws LispError
    {
        for (int i = capacity; i-- > 0;)
            elements[i] = obj;
    }

    public String toString()
    {
        StringBuffer sb = new StringBuffer("#(");
        // FIXME The limit should be based on the value of *PRINT-LENGTH*.
        final int limit = Math.min(length(), 10);
        for (int i = 0; i < limit; i++) {
            if (i > 0)
                sb.append(' ');
            sb.append(elements[i]);
        }
        if (limit < length())
            sb.append(" ...");
        sb.append(')');
        return sb.toString();
    }
}
