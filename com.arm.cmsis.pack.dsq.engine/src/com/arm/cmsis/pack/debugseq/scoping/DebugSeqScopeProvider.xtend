/*
 * generated by Xtext 2.10.0
 */
/*******************************************************************************
* Copyright (c) 2021 ARM Ltd. and others
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
* ARM Ltd and ARM Germany GmbH - Initial API and implementation
*******************************************************************************/

package com.arm.cmsis.pack.debugseq.scoping

import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EReference
import com.arm.cmsis.pack.debugseq.debugSeq.Expression
import com.arm.cmsis.pack.debugseq.debugSeq.DebugSeqPackage
import org.eclipse.xtext.scoping.IScope
import com.arm.cmsis.pack.debugseq.debugSeq.Sequence
import org.eclipse.xtext.scoping.Scopes
import java.util.List
import com.arm.cmsis.pack.debugseq.debugSeq.CodeBlock
import com.arm.cmsis.pack.debugseq.debugSeq.Block
import com.arm.cmsis.pack.debugseq.debugSeq.Control
import com.arm.cmsis.pack.debugseq.debugSeq.Statement
import com.arm.cmsis.pack.debugseq.debugSeq.VariableDeclaration
import com.arm.cmsis.pack.debugseq.debugSeq.DebugSeqModel

/**
 * This class contains custom scoping description.
 * 
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#scoping
 * on how and when to use it.
 */
class DebugSeqScopeProvider extends AbstractDebugSeqScopeProvider {
	
	override getScope(EObject context, EReference r) {
		if (context instanceof Expression
			&& r == DebugSeqPackage$Literals.VARIABLE_REF__VARIABLE) {
			context.eContainer.symbolsDefinedBefore(context)
		} else {
			super.getScope(context, r)
		}
	}
	
	def dispatch IScope symbolsDefinedBefore(EObject cont, EObject o) {
		cont.eContainer.symbolsDefinedBefore(o.eContainer)
	}
	
	def dispatch IScope symbolsDefinedBefore(DebugSeqModel dsm, EObject o) {
		Scopes.scopeFor(
			dsm.debugvars.statements.filter(typeof(VariableDeclaration))
		)
	}
	
	def dispatch IScope symbolsDefinedBefore(Sequence seq, EObject o) {
		Scopes.scopeFor(
			seq.codeblocks.blocksDeclaredBefore(o).map[it.statements.filter(typeof(VariableDeclaration))].flatten,
			seq.eContainer.symbolsDefinedBefore(o.eContainer)
		)
	}
	
	def dispatch IScope symbolsDefinedBefore(Block b, EObject o) {
		Scopes.scopeFor(
			b.statements.variablesDeclaredBefore(o),
			b.eContainer.symbolsDefinedBefore(o.eContainer)
		)
	}
	
	def dispatch IScope symbolsDefinedBefore(Control c, EObject o) {
		Scopes.scopeFor(
			c.codeblocks.blocksDeclaredBefore(o).map[it.statements.filter(typeof(VariableDeclaration))].flatten,
			c.eContainer.symbolsDefinedBefore(o.eContainer)
		)
	}
	
	def private blocksDeclaredBefore(List<CodeBlock> list, EObject o) {
		if (o instanceof Block || o instanceof Control) {
			list.subList(0, list.indexOf(o)).filter(typeof(Block))
		} else {
			newArrayList
		}
	}
	
	def private variablesDeclaredBefore(List<Statement> list, EObject o) {
		list.subList(0, list.indexOf(o)).filter(typeof(VariableDeclaration))
	}

}
