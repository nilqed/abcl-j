;;; boot.lisp
;;;
;;; Copyright (C) 2003 Peter Graves
;;; $Id: boot.lisp,v 1.11 2003-03-05 17:46:45 piso Exp $
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

(let ((*load-verbose* nil))
  (dolist (name '("documentation"
                  "backquote"
                  "setf"
                  "macros"
                  "list"
                  "sequences"
                  "symbol"
                  "error"
                  "defstruct"
                  "compiler"))
    (cl::%load (concatenate 'string name ".lisp"))))


;; Miscellany.

(in-package "COMMON-LISP")

(export '(open call-arguments-limit lambda-parameters-limit
          multiple-values-limit))

(defun open (filename
	     &key
	     (direction :input)
	     (element-type 'base-char)
	     (if-exists nil if-exists-given)
	     (if-does-not-exist nil if-does-not-exist-given)
	     (external-format :default))
  (if (eq direction :output)
      (%open-output-file filename)
      (error "operation not supported")))

(defconstant call-arguments-limit 50)

(defconstant lambda-parameters-limit 50)

(defconstant multiple-values-limit 20)


;; Compile the world.
(dolist (sym (package-symbols :cl))
  (unless (eq sym 'compile))
  (when (fboundp sym)
    (unless (or (special-operator-p sym) (macro-function sym))
      (let ((f (fdefinition sym)))
        (unless (compiled-function-p f)
;;           (format t "compiling ~S~%" sym)
;;           (finish-output)
          (compile sym))))))
