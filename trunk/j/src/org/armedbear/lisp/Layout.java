/*
 * Layout.java
 *
 * Copyright (C) 2003 Peter Graves
 * $Id: Layout.java,v 1.2 2003-12-13 00:02:47 piso Exp $
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

public final class Layout extends LispObject
{
    private final LispClass cls;
    private final LispObject length;

    public Layout(LispClass cls, LispObject length)
    {
        this.cls = cls;
        this.length = length;
    }

    public LispClass getLispClass()
    {
        return cls;
    }

    private static final Primitive2 MAKE_LAYOUT =
        new Primitive2("make-layout", PACKAGE_SYS, false)
    {
        public LispObject execute(LispObject first, LispObject second)
            throws ConditionThrowable
        {
            try {
                return new Layout((LispClass)first, second);
            }
            catch (ClassCastException e) {
                return signal(new TypeError(first, "class"));
            }
        }

    };

    private static final Primitive1 LAYOUT_CLASS =
        new Primitive1("layout-class", PACKAGE_SYS, false)
    {
        public LispObject execute(LispObject arg) throws ConditionThrowable
        {
            try {
                return ((Layout)arg).cls;
            }
            catch (ClassCastException e) {
                return signal(new TypeError(arg, "layout"));
            }
        }
    };

    private static final Primitive1 LAYOUT_LENGTH =
        new Primitive1("layout-length", PACKAGE_SYS, false)
    {
        public LispObject execute(LispObject arg) throws ConditionThrowable
        {
            try {
                return ((Layout)arg).length;
            }
            catch (ClassCastException e) {
                return signal(new TypeError(arg, "layout"));
            }
        }
    };
}
