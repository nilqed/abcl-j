/*
 * LispError.java
 *
 * Copyright (C) 2002-2005 Peter Graves
 * $Id: LispError.java,v 1.13 2005-06-21 18:42:13 piso Exp $
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

public class LispError extends SeriousCondition
{
    public LispError()
    {
    }

    protected LispError(LispClass cls)
    {
        super(cls);
    }

    public LispError(LispObject initArgs) throws ConditionThrowable
    {
        super(initArgs);
    }

    public LispError(String message)
    {
        super(message);
    }

    public LispObject typeOf()
    {
        return Symbol.ERROR;
    }

    public LispObject classOf()
    {
        return StandardClass.ERROR;
    }

    public LispObject typep(LispObject type) throws ConditionThrowable
    {
        if (type == Symbol.ERROR)
            return T;
        if (type == StandardClass.ERROR)
            return T;
        return super.typep(type);
    }
}
