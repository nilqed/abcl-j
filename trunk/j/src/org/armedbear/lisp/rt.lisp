;;; rt.lisp

(unless (find-package :rt)
  (make-package :rt)
  (use-package "COMMON-LISP" :rt))

(in-package :rt)
(export '(deftest))

(defvar *passed* 0)
(defvar *failed* 0)

(defun equalp-with-case (x y)
  (cond
   ((consp x)
    (and (consp y)
	 (equalp-with-case (car x) (car y))
	 (equalp-with-case (cdr x) (cdr y))))
   ((vectorp x)
    (and (vectorp y)
	 (let ((x-len (length x))
	       (y-len (length y)))
	   (and (eql x-len y-len)
                (progn
                  (dotimes (i x-len)
                    (unless (equalp-with-case (aref x i) (aref y i))
                      (return nil)))
                  t)))))
   (t (eql x y))))

(defmacro deftest (name form &rest values)
  (format t "Test ~s~%" `,name)
  (finish-output)
  (let* ((aborted nil)
        (r (handler-case (multiple-value-list
                          (eval `,form))
                         (error (c) (setf aborted t) (list c))))
        (passed (and (not aborted) (equalp-with-case r `,values))))
    (unless passed
      (format t "  Expected value: ~s~%"
              (if (= (length `,values) 1)
                  (car `,values)
                  `,values))
      (format t "    Actual value: ~s~%"
              (if (= (length r) 1)
                  (car r)
                  r))
      (finish-output))
    (if passed (incf *passed*) (incf *failed*))))

(unless (find-package :cl-test)
  (make-package :cl-test)
  (use-package "COMMON-LISP" :cl-test))

(in-package :cl-test)
(use-package :rt)

(defun notnot (x) (not (not x)))

(defmacro notnot-mv (form)
  `(notnot-mv-fn (multiple-value-list ,form)))

(defun notnot-mv-fn (results)
  (if (null results)
      (values)
      (apply #'values
             (not (not (first results)))
             (rest results))))

(defmacro not-mv (form)
  `(not-mv-fn (multiple-value-list ,form)))

(defun not-mv-fn (results)
  (if (null results)
      (values)
      (apply #'values
             (not (first results))
             (rest results))))

;;; Macro to check that a function is returning a specified number of values
;;; (defaults to 1)
(defmacro check-values (form &optional (num 1))
  (let ((v (gensym))
	(n (gensym)))
    `(let ((,v (multiple-value-list ,form))
           (,n ,num))
       (check-values-length ,v ,n ',form)
       (car ,v))))

(defun check-values-length (results expected-number form)
  (let ((n expected-number))
    (dolist (e results)
      (decf n))
    (unless (= n 0)
      (error "Expected ~A results from ~A, got ~A results instead.~%"
             expected-number form n))))

(defun eqt (x y)
  "Like EQ, but guaranteed to return T for true."
  (apply #'values (mapcar #'notnot (multiple-value-list (eq x y)))))

(defun eqlt (x y)
  "Like EQL, but guaranteed to return T for true."
  (apply #'values (mapcar #'notnot (multiple-value-list (eql x y)))))

(defun equalt (x y)
  "Like EQUAL, but guaranteed to return T for true."
  (apply #'values (mapcar #'notnot (multiple-value-list (equal x y)))))

(defun equalpt (x y)
  "Like EQUALP, but guaranteed to return T for true."
  (apply #'values (mapcar #'notnot (multiple-value-list (equalp x y)))))

(defun make-int-list (n)
  (do ((result nil)
       (i (1- n) (1- i)))
    ((< i 0) result)
    (setq result (cons i result))))

(defun make-int-array (n &optional (fn #'make-array))
  (let ((a (funcall fn n)))
    (dotimes (i n)
      (setf (aref a i) i))
    a))

(defmacro classify-error* (form)
  "Evaluate form in safe mode, returning its value if there is no error.
   If an error does occur, return a symbol classify the error, or allow
   the condition to go uncaught if it cannot be classified."
  `(handler-case ,form
                (undefined-function () 'undefined-function)
                (program-error () 'program-error)
                (package-error () 'package-error)
                (type-error    () 'type-error)
                (control-error () 'control-error)
                ))

(defmacro classify-error (form)
  `(classify-error* (eval ,form)))

(defstruct scaffold
  node
  car
  cdr)

(defun make-scaffold-copy (x)
  "Make a tree that will be used to check if a tree has been changed."
  (if
   (consp x)
   (make-scaffold :node x
                  :car (make-scaffold-copy (car x))
                  :cdr (make-scaffold-copy (cdr x)))
   (make-scaffold :node x
                  :car nil
                  :cdr nil)))

(defun check-scaffold-copy (x xcopy)
  "Return t if xcopy were produced from x by make-scaffold-copy,
   and none of the cons cells in the tree rooted at x have been
   changed."
  (and (eq x (scaffold-node xcopy))
       (or
	(not (consp x))
	(and
	 (check-scaffold-copy (car x) (scaffold-car xcopy))
	 (check-scaffold-copy (cdr x) (scaffold-cdr xcopy))))))

(defun evendigitp (c)
  (notnot (find c "02468")))

(defun odddigitp (c)
  (notnot (find c "13579")))

(defun check-sublis (a al &key (key 'no-key) test test-not)
  "Apply sublis al a with various keys.  Check that
   the arguments are not themselves changed.  Return nil
   if the arguments do get changed."
  (setf a (copy-tree a))
  (setf al (copy-tree al))
  (let ((acopy (make-scaffold-copy a))
	(alcopy (make-scaffold-copy al)))
    (let ((as
	   (apply #'sublis al a
		  `(,@(when test `(:test ,test))
                      ,@(when test-not `(:test-not ,test-not))
                      ,@(unless (eqt key 'no-key) `(:key ,key))))))
      (and
       (check-scaffold-copy a acopy)
       (check-scaffold-copy al alcopy)
       as))))

(defun check-nsublis (a al &key (key 'no-key) test test-not)
  "Apply nsublis al a, copying these arguments first."
  (setf a (copy-tree a))
  (setf al (copy-tree al))
  (let ((as
	 (apply #'sublis (copy-tree al) (copy-tree a)
		`(,@(when test `(:test ,test))
		    ,@(when test-not `(:test-not ,test-not))
		    ,@(unless (eqt key 'no-key) `(:key ,key))))))
    as))

(defun check-subst (new old tree &key (key 'no-key) test test-not)
  "Call subst new old tree, with keyword arguments if present.
   Check that the arguments are not changed."
  (setf new (copy-tree new))
  (setf old (copy-tree old))
  (setf tree (copy-tree tree))
  (let ((newcopy (make-scaffold-copy new))
	(oldcopy (make-scaffold-copy old))
	(treecopy (make-scaffold-copy tree)))
    (let ((result
	   (apply #'subst new old tree
		  `(,@(unless (eqt key 'no-key) `(:key ,key))
                      ,@(when test `(:test ,test))
                      ,@(when test-not `(:test-not ,test-not))))))
      (and (check-scaffold-copy new newcopy)
	   (check-scaffold-copy old oldcopy)
	   (check-scaffold-copy tree treecopy)
	   result))))


(defun check-subst-if (new pred tree &key (key 'no-key))
  "Call subst-if new pred tree, with various keyword arguments
   if present.  Check that the arguments are not changed."
  (setf new (copy-tree new))
  (setf tree (copy-tree tree))
  (let ((newcopy (make-scaffold-copy new))
	(predcopy (make-scaffold-copy pred))
	(treecopy (make-scaffold-copy tree)))
    (let ((result
	   (apply #'subst-if new pred tree
		  (unless (eqt key 'no-key) `(:key ,key)))))
      (and (check-scaffold-copy new newcopy)
	   (check-scaffold-copy pred predcopy)
	   (check-scaffold-copy tree treecopy)
	   result))))

(defun check-subst-if-not (new pred tree &key (key 'no-key))
  "Call subst-if-not new pred tree, with various keyword arguments
   if present.  Check that the arguments are not changed."
  (setf new (copy-tree new))
  (setf tree (copy-tree tree))
  (let ((newcopy (make-scaffold-copy new))
	(predcopy (make-scaffold-copy pred))
	(treecopy (make-scaffold-copy tree)))
    (let ((result
	   (apply #'subst-if-not new pred tree
		  (unless (eqt key 'no-key) `(:key ,key)))))
      (and (check-scaffold-copy new newcopy)
	   (check-scaffold-copy pred predcopy)
	   (check-scaffold-copy tree treecopy)
	   result))))

(defun check-nsubst (new old tree &key (key 'no-key) test test-not)
  "Call nsubst new old tree, with keyword arguments if present."
  (setf new (copy-tree new))
  (setf old (copy-tree old))
  (setf tree (copy-tree tree))
  (apply #'nsubst new old tree
	 `(,@(unless (eqt key 'no-key) `(:key ,key))
	     ,@(when test `(:test ,test))
	     ,@(when test-not `(:test-not ,test-not)))))

(defun check-nsubst-if (new pred tree &key (key 'no-key))
  "Call nsubst-if new pred tree, with keyword arguments if present."
  (setf new (copy-tree new))
  (setf tree (copy-tree tree))
  (apply #'nsubst-if new pred tree
	 (unless (eqt key 'no-key) `(:key ,key))))

(defun check-nsubst-if-not (new pred tree &key (key 'no-key))
  "Call nsubst-if-not new pred tree, with keyword arguments if present."
  (setf new (copy-tree new))
  (setf tree (copy-tree tree))
  (apply #'nsubst-if-not new pred tree
         (unless (eqt key 'no-key) `(:key ,key))))

(defun check-copy-list-copy (x y)
  "Check that y is a copy of the list x."
  (if
   (consp x)
   (and (consp y)
        (not (eqt x y))
        (eqt (car x) (car y))
        (check-copy-list-copy (cdr x) (cdr y)))
   (and (eqt x y) t)))

(defun check-copy-list (x)
  "Apply copy-list, checking that it properly copies,
   and checking that it does not change its argument."
  (let ((xcopy (make-scaffold-copy x)))
    (let ((y (copy-list x)))
      (and
       (check-scaffold-copy x xcopy)
       (check-copy-list-copy x y)
       y))))

(defun safe-elt (x n)
  (classify-error* (elt x n)))

(in-package :cl-user)

(defun do-tests (&rest args)
  (let ((rt::*passed* 0) (rt::*failed* 0)
;;         (prefix "/usr/share/common-lisp/source/ansi-tests/")
        (prefix "/home/peter/gcl/ansi-tests/")
        (suffix ".lsp")
        (tests (or args (list "and"
                              "apply"
                              "aref"
                              "bit-vector"
                              "block"
                              "boundp"
                              "call-arguments-limit"
                              "case"
                              "catch"
                              "cond"
                              "cons-test-01"
                              "cons-test-02"
                              "cons-test-03"
                              "cons-test-04"
                              "cons-test-06"
                              "cons-test-07"
                              "cons-test-08"
                              "cons-test-09"
                              "cons-test-10"
                              "cons-test-11"
                              "cons-test-12"
                              "cons-test-13"
                              "cons-test-14"
                              "cons-test-15"
                              "cons-test-16"
                              "cons-test-17"
                              "cons-test-18"
                              "cons-test-19"
                              "cons-test-20"
                              "cons-test-21"
                              "cons-test-22"
                              "cons-test-23"
                              "cons-test-24"
                              "constantly"
                              "constantp"
                              "copy-seq"
                              "count"
                              "count-if"
                              "count-if-not"
                              "defvar"
                              "equal"
                              "equalp"
                              "eql"
                              "eval"
                              "every"
                              "fill-pointer"
                              "find"
                              "find-if"
                              "find-if-not"
                              "flet"
                              "funcall"
                              "identity"
                              "if"
                              "iteration"
                              "labels"
                              "lambda"
                              "lambda-parameters-limit"
                              "length"
                              "let"
                              "make-string"
                              "multiple-value-bind"
                              "multiple-value-call"
                              "nil"
                              "notany"
                              "notevery"
                              "or"
                              "position"
                              "position-if"
                              "position-if-not"
                              "prog1"
                              "progn"
                              "some"
                              "string"
                              "svref"
                              "t"
                              "tagbody"
                              "unless"
                              "unwind-protect"
                              "values"
                              "vector"
                              "vectorp"
                              "when"))))
    (dolist (test tests)
             (load (concatenate 'string prefix test suffix)))
    (format t "~A tests: ~A passed, ~A failed~%"
            (+ rt::*passed* rt::*failed*)
            rt::*passed*
            rt::*failed*))
  (values))
