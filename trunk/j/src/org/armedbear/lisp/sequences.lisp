;;; sequences.lisp
;;;
;;; Copyright (C) 2003 Peter Graves
;;; $Id: sequences.lisp,v 1.42 2003-06-10 15:28:25 piso Exp $
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

(in-package "COMMON-LISP")

(export '(subseq copy-seq fill
          replace
          reverse nreverse
          concatenate
          map map-into
          reduce
          remove remove-if remove-if-not
          remove-duplicates delete-duplicates
          position position-if position-if-not
          find find-if find-if-not
          count count-if count-if-not
          mismatch
          search))

(autoload 'make-sequence "make-sequence.lisp")

(defmacro seq-dispatch (sequence list-form array-form)
  `(if (listp ,sequence)
       ,list-form
       ,array-form))

(defmacro type-specifier-atom (type)
  `(if (atom ,type) ,type (car ,type)))

(defun make-sequence-of-type (type length)
  (case (type-specifier-atom type)
    (list (make-list length))
    ((bit-vector simple-bit-vector) (make-array length :element-type 'bit))
    (string (make-string length))
    (vector (make-array length))
    (t
     (error 'type-error))))

(defmacro make-sequence-like (sequence length)
  `(make-sequence-of-type (type-of ,sequence) ,length))


;;; SUBSEQ (from CMUCL)

(defun list-subseq (sequence start &optional end)
  (if (and end (>= start end))
      ()
      (let* ((groveled (nthcdr start sequence))
	     (result (list (car groveled))))
	(if groveled
	    (do ((list (cdr groveled) (cdr list))
		 (splice result (cdr (rplacd splice (list (car list)))))
		 (index (1+ start) (1+ index)))
              ((or (atom list) (and end (= index end)))
               result))
	    ()))))

(defun subseq (sequence start &optional end)
  (seq-dispatch sequence
		(list-subseq sequence start end)
		(vector-subseq sequence start end)))


;; COPY-SEQ (from CMUCL)

(defmacro vector-copy-seq (sequence type)
  `(let ((length (length ,sequence)))
     (do ((index 0 (1+ index))
	  (copy (make-sequence-of-type ,type length)))
       ((= index length) copy)
       (setf (aref copy index) (aref ,sequence index)))))

(defmacro list-copy-seq (list)
  `(if (atom ,list) '()
       (let ((result (cons (car ,list) '()) ))
	 (do ((x (cdr ,list) (cdr x))
	      (splice result
		      (cdr (rplacd splice (cons (car x) '() ))) ))
           ((atom x) (unless (null x)
                       (rplacd splice x))
            result)))))

(defun copy-seq (sequence)
  (seq-dispatch sequence
		(list-copy-seq* sequence)
		(vector-copy-seq* sequence)))

(defun list-copy-seq* (sequence)
  (list-copy-seq sequence))

(defun vector-copy-seq* (sequence)
  (vector-copy-seq sequence (type-of sequence)))


;;; FILL (from CMUCL)

(defmacro vector-fill (sequence item start end)
  `(do ((index ,start (1+ index)))
     ((= index ,end) ,sequence)
     (setf (aref ,sequence index) ,item)))

(defmacro list-fill (sequence item start end)
  `(do ((current (nthcdr ,start ,sequence) (cdr current))
        (index ,start (1+ index)))
     ((or (atom current) (and end (= index ,end)))
      sequence)
     (rplaca current ,item)))

(defun list-fill* (sequence item start end)
  (list-fill sequence item start end))

(defun vector-fill* (sequence item start end)
  (when (null end) (setq end (length sequence)))
  (vector-fill sequence item start end))

(defun fill (sequence item &key (start 0) end)
  (seq-dispatch sequence
		(list-fill* sequence item start end)
		(vector-fill* sequence item start end)))


;;; REPLACE (from ECL)

(defun replace (sequence1 sequence2
                          &key start1  end1
                          start2 end2 )
  (with-start-end start1 end1 sequence1
    (with-start-end start2 end2 sequence2
      (if (and (eq sequence1 sequence2)
               (> start1 start2))
          (do* ((i 0 (1+ i))
                (l (if (< (- end1 start1)
                          (- end2 start2))
                       (- end1 start1)
                       (- end2 start2)))
                (s1 (+ start1 (1- l)) (1- s1))
                (s2 (+ start2 (1- l)) (1- s2)))
               ((>= i l) sequence1)
            (setf (elt sequence1 s1) (elt sequence2 s2)))
          (do ((i 0 (1+ i))
               (l (if (< (- end1 start1)
                         (- end2 start2))
                      (- end1 start1)
                      (- end2 start2)))
               (s1 start1 (1+ s1))
               (s2 start2 (1+ s2)))
              ((>= i l) sequence1)
            (setf (elt sequence1 s1) (elt sequence2 s2)))))))


;;; CONCATENATE (from GCL)

(defun concatenate (result-type &rest sequences)
  (do ((x (make-sequence result-type
			 (apply #'+ (mapcar #'length sequences))))
       (s sequences (cdr s))
       (i 0))
    ((null s) x)
    (do ((j 0 (1+ j))
         (n (length (car s))))
      ((>= j n))
      (setf (elt x i) (elt (car s) j))
      (incf i))))


;;; MAP (from ECL)

(defun map (result-type function sequence &rest more-sequences)
  (setq more-sequences (cons sequence more-sequences))
  (let ((l (apply #'min (mapcar #'length more-sequences))))
    (if (null result-type)
        (do ((i 0 (1+ i))
             (l l))
          ((>= i l) nil)
          (apply function (mapcar #'(lambda (z) (elt z i))
                                  more-sequences)))
        (let ((x (make-sequence result-type l)))
          (do ((i 0 (1+ i))
               (l l))
            ((>= i l) x)
            (setf (elt x i)
                  (apply function (mapcar #'(lambda (z) (elt z i))
                                          more-sequences))))))))


;;; MAP-INTO (from CMUCL)

(defun map-into (result-sequence function &rest sequences)
  (let* ((fp-result
	  (and (arrayp result-sequence)
	       (array-has-fill-pointer-p result-sequence)))
	 (len (apply #'min
		     (if fp-result
			 (array-dimension result-sequence 0)
			 (length result-sequence))
		     (mapcar #'length sequences))))

    (when fp-result
      (setf (fill-pointer result-sequence) len))

    (dotimes (index len)
      (setf (elt result-sequence index)
	    (apply function
		   (mapcar #'(lambda (seq) (elt seq index))
			   sequences)))))
  result-sequence)

;;; REDUCE (from OpenMCL)

(defmacro list-reduce (function sequence start end initial-value ivp key)
  (let ((what `(if ,key (funcall ,key (car sequence)) (car sequence))))
    `(let ((sequence (nthcdr ,start ,sequence)))
       (do ((count (if ,ivp ,start (1+ ,start)) (1+ count))
            (sequence (if ,ivp sequence (cdr sequence))
                      (cdr sequence))
            (value (if ,ivp ,initial-value ,what)
                   (funcall ,function value ,what)))
         ((= count ,end) value)))))


(defmacro list-reduce-from-end (function sequence start end
                                         initial-value ivp key)
  (let ((what `(if ,key (funcall ,key (car sequence)) (car sequence))))
    `(let ((sequence (nthcdr (- (length ,sequence) ,end) (reverse ,sequence))))
       (do ((count (if ,ivp ,start (1+ ,start)) (1+ count))
            (sequence (if ,ivp sequence (cdr sequence))
                      (cdr sequence))
            (value (if ,ivp ,initial-value ,what)
                   (funcall ,function ,what value)))
         ((= count ,end) value)))))


(defun reduce (function sequence &key from-end (start 0)
                        end (initial-value nil ivp) key)
  (unless end (setq end (length sequence)))
  (if (= end start)
      (if ivp initial-value (funcall function))
      (seq-dispatch
       sequence
       (if from-end
           (list-reduce-from-end  function sequence start end initial-value ivp key)
           (list-reduce function sequence start end initial-value ivp key))
       (let* ((disp (if from-end -1 1))
              (index (if from-end (1- end) start))
              (terminus (if from-end (1- start) end))
              (value (if ivp initial-value
                         (let ((elt (aref sequence index)))
                           (setq index (+ index disp))
                           (if key (funcall key elt) elt))))
              (element nil))
         (do* ()
           ((= index terminus) value)
           (setq element (aref sequence index)
                 index (+ index disp)
                 element (if key (funcall key element) element)
                 value (funcall function (if from-end element value) (if from-end value element))))))))


(autoload 'delete "delete.lisp")
(autoload 'delete-if "delete.lisp")
(autoload 'delete-if-not "delete.lisp")
(autoload 'remove "remove.lisp")
(autoload 'remove-if "remove.lisp")
(autoload 'remove-if-not "remove.lisp")

(autoload 'remove-duplicates "remove-duplicates.lisp")
(autoload 'delete-duplicates "delete-duplicates.lisp")

(autoload 'substitute "substitute.lisp")
(autoload 'substitute-if "substitute.lisp")
(autoload 'substitute-if-not "substitute.lisp")

(autoload 'nsubstitute "nsubstitute.lisp")
(autoload 'nsubstitute-if "nsubstitute.lisp")
(autoload 'nsubstitute-if-not "nsubstitute.lisp")

(defmacro vector-locater-macro (sequence body-form return-type)
  `(let ((incrementer (if from-end -1 1))
	 (start (if from-end (1- end) start))
	 (end (if from-end (1- start) end)))
     (do ((index start (+ index incrementer))
	  ,@(case return-type (:position nil) (:element '(current))))
       ((= index end) ())
       ,@(case return-type
	   (:position nil)
	   (:element `((setf current (aref ,sequence index)))))
       ,body-form)))

(defmacro locater-test-not (item sequence seq-type return-type)
  (let ((seq-ref (case return-type
		   (:position
		    (case seq-type
		      (:vector `(aref ,sequence index))
		      (:list `(pop ,sequence))))
		   (:element 'current)))
	(return (case return-type
		  (:position 'index)
		  (:element 'current))))
    `(if test-not
	 (if (not (funcall test-not ,item (apply-key key ,seq-ref)))
	     (return ,return))
	 (if (funcall test ,item (apply-key key ,seq-ref))
	     (return ,return)))))

(defmacro vector-locater (item sequence return-type)
  `(vector-locater-macro ,sequence
			 (locater-test-not ,item ,sequence :vector ,return-type)
			 ,return-type))

(defmacro locater-if-test (test sequence seq-type return-type sense)
  (let ((seq-ref (case return-type
		   (:position
		    (case seq-type
		      (:vector `(aref ,sequence index))
		      (:list `(pop ,sequence))))
		   (:element 'current)))
	(return (case return-type
		  (:position 'index)
		  (:element 'current))))
    (if sense
	`(if (funcall ,test (apply-key key ,seq-ref))
	     (return ,return))
	`(if (not (funcall ,test (apply-key key ,seq-ref)))
	     (return ,return)))))

(defmacro vector-locater-if-macro (test sequence return-type sense)
  `(vector-locater-macro ,sequence
			 (locater-if-test ,test ,sequence :vector ,return-type ,sense)
			 ,return-type))

(defmacro vector-locater-if (test sequence return-type)
  `(vector-locater-if-macro ,test ,sequence ,return-type t))

(defmacro vector-locater-if-not (test sequence return-type)
  `(vector-locater-if-macro ,test ,sequence ,return-type nil))

(defmacro list-locater-macro (sequence body-form return-type)
  `(if from-end
       (do ((sequence (nthcdr (- (length sequence) end)
			      (reverse ,sequence)))
	    (index (1- end) (1- index))
	    (terminus (1- start))
	    ,@(case return-type (:position nil) (:element '(current))))
         ((or (= index terminus) (null sequence)) ())
	 ,@(case return-type
	     (:position nil)
	     (:element `((setf current (pop ,sequence)))))
	 ,body-form)
       (do ((sequence (nthcdr start ,sequence))
	    (index start (1+ index))
	    ,@(case return-type (:position nil) (:element '(current))))
         ((or (= index end) (null sequence)) ())
	 ,@(case return-type
	     (:position nil)
	     (:element `((setf current (pop ,sequence)))))
	 ,body-form)))

(defmacro list-locater (item sequence return-type)
  `(list-locater-macro ,sequence
		       (locater-test-not ,item ,sequence :list ,return-type)
		       ,return-type))

(defmacro list-locater-if-macro (test sequence return-type sense)
  `(list-locater-macro ,sequence
		       (locater-if-test ,test ,sequence :list ,return-type ,sense)
		       ,return-type))

(defmacro list-locater-if (test sequence return-type)
  `(list-locater-if-macro ,test ,sequence ,return-type t))

(defmacro list-locater-if-not (test sequence return-type)
  `(list-locater-if-macro ,test ,sequence ,return-type nil))

(defmacro vector-position (item sequence)
  `(vector-locater ,item ,sequence :position))

(defmacro list-position (item sequence)
  `(list-locater ,item ,sequence :position))


(defun position (item sequence &key from-end (test #'eql) test-not (start 0)
                      end key)
  (seq-dispatch sequence
                (list-position* item sequence from-end test test-not start end key)
                (vector-position* item sequence from-end test test-not start end key)))


(defun list-position* (item sequence from-end test test-not start end key)
  (when (null end) (setf end (length sequence)))
  (list-position item sequence))

(defun vector-position* (item sequence from-end test test-not start end key)
  (when (null end) (setf end (length sequence)))
  (vector-position item sequence))

(defmacro vector-position-if (test sequence)
  `(vector-locater-if ,test ,sequence :position))

(defmacro list-position-if (test sequence)
  `(list-locater-if ,test ,sequence :position))

(defun position-if (test sequence &key from-end (start 0) key end)
  (let ((end (or end (length sequence))))
    (seq-dispatch sequence
		  (list-position-if test sequence)
		  (vector-position-if test sequence))))

(defmacro vector-position-if-not (test sequence)
  `(vector-locater-if-not ,test ,sequence :position))

(defmacro list-position-if-not (test sequence)
  `(list-locater-if-not ,test ,sequence :position))

(defun position-if-not (test sequence &key from-end (start 0) key end)
  (let ((end (or end (length sequence))))
    (seq-dispatch sequence
		  (list-position-if-not test sequence)
		  (vector-position-if-not test sequence))))

(defmacro vector-find (item sequence)
  `(vector-locater ,item ,sequence :element))

(defmacro list-find (item sequence)
  `(list-locater ,item ,sequence :element))

(defun find (item sequence &key from-end (test #'eql) test-not (start 0)
                  end key)
  (seq-dispatch sequence
                (list-find* item sequence from-end test test-not start end key)
                (vector-find* item sequence from-end test test-not start end key)))

(defun list-find* (item sequence from-end test test-not start end key)
  (when (null end) (setf end (length sequence)))
  (list-find item sequence))

(defun vector-find* (item sequence from-end test test-not start end key)
  (when (null end) (setf end (length sequence)))
  (vector-find item sequence))

(defmacro vector-find-if (test sequence)
  `(vector-locater-if ,test ,sequence :element))

(defmacro list-find-if (test sequence)
  `(list-locater-if ,test ,sequence :element))

(defun find-if (test sequence &key from-end (start 0) end key)
  (let ((end (or end (length sequence))))
    (seq-dispatch sequence
		  (list-find-if test sequence)
		  (vector-find-if test sequence))))

(defmacro vector-find-if-not (test sequence)
  `(vector-locater-if-not ,test ,sequence :element))

(defmacro list-find-if-not (test sequence)
  `(list-locater-if-not ,test ,sequence :element))

(defun find-if-not (test sequence &key from-end (start 0) end key)
  (let ((end (or end (length sequence))))
    (seq-dispatch sequence
		  (list-find-if-not test sequence)
		  (vector-find-if-not test sequence))))

(defmacro vector-count-if (not-p from-end-p predicate sequence)
  (let ((next-index (if from-end-p '(1- index) '(1+ index)))
        (pred `(funcall ,predicate (apply-key key (aref ,sequence index)))))
    `(let ((%start ,(if from-end-p '(1- end) 'start))
           (%end ,(if from-end-p '(1- start) 'end)))
       (do ((index %start ,next-index)
            (count 0))
         ((= index %end) count)
         (,(if not-p 'unless 'when) ,pred
           (setq count (1+ count)))))))

(defmacro list-count-if (not-p from-end-p predicate sequence)
  (let ((pred `(funcall ,predicate (apply-key key (pop sequence)))))
    `(let ((%start ,(if from-end-p '(- length end) 'start))
           (%end ,(if from-end-p '(- length start) 'end))
           (sequence ,(if from-end-p '(reverse sequence) 'sequence)))
       (do ((sequence (nthcdr %start ,sequence))
            (index %start (1+ index))
            (count 0))
         ((or (= index %end) (null sequence)) count)
         (,(if not-p 'unless 'when) ,pred
           (setq count (1+ count)))))))

(defun count (item sequence &key from-end (test #'eql test-p) (test-not nil test-not-p)
		   (start 0) end key)
  (when (and test-p test-not-p)
    (error "test and test-not both supplied"))
  (let* ((length (length sequence))
	 (end (or end length)))
    (let ((%test (if test-not-p
		     (lambda (x)
		       (not (funcall test-not item x)))
		     (lambda (x)
		       (funcall test item x)))))
      (seq-dispatch sequence
		    (if from-end
			(list-count-if nil t %test sequence)
			(list-count-if nil nil %test sequence))
		    (if from-end
			(vector-count-if nil t %test sequence)
			(vector-count-if nil nil %test sequence))))))

(defun count-if (test sequence &key from-end (start 0) end key)
  (let* ((length (length sequence))
	 (end (or end length)))
    (seq-dispatch sequence
		  (if from-end
		      (list-count-if nil t test sequence)
		      (list-count-if nil nil test sequence))
		  (if from-end
		      (vector-count-if nil t test sequence)
		      (vector-count-if nil nil test sequence)))))

(defun count-if-not (test sequence &key from-end (start 0) end key)
  (let* ((length (length sequence))
	 (end (or end length)))
    (seq-dispatch sequence
		  (if from-end
		      (list-count-if t t test sequence)
		      (list-count-if t nil test sequence))
		  (if from-end
		      (vector-count-if t t test sequence)
		      (vector-count-if t nil test sequence)))))


;;; MISMATCH (from ECL)

(defun call-test (test test-not item keyx)
  (cond (test (funcall test item keyx))
        (test-not (not (funcall test-not item keyx)))
        (t (eql item keyx))))

(defun test-error()
  (error "both test and test are supplied"))

(defun bad-seq-limit (x &optional y)
  (error "bad sequence limit ~a" (if y (list x y) x)))

(defmacro with-start-end (start end seq &body body)
  `(let* ((,start (if ,start (the-start ,start) 0))
          (,end (the-end ,end ,seq)))
     (unless (<= ,start ,end) (bad-seq-limit ,start ,end))
     ,@ body))

(defun the-end (x y)
  (cond ((fixnump x)
	 (unless (<= x (length y))
	   (bad-seq-limit x))
	 x)
	((null x)
	 (length y))
	(t (bad-seq-limit x))))

(defun the-start (x)
  (cond ((fixnump x)
	 (unless (>= x 0)
           (bad-seq-limit x))
	 x)
	((null x) 0)
	(t (bad-seq-limit x))))

(defun mismatch (sequence1 sequence2 &key from-end test test-not
                           (key #'identity) start1 start2 end1 end2)
  (and test test-not (test-error))
  (with-start-end
   start1 end1 sequence1
   (with-start-end
    start2 end2 sequence2
    (if (not from-end)
        (do ((i1 start1 (1+ i1))
             (i2 start2 (1+ i2)))
          ((or (>= i1 end1) (>= i2 end2))
           (if (and (>= i1 end1) (>= i2 end2)) nil i1))
          (unless (call-test test test-not
                             (funcall key (elt sequence1 i1))
                             (funcall key (elt sequence2 i2)))
            (return i1)))
        (do ((i1 (1- end1) (1- i1))
             (i2 (1- end2)  (1- i2)))
          ((or (< i1 start1) (< i2 start2))
           (if (and (< i1 start1) (< i2 start2)) nil (1+ i1)))
          (unless (call-test test test-not
                             (funcall key (elt sequence1 i1))
                             (funcall key (elt sequence2 i2)))
            (return (1+ i1))))))))


;; SEARCH (from CMUCL)

(defmacro compare-elements (elt1 elt2)
  `(if test-not
       (if (funcall test-not (apply-key key ,elt1) (apply-key key ,elt2))
           (return nil)
           t)
       (if (not (funcall test (apply-key key ,elt1) (apply-key key ,elt2)))
           (return nil)
           t)))


(defmacro search-compare-list-list (main sub)
  `(do ((main ,main (cdr main))
        (jndex start1 (1+ jndex))
        (sub (nthcdr start1 ,sub) (cdr sub)))
     ((or (null main) (null sub) (= end1 jndex))
      t)
     (compare-elements (car main) (car sub))))


(defmacro search-compare-list-vector (main sub)
  `(do ((main ,main (cdr main))
        (index start1 (1+ index)))
     ((or (null main) (= index end1)) t)
     (compare-elements (car main) (aref ,sub index))))


(defmacro search-compare-vector-list (main sub index)
  `(do ((sub (nthcdr start1 ,sub) (cdr sub))
        (jndex start1 (1+ jndex))
        (index ,index (1+ index)))
     ((or (= end1 jndex) (null sub)) t)
     (compare-elements (aref ,main index) (car sub))))


(defmacro search-compare-vector-vector (main sub index)
  `(do ((index ,index (1+ index))
        (sub-index start1 (1+ sub-index)))
     ((= sub-index end1) t)
     (compare-elements (aref ,main index) (aref ,sub sub-index))))


(defmacro search-compare (main-type main sub index)
  (if (eq main-type 'list)
      `(seq-dispatch ,sub
                     (search-compare-list-list ,main ,sub)
                     (search-compare-list-vector ,main ,sub))
      `(seq-dispatch ,sub
                     (search-compare-vector-list ,main ,sub ,index)
                     (search-compare-vector-vector ,main ,sub ,index))))


(defmacro list-search (main sub)
  `(do ((main (nthcdr start2 ,main) (cdr main))
        (index2 start2 (1+ index2))
        (terminus (- end2 (- end1 start1)))
        (last-match ()))
     ((> index2 terminus) last-match)
     (if (search-compare list main ,sub index2)
         (if from-end
             (setq last-match index2)
             (return index2)))))


(defmacro vector-search (main sub)
  `(do ((index2 start2 (1+ index2))
        (terminus (- end2 (- end1 start1)))
        (last-match ()))
     ((> index2 terminus) last-match)
     (if (search-compare vector ,main ,sub index2)
         (if from-end
             (setq last-match index2)
             (return index2)))))


(defun search (sequence1 sequence2 &key from-end (test #'eql) test-not
                         (start1 0) end1 (start2 0) end2 key)
  (let ((end1 (or end1 (length sequence1)))
	(end2 (or end2 (length sequence2))))
    (seq-dispatch sequence2
		  (list-search sequence2 sequence1)
		  (vector-search sequence2 sequence1))))
