;;; profiler.lisp
;;;
;;; Copyright (C) 2003 Peter Graves
;;; $Id: profiler.lisp,v 1.11 2004-10-10 17:18:14 piso Exp $
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

(in-package #:profiler)

(export '(*hidden-functions*))

(require '#:clos)
(require '#:format)

(defvar *type* nil)

(defvar *granularity* 1 "Sampling interval (in milliseconds).")

(defvar *hidden-functions*
  '(funcall apply eval
    sys::%eval sys::interactive-eval
    tpl::repl tpl::top-level-loop))

(defstruct (profile-info
            (:constructor make-profile-info (object count)))
  object
  count)

;; Returns list of all symbols with non-zero call counts.
(defun list-called-objects ()
  (let ((result '()))
    (dolist (pkg (list-all-packages))
      (dolist (sym (sys:package-symbols pkg))
        (unless (memq sym *hidden-functions*)
          (when (fboundp sym)
            (let* ((definition (fdefinition sym))
                   (count (sys:call-count definition)))
              (unless (zerop count)
                (cond ((typep definition 'generic-function)
                       (push (make-profile-info definition count) result)
                       (dolist (method (sys::generic-function-methods definition))
                         (setf count (sys:call-count (sys::method-function method)))
                         (unless (zerop count)
                           (push (make-profile-info method count) result))))
                      (t
                       (push (make-profile-info sym count) result)))))))))
    (remove-duplicates result :key 'profile-info-object :test 'eq)))

(defun object-name (object)
  (cond ((symbolp object)
         (symbol-name object))
        ((typep object 'generic-function)
         (sys::generic-function-name object))
        ((typep object 'method)
         (format nil "~A ~A"
                 (sys::generic-function-name (sys::method-generic-function object))
                 (mapcar #'class-name (sys::method-specializers object))))))

(defun show-call-count (info max-count)
  (let* ((object (profile-info-object info))
         (count (profile-info-count info))
         (function (if (symbolp object) (fdefinition object) object)))
    (if max-count
        (format t "~5,1F ~8D ~A~A~%"
                (/ (* count 100.0) max-count)
                count
                (object-name object)
                (if (or (compiled-function-p function)
                        (and (symbolp object) (special-operator-p object)))
                    ""
                    " [interpreted function]"))
        (format t "~8D ~A~A~%"
                count
                (object-name object)
                (if (or (compiled-function-p function)
                        (and (symbolp object) (special-operator-p object)))
                    ""
                    " [interpreted function]")))))

(defun show-call-counts ()
  (let ((list (list-called-objects)))
    (setf list (sort list #'< :key 'profile-info-count))
    (let ((max-count nil))
      (when (eq *type* :time)
        (let ((last-info (car (last list))))
          (setf max-count (if last-info
                              (profile-info-count last-info)
                              nil))
          (when (eql max-count 0)
            (setf max-count nil))))
      (dolist (info list)
        (show-call-count info max-count))))
  (values))

(defun start-profiler (&key type)
  "Starts the profiler.
  :TYPE may be either :TIME (statistical sampling) or :COUNT-ONLY (exact call
  counts)."
  (unless type
    (setf type :time))
  (unless (memq type '(:time :count-only))
    (error ":TYPE must be :TIME or :COUNT-ONLY"))
  (setf *type* type)
  (%start-profiler type *granularity*))

(defmacro with-profiling ((&key type) &body body)
  `(unwind-protect (progn (start-profiler :type ,type) ,@body)
                   (stop-profiler)))