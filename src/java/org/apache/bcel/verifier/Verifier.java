package org.apache.bcel.verifier;

/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" and
 *    "Apache BCEL" must not be used to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    "Apache BCEL", nor may "Apache" appear in their name, without
 *    prior written permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

import org.apache.bcel.*;
import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.*;
import org.apache.bcel.verifier.statics.*;
import org.apache.bcel.verifier.structurals.*;
import org.apache.bcel.verifier.exc.*;
import org.apache.bcel.verifier.exc.Utility; // Ambigous if not declared explicitely.
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * A Verifier instance is there to verify a class file according to The Java Virtual
 * Machine Specification, 2nd Edition.
 *
 * Pass-3b-verification includes pass-3a-verification;
 * pass-3a-verification includes pass-2-verification;
 * pass-2-verification includes pass-1-verification.
 *
 * A Verifier creates PassVerifier instances to perform the actual verification.
 *
 * @version $Id$
 * @author <A HREF="http://www.inf.fu-berlin.de/~ehaase"/>Enver Haase</A>
 * @see org.apache.bcel.verifier.PassVerifier
 */
public class Verifier{
	/**
	 * The name of the class this verifier operates on.
	 */
	private final String classname;

	/** A Pass1Verifier for this Verifier instance. */
	private Pass1Verifier p1v;
	/** A Pass2Verifier for this Verifier instance. */
	private Pass2Verifier p2v;
	/** The Pass3aVerifiers for this Verifier instance. Key: Interned string specifying the method number. */
	private HashMap p3avs = new HashMap();
	/** The Pass3bVerifiers for this Verifier instance. Key: Interned string specifying the method number. */
	private HashMap p3bvs = new HashMap();

	/** Returns the VerificationResult for the given pass. */
	public VerificationResult doPass1(){
		if (p1v == null){
			p1v = new Pass1Verifier(this);
		}
		return p1v.verify();
	}

	/** Returns the VerificationResult for the given pass. */
	public VerificationResult doPass2(){
		if (p2v == null){
			p2v = new Pass2Verifier(this);
		}
		return p2v.verify();
	}
		
	/** Returns the VerificationResult for the given pass. */
	public VerificationResult doPass3a(int method_no){
		String key = Integer.toString(method_no);
		Pass3aVerifier p3av;
		p3av = (Pass3aVerifier) (p3avs.get(key));
		if (p3avs.get(key) == null){
			p3av = new Pass3aVerifier(this, method_no);
			p3avs.put(key, p3av);
		}
		return p3av.verify();
	}

	/** Returns the VerificationResult for the given pass. */
	public VerificationResult doPass3b(int method_no){
		String key = Integer.toString(method_no);
		Pass3bVerifier p3bv;
		p3bv = (Pass3bVerifier) (p3bvs.get(key));
		if (p3bvs.get(key) == null){
			p3bv = new Pass3bVerifier(this, method_no);
			p3bvs.put(key, p3bv);
		}
		return p3bv.verify();
	}
	
	/**
	 * This class may not be no-args instantiated.
	 */
	private Verifier(){
		classname = ""; // never executed anyway, make compiler happy.
	}// not noargs-instantiable

	/**
	 * Instantiation is done by the VerifierFactory.
	 *
	 * @see VerifierFactory
	 */
	Verifier(String fully_qualified_classname){
		classname = fully_qualified_classname;
		flush();
	}

	/**
	 * Returns the name of the class this verifier operates on.
	 * This is particularly interesting when this verifier was created
	 * recursively by another Verifier and you got a reference to this
	 * Verifier by the getVerifiers() method of the VerifierFactory.
	 * @see VerifierFactory
	 */
	public final String getClassName(){
		return classname;
	}

	/**
	 * Forget everything known about the class file; that means, really
	 * start a new verification of a possibly different class file from
	 * BCEL's repository.
	 *
	 */
	public void flush(){
		p1v = null;
		p2v = null;
		p3avs.clear();
		p3bvs.clear();
	}

	/**
	 * This returns all the (warning) messages collected during verification.
	 * A prefix shows from which verifying pass a message originates.
	 */
	public String[] getMessages(){
		ArrayList messages = new ArrayList();

		if (p1v != null){
			String[] p1m = p1v.getMessages();
			for (int i=0; i<p1m.length; i++){
				messages.add("Pass 1: "+p1m[i]);
			}
		}
		if (p2v != null){
			String[] p2m = p2v.getMessages();
			for (int i=0; i<p2m.length; i++){
				messages.add("Pass 2: "+p2m[i]);
			}
		}
		Iterator p3as = p3avs.values().iterator();
		while (p3as.hasNext()){
			Pass3aVerifier pv = (Pass3aVerifier) p3as.next();
			String[] p3am = pv.getMessages();
			int meth = pv.getMethodNo();
			for (int i=0; i<p3am.length; i++){
				messages.add("Pass 3a, method "+meth+" ('"+Repository.lookupClass(classname).getMethods()[meth]+"'): "+p3am[i]);
			}
		}
		Iterator p3bs = p3bvs.values().iterator();
		while (p3bs.hasNext()){
			Pass3bVerifier pv = (Pass3bVerifier) p3bs.next();
			String[] p3bm = pv.getMessages();
			int meth = pv.getMethodNo();
			for (int i=0; i<p3bm.length; i++){
				messages.add("Pass 3b, method "+meth+" ('"+Repository.lookupClass(classname).getMethods()[meth]+"'): "+p3bm[i]);
			}
		}

		String[] ret = new String[messages.size()];
		for (int i=0; i< messages.size(); i++){
			ret[i] = (String) messages.get(i);
		}
		
		return ret;
	}

	/**
	 * Verifies class files.
	 * This is a simple demonstration of how the API of BCEL's
	 * class file verifier "JustIce" may be used.
	 * You should supply command-line arguments which are
	 * fully qualified namea of the classes to verify. These class files
	 * must be somewhere in your CLASSPATH (refer to Sun's
	 * documentation for questions about this) or you must have put the classes
	 * into the BCEL Repository yourself (via 'addClass(JavaClass)').
	 */
	public static void main(String [] args){
		System.out.println("JustIce by Enver Haase, (C) 2001. http://bcel.sourceforge.net\n");
	  for(int k=0; k < args.length; k++) {

			if (args[k].endsWith(".class")){
				int dotclasspos = args[k].lastIndexOf(".class");
				if (dotclasspos != -1) args[k] = args[k].substring(0,dotclasspos);
			}
		
			args[k] = args[k].replace('/', '.');
		
			System.out.println("Now verifiying: "+args[k]+"\n");

			Verifier v = VerifierFactory.getVerifier(args[k]);
			VerificationResult vr;
		
			vr = v.doPass1();
			System.out.println("Pass 1:\n"+vr);

			vr = v.doPass2();
			System.out.println("Pass 2:\n"+vr);

			if (vr == VerificationResult.VR_OK){
				JavaClass jc = Repository.lookupClass(args[k]);
				for (int i=0; i<jc.getMethods().length; i++){
					vr = v.doPass3a(i);
					System.out.println("Pass 3a, method "+i+" ['"+jc.getMethods()[i]+"']:\n"+vr);

					vr = v.doPass3b(i);
					System.out.println("Pass 3b, method number "+i+" ['"+jc.getMethods()[i]+"']:\n"+vr);
				}
			}
		
			System.out.println("Warnings:");
			String[] warnings = v.getMessages();
			if (warnings.length == 0) System.out.println("<none>");
			for (int j=0; j<warnings.length; j++){
				System.out.println(warnings[j]);
			}

			System.out.println("\n");
	  
			// avoid swapping.
	  	v.flush();
	  	Repository.clearCache();
			System.gc();
	  }
	}
}
