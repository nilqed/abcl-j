;;; java.lisp
;;;
;;; Copyright (C) 2003 Peter Graves
;;;
;;; This program is free software; you can redistribute it and/or
;;; modify it under the terms of the GNU General Public License
;;; as published by the Free Software Foundation; either version 2
;;; of the License, or (at your option) any later version.
;;;
;;; This program is distributed in the hope that it will be useful,
;;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;;; GNU General Public License for more details.
;;;
;;; You should have received a copy of the GNU General Public License
;;; along with this program; if not, write to the Free Software
;;; Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

(in-package "JAVA")

(defun jregister-handler (object event handler &key data count)
  (%jregister-handler object event handler data count))

(defun jclass-name (class)
  "Returns the name of CLASS as a Lisp string"
  (jcall (jmethod "java.lang.Class" "getName") class))

(defun jclass-for-name (name)
  "Returns the class of name NAME"
  (jstatic "forName" "java.lang.Class" name))

(defun jobject-class (obj)
  "Returns the Java class that OBJ belongs to"
  (jcall (jmethod "java.lang.Object" "getClass") obj))

(defun jclass-constructors (class)
  "Returns a vector of constructors for CLASS"
  (jcall (jmethod "java.lang.Class" "getConstructors") class))

(defun jconstructor-params (constructor)
  "Returns a vector of parameter types (Java classes) for CONSTRUCTOR"
  (jcall (jmethod "java.lang.reflect.Constructor" "getParameterTypes") constructor))

(defun jclass-fields (class &key declared public)
  "Returns a vector of all (or just the declared/public, if DECLARED/PUBLIC is true) fields of CLASS"
  (let* ((getter (if declared "getDeclaredFields" "getFields"))
         (fields (jcall (jmethod "java.lang.Class" getter) class)))
    (if public (delete-if-not #'jmember-public-p fields) fields)))


(defun jfield-type (field)
  "Returns the type (Java class) of FIELD"
  (jcall (jmethod "java.lang.reflect.Field" "getType") field))

(defun jfield-name (field)
  "Returns the name of FIELD as a Lisp string"
  (jcall (jmethod "java.lang.reflect.Field" "getName") field))

(defun jclass-methods (class &key declared public)
  "Return a vector of all (or just the declared/public, if DECLARED/PUBLIC is true) methods of CLASS"
  (let* ((getter (if declared "getDeclaredMethods" "getMethods"))
         (methods (jcall (jmethod "java.lang.Class" getter) class)))
    (if public (delete-if-not #'jmember-public-p methods) methods)))


(defun jmethod-params (method)
  "Returns a vector of parameter types (Java classes) for METHOD"
  (jcall (jmethod "java.lang.reflect.Method" "getParameterTypes") method))

(defun jmethod-return-type (method)
  "Returns the result type (Java class) of the METHOD"
  (jcall (jmethod "java.lang.reflect.Method" "getReturnType") method))

(defun jmethod-name (method)
  "Returns the name of METHOD as a Lisp string"
  (jcall (jmethod "java.lang.reflect.Method" "getName") method))

(defun jinstance-of-p (obj class)
  "OBJ is an instance of CLASS (or one of its subclasses)"
  (jcall (jmethod "java.lang.Class" "isInstance" "java.lang.Object") class obj))

(defun jmember-static-p (member)
  "MEMBER is a static member of its declaring class"
  (jstatic (jmethod "java.lang.reflect.Modifier" "isStatic" "int")
    "java.lang.reflect.Modifier"
    (jcall (jmethod "java.lang.reflect.Member" "getModifiers") member)))

(defun jmember-public-p (member)
  "MEMBER is a public member of its declaring class"
  (jstatic (jmethod "java.lang.reflect.Modifier" "isPublic" "int")
    "java.lang.reflect.Modifier"
    (jcall (jmethod "java.lang.reflect.Member" "getModifiers") member)))
