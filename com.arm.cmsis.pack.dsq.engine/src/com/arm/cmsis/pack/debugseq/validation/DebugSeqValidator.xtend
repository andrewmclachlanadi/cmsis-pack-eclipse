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

package com.arm.cmsis.pack.debugseq.validation

import static extension org.eclipse.xtext.EcoreUtil2.*
import static extension com.arm.cmsis.pack.debugseq.util.DebugSeqUtil.*

import java.util.Set
import java.util.HashSet
import com.google.inject.Inject
import com.arm.cmsis.pack.debugseq.typing.DebugSeqType
import com.arm.cmsis.pack.debugseq.typing.DebugSeqTypeProvider
import org.eclipse.emf.ecore.EReference
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.validation.Check
import com.arm.cmsis.pack.debugseq.debugSeq.Sequence
import com.arm.cmsis.pack.debugseq.debugSeq.DebugSeqPackage
import com.arm.cmsis.pack.debugseq.debugSeq.VariableDeclaration
import com.arm.cmsis.pack.debugseq.debugSeq.Assignment
import com.arm.cmsis.pack.debugseq.debugSeq.VariableRef
import com.arm.cmsis.pack.debugseq.debugSeq.Expression
import com.arm.cmsis.pack.debugseq.debugSeq.Not
import com.arm.cmsis.pack.debugseq.debugSeq.Or
import com.arm.cmsis.pack.debugseq.debugSeq.And
import com.arm.cmsis.pack.debugseq.debugSeq.Plus
import com.arm.cmsis.pack.debugseq.debugSeq.Minus
import com.arm.cmsis.pack.debugseq.debugSeq.Mul
import com.arm.cmsis.pack.debugseq.debugSeq.Div
import com.arm.cmsis.pack.debugseq.debugSeq.Equality
import com.arm.cmsis.pack.debugseq.debugSeq.Comparison
import com.arm.cmsis.pack.debugseq.debugSeq.BitOr
import com.arm.cmsis.pack.debugseq.debugSeq.BitXor
import com.arm.cmsis.pack.debugseq.debugSeq.BitAnd
import com.arm.cmsis.pack.debugseq.debugSeq.Shift
import com.arm.cmsis.pack.debugseq.debugSeq.Query
import com.arm.cmsis.pack.debugseq.debugSeq.Read8
import com.arm.cmsis.pack.debugseq.debugSeq.Read16
import com.arm.cmsis.pack.debugseq.debugSeq.Read32
import com.arm.cmsis.pack.debugseq.debugSeq.Read64
import com.arm.cmsis.pack.debugseq.debugSeq.ReadAP
import com.arm.cmsis.pack.debugseq.debugSeq.ReadDP
import com.arm.cmsis.pack.debugseq.debugSeq.Write8
import com.arm.cmsis.pack.debugseq.debugSeq.Write16
import com.arm.cmsis.pack.debugseq.debugSeq.Write32
import com.arm.cmsis.pack.debugseq.debugSeq.Write64
import com.arm.cmsis.pack.debugseq.debugSeq.WriteAP
import com.arm.cmsis.pack.debugseq.debugSeq.WriteDP
import com.arm.cmsis.pack.debugseq.debugSeq.DapDelay
import com.arm.cmsis.pack.debugseq.debugSeq.DapWriteABORT
import com.arm.cmsis.pack.debugseq.debugSeq.DapSwjPins
import com.arm.cmsis.pack.debugseq.debugSeq.DapSwjClock
import com.arm.cmsis.pack.debugseq.debugSeq.DapSwjSequence
import com.arm.cmsis.pack.debugseq.debugSeq.DapJtagSequence
import com.arm.cmsis.pack.debugseq.debugSeq.SequenceCall
import com.arm.cmsis.pack.debugseq.debugSeq.Block
import com.arm.cmsis.pack.debugseq.debugSeq.Statement
import com.arm.cmsis.pack.debugseq.debugSeq.QueryValue
import com.arm.cmsis.pack.debugseq.debugSeq.LoadDebugInfo
import com.arm.cmsis.pack.debugseq.debugSeq.Message
import com.arm.cmsis.pack.debugseq.debugSeq.Ternary

/**
 * This class contains custom validation rules. 
 *
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation
 */
class DebugSeqValidator extends AbstractDebugSeqValidator {
	
	public static val DUPLICATE_ELEMENT =
		"com.arm.cmsis.pack.dsq.DuplicateElement"
	
	public static val SEQUENCE_UNDEFINED =
		"com.arm.cmsis.pack.dsq.SequenceUndefined"
	
	public static val ASSIGNMENT_ERROR =
		"com.arm.cmsis.pack.dsq.AssignmentError"
	
	public static val WRONG_TYPE =
		"com.arm.cmsis.pack.dsq.WrongType"
		
	public static val SEQUENCE_CALL_IN_ATOMIC_BLOCK =
		"com.arm.cmsis.pack.dsq.SequenceCallInAtomicBlock"
	
	public static val QUERY_CALL_IN_ATOMIC_BLOCK =
		"com.arm.cmsis.pack.dsq.QueryCallInAtomicBlock"
	
	public static val MUST_IN_ATOMIC_BLOCK =
		"com.arm.cmsis.pack.dsq.MustInAtomicBlock"
	
	public static val NESTED_COMMAND_IN_ATOMIC_BLOCK =
		"com.arm.cmsis.pack.dsq.NestedCommandInAtomicBlock"
	
	@Inject extension DebugSeqTypeProvider
	
//	def private checkExpectedBoolean(Expression exp, EReference reference) {
//		checkExpectedType(exp, DebugSeqTypeProvider::boolType, reference)
//	}
	
	def private checkExpectedInteger(Expression exp, EReference reference) {
		checkExpectedType(exp, DebugSeqTypeProvider::intType, reference)
	}
	
	def private checkExpectedType(Expression exp,
		DebugSeqType expectedType, EReference reference) {
		val actualType = getTypeAndCheckNotNull(exp, reference)
		if (actualType != expectedType) {
			error("expected " + expectedType + " type, but was " + actualType,
				reference, WRONG_TYPE
			)
		}
	}
	
	def private DebugSeqType getTypeAndCheckNotNull(Expression exp,
		EReference reference) {
		var type = exp?.typeFor
		if (type == null)
			error("null type", reference, WRONG_TYPE)
		return type;
	}
	
	def private checkExpectedSame(DebugSeqType left, DebugSeqType right) {
		if (right != null && left != null && right != left) {
			error("expected the same type, but was " + left + ", " + right,
					DebugSeqPackage$Literals::EQUALITY.EIDAttribute,
					WRONG_TYPE)
		}
	}
	
	@Check def checkNot(Not not) {
		checkExpectedInteger(not.expression,
			DebugSeqPackage$Literals::NOT__EXPRESSION)
	}
	
	@Check def checkAssignment(Assignment assign) {
		if (!(assign.left instanceof VariableRef)) {
			error("The left hand side of assignment expression must be a variable",
				DebugSeqPackage::eINSTANCE.assignment_Left,
				ASSIGNMENT_ERROR
			)
			return
		}
		
		val varname = (assign.left as VariableRef).variable.name
		if (varname == "__protocol" ||
			varname == "__connection" ||
			varname == "__traceout") {
			error("Read-only variable '" + varname + "' cannot be modified",
				DebugSeqPackage::eINSTANCE.assignment_Left,
				ASSIGNMENT_ERROR
			)
			return
		}

		checkExpectedInteger(assign.right,
			DebugSeqPackage$Literals::ASSIGNMENT__RIGHT)
	}
	
	@Check def checkTernary(Ternary ternary) {
		checkExpectedSame(ternary.exp1.typeFor, ternary.exp2.typeFor)
	}
	
	@Check def checkOr(Or or) {
		checkExpectedInteger(or.left,
			DebugSeqPackage$Literals::OR__LEFT)
		checkExpectedInteger(or.right,
			DebugSeqPackage$Literals::OR__RIGHT)
	}
	
	@Check def checkAnd(And and) {
		checkExpectedInteger(and.left,
			DebugSeqPackage$Literals::AND__LEFT)
		checkExpectedInteger(and.right,
			DebugSeqPackage$Literals::AND__RIGHT)
	}
	
	@Check def checkBitOr(BitOr bitOr) {
		checkExpectedInteger(bitOr.left,
			DebugSeqPackage$Literals::BIT_OR__LEFT)
		checkExpectedInteger(bitOr.right,
			DebugSeqPackage$Literals::BIT_OR__RIGHT)
	}
	
	@Check def checkBitXor(BitXor bitXor) {
		checkExpectedInteger(bitXor.left,
			DebugSeqPackage$Literals::BIT_XOR__LEFT)
		checkExpectedInteger(bitXor.right,
			DebugSeqPackage$Literals::BIT_XOR__RIGHT)
	}
	
	@Check def checkBitAnd(BitAnd bitAnd) {
		checkExpectedInteger(bitAnd.left,
			DebugSeqPackage$Literals::BIT_AND__LEFT)
		checkExpectedInteger(bitAnd.right,
			DebugSeqPackage$Literals::BIT_AND__RIGHT)
	}
	
	@Check def checkShift(Shift shift) {
		checkExpectedInteger(shift.left,
			DebugSeqPackage$Literals::SHIFT__LEFT)
		checkExpectedInteger(shift.right,
			DebugSeqPackage$Literals::SHIFT__RIGHT)
	}
	
	@Check def checkPlus(Plus plus) {
		checkExpectedInteger(plus.left,
			DebugSeqPackage$Literals::PLUS__LEFT)
		checkExpectedInteger(plus.right,
			DebugSeqPackage$Literals::PLUS__RIGHT)
	}
	
	@Check def checkMinus(Minus minus) {
		checkExpectedInteger(minus.left,
			DebugSeqPackage$Literals::MINUS__LEFT)
		checkExpectedInteger(minus.right,
			DebugSeqPackage$Literals::MINUS__RIGHT)
	}
	
	@Check def checkMultiply(Mul mul) {
		checkExpectedInteger(mul.left,
			DebugSeqPackage$Literals::MUL__LEFT)
		checkExpectedInteger(mul.right,
			DebugSeqPackage$Literals::MUL__RIGHT)
	}
	
	@Check def checkDivide(Div div) {
		checkExpectedInteger(div.left,
			DebugSeqPackage$Literals::DIV__LEFT)
		checkExpectedInteger(div.right,
			DebugSeqPackage$Literals::DIV__RIGHT)
	}
	
	@Check def checkEquality(Equality equality) {
		val leftType = getTypeAndCheckNotNull(equality.left,
			DebugSeqPackage$Literals::EQUALITY__LEFT)
		val rightType = getTypeAndCheckNotNull(equality.right,
			DebugSeqPackage$Literals::EQUALITY__RIGHT)
		checkExpectedSame(leftType, rightType)
	}
	
	@Check def checkComparison(Comparison comparison) {
		val leftType = getTypeAndCheckNotNull(comparison.left,
				DebugSeqPackage$Literals::COMPARISON__LEFT)
		val rightType = getTypeAndCheckNotNull(comparison.right,
				DebugSeqPackage$Literals::COMPARISON__RIGHT)
		checkExpectedSame(leftType, rightType)
	}
	
	@Check def checkQuery(Query query) {
		checkExpectedInteger(query.type,
			DebugSeqPackage$Literals::QUERY__TYPE)
		checkExpectedInteger(query.^default,
			DebugSeqPackage$Literals::QUERY__DEFAULT)
		
		val block = query.containingBlock
		if (block.atomic !== 0) {
			error("Calling a query function in an atomic block",
				query,
				null,
				QUERY_CALL_IN_ATOMIC_BLOCK
			)
		}
	}
	
	@Check def checkQueryValue(QueryValue query) {
		checkExpectedInteger(query.^default,
			DebugSeqPackage$Literals::QUERY__DEFAULT)
		
		val block = query.containingBlock
		if (block.atomic !== 0) {
			error("Calling a query function in an atomic block",
				query,
				null,
				QUERY_CALL_IN_ATOMIC_BLOCK
			)
		}
	}
	
	@Check def checkSequenceCall(SequenceCall seqCall) {
		val name = seqCall.seqname
		val sequence = seqCall.containingSequences.sequences.
			findFirst[it.name == name]
		if (sequence == null && !name.isDefaultSequence) {
			error("Calling an undefined sequence '" + name + "'",
				DebugSeqPackage::eINSTANCE.sequenceCall_Seqname,
				SEQUENCE_UNDEFINED
			)
		}
		
		val block = seqCall.containingBlock
		if (block.atomic !== 0) {
			error("Calling sequence '" + name + "' in an atomic block",
				DebugSeqPackage::eINSTANCE.sequenceCall_Seqname,
				SEQUENCE_CALL_IN_ATOMIC_BLOCK
			)
		}
	}
	
	@Check def checkRead8(Read8 read) {
		checkExpectedInteger(read.addr,
			DebugSeqPackage$Literals::READ8__ADDR)
	}
	
	@Check def checkRead16(Read16 read) {
		checkExpectedInteger(read.addr,
			DebugSeqPackage$Literals::READ16__ADDR)
	}
	
	@Check def checkRead32(Read32 read) {
		checkExpectedInteger(read.addr,
			DebugSeqPackage$Literals::READ32__ADDR)
	}
	
	@Check def checkRead64(Read64 read) {
		checkExpectedInteger(read.addr,
			DebugSeqPackage$Literals::READ64__ADDR)
	}
	
	@Check def checkReadAP(ReadAP read) {
		checkExpectedInteger(read.addr,
			DebugSeqPackage$Literals::READ_AP__ADDR)
	}
	
	@Check def checkReadDP(ReadDP read) {
		checkExpectedInteger(read.addr,
			DebugSeqPackage$Literals::READ_DP__ADDR)
	}
	
	@Check def checkWrite8(Write8 write) {
		checkExpectedInteger(write.addr,
			DebugSeqPackage$Literals::WRITE8__ADDR)
		checkExpectedInteger(write.^val,
			DebugSeqPackage$Literals::WRITE8__VAL)
	}
	
	@Check def checkWrite16(Write16 write) {
		checkExpectedInteger(write.addr,
			DebugSeqPackage$Literals::WRITE16__ADDR)
		checkExpectedInteger(write.^val,
			DebugSeqPackage$Literals::WRITE16__VAL)
	}
	
	@Check def checkWrite32(Write32 write) {
		checkExpectedInteger(write.addr,
			DebugSeqPackage$Literals::WRITE32__ADDR)
		checkExpectedInteger(write.^val,
			DebugSeqPackage$Literals::WRITE32__VAL)
	}
	
	@Check def checkWrite64(Write64 write) {
		checkExpectedInteger(write.addr,
			DebugSeqPackage$Literals::WRITE64__ADDR)
		checkExpectedInteger(write.^val,
			DebugSeqPackage$Literals::WRITE64__VAL)
	}
	
	@Check def checkWriteAP(WriteAP write) {
		checkExpectedInteger(write.addr,
			DebugSeqPackage$Literals::WRITE_AP__ADDR)
		checkExpectedInteger(write.^val,
			DebugSeqPackage$Literals::WRITE_AP__VAL)
	}
	
	@Check def checkWriteDP(WriteDP write) {
		checkExpectedInteger(write.addr,
			DebugSeqPackage$Literals::WRITE_DP__ADDR)
		checkExpectedInteger(write.^val,
			DebugSeqPackage$Literals::WRITE_DP__VAL)
	}
	
	@Check def checkDapDelay(DapDelay dap) {
		checkExpectedInteger(dap.delay,
			DebugSeqPackage$Literals::DAP_DELAY__DELAY)
	}
	
	@Check def checkDapWriteABORT(DapWriteABORT dap) {
		checkExpectedInteger(dap.value,
			DebugSeqPackage$Literals::DAP_WRITE_ABORT__VALUE)
	}
	
	@Check def checkDapSwjPins(DapSwjPins dap) {
		checkExpectedInteger(dap.pinout,
			DebugSeqPackage$Literals::DAP_SWJ_PINS__PINOUT)
		checkExpectedInteger(dap.pinselect,
			DebugSeqPackage$Literals::DAP_SWJ_PINS__PINSELECT)
		checkExpectedInteger(dap.pinwait,
			DebugSeqPackage$Literals::DAP_SWJ_PINS__PINWAIT)
	}
	
	@Check def checkDapSwjClock(DapSwjClock dap) {
		checkExpectedInteger(dap.value,
			DebugSeqPackage$Literals::DAP_SWJ_CLOCK__VALUE)
	}
	
	@Check def checkDapSwjSequence(DapSwjSequence dap) {
		checkExpectedInteger(dap.cnt,
			DebugSeqPackage$Literals::DAP_SWJ_SEQUENCE__CNT)
		checkExpectedInteger(dap.^val,
			DebugSeqPackage$Literals::DAP_SWJ_SEQUENCE__VAL)
		
		// DAP_SWJ_Sequence commands must be encapsulated in an atomic block to ensure correct execution.
		val block = dap.containingBlock
		if (block.atomic !== 1) {
			error("DAP_SWJ_Sequence commands must be encapsulated in an atomic block to ensure correct execution",
				DebugSeqPackage::eINSTANCE.dapSwjSequence.EIDAttribute,
				MUST_IN_ATOMIC_BLOCK
			)
		}
	}
	
	@Check def checkDapJtagSequence(DapJtagSequence dap) {
		checkExpectedInteger(dap.cnt,
			DebugSeqPackage$Literals::DAP_JTAG_SEQUENCE__CNT)
		checkExpectedInteger(dap.tms,
			DebugSeqPackage$Literals::DAP_JTAG_SEQUENCE__TMS)
		checkExpectedInteger(dap.tdi,
			DebugSeqPackage$Literals::DAP_JTAG_SEQUENCE__TDI)
	}
	
	@Check def checkNoDuplicateSequence(Sequence seq) {
		if (seq.containingSequences.sequences.exists[
			it != seq && it.name == seq.name && it.pname == seq.pname]) {
			error("Duplicate sequence '" + seq.name + "'",
				DebugSeqPackage::eINSTANCE.sequence_Name,
				DUPLICATE_ELEMENT
			)
		}
	}
	
	@Check def checkNoDuplicateVariableDeclaration(VariableDeclaration vardecl) {
		val dsm = vardecl.containingDebugSeqModel
		val globalDeplicate = dsm.debugvars.statements.filter(typeof(VariableDeclaration)).
			findFirst[it != vardecl && it.name == vardecl.name]
		if (globalDeplicate != null)
			error("Duplicate variable declaration '" + vardecl.name + "'",
				DebugSeqPackage::eINSTANCE.variableDeclaration_Name,
				DUPLICATE_ELEMENT
			)
		
		val localDuplicate = vardecl.containingSequence.getAllContentsOfType(typeof(VariableDeclaration)).
			findFirst[it != vardecl && it.name == vardecl.name &&
				(it.containingControl == null ||
					it.containingControl.isAncestor(vardecl.containingControl))] // this variable has been declared
		if (localDuplicate != null) {
			error("Duplicate variable declaration '" + vardecl.name + "'",
				DebugSeqPackage::eINSTANCE.variableDeclaration_Name,
				DUPLICATE_ELEMENT
			)
		}
	}
	
	private static class HelperStruct {
		Set<String> varsInAtomicBlock = new HashSet<String>
		int usedCommands = 0
		
		new() {}
	}
	
	@Check def checkAtomicBlock(Block block) {
		if (block.atomic === 1) {
			val helper = new HelperStruct
			if (!block.checkStatement(helper)) {
				error("Nested command execution is not allowed in an atomic block",
					DebugSeqPackage::eINSTANCE.block.EIDAttribute,
					NESTED_COMMAND_IN_ATOMIC_BLOCK
				)
			}
		}
	}
	
	def private boolean checkStatement(EObject parent, HelperStruct helper) {
		val statements = parent.eContents.filter(typeof(Statement))
		if (statements === null || statements.empty) {
			return true
		}
		val check = statements.map[
			switch(it) {
				VariableDeclaration: {
					val usedCommands = helper.usedCommands
					if (!it.checkStatement(helper)) {
						return false
					}
					// if there is command used in the 'value' expression
					if (usedCommands < helper.usedCommands) {
						helper.varsInAtomicBlock.add(it.name)
					}
					return true
				}
				VariableRef: {
					// if this variable reference is the left expression of an assignment expression, don't check
					if (it.eContainer instanceof Assignment && it === (it.eContainer as Assignment).left) {
						return true
					}
					val varName = it.variable.name
					if (helper.varsInAtomicBlock.contains(varName)) {
						helper.usedCommands++
					}
					return true
				}
				Assignment: {
					val varName = (it.left as VariableRef).variable.name
					val usedCommands = helper.usedCommands
					if (!it.checkStatement(helper)) {
						return false
					}
					// if there is commands used in the 'right' expression
					if (usedCommands < helper.usedCommands) {
						helper.varsInAtomicBlock.add(varName)
					} else {
						helper.varsInAtomicBlock.remove(varName)
					}
					return true
				}
				LoadDebugInfo, Message,
				Read8, Read16, Read32, Read64, ReadAP, ReadDP,
				Write8, Write16, Write32, Write64, WriteAP, WriteDP,
				DapDelay,
				DapWriteABORT,
				DapSwjPins,
				DapSwjClock,
				DapSwjSequence,
				DapJtagSequence: it.checkCommand(helper)
				default: {
					return it.checkStatement(helper)
				}
			}
		].findFirst[it === false]
		
		return if (check === null) true else false
	}
	
	def private boolean checkCommand(Expression command, HelperStruct helper) {
		val usedCommands = helper.usedCommands
		val check = command.checkStatement(helper)
		if (usedCommands < helper.usedCommands) { // if there is nested command
			return false
		}
		
		// increment for this command
		helper.usedCommands++
		return check
	}
	
}
