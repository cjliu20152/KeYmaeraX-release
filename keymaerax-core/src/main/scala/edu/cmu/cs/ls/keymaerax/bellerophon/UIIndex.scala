/**
  * Copyright (c) Carnegie Mellon University. CONFIDENTIAL
  * See LICENSE.txt for the conditions of this license.
  */
package edu.cmu.cs.ls.keymaerax.bellerophon

import java.lang.Number

import edu.cmu.cs.ls.keymaerax.btactics.Augmentors._
import edu.cmu.cs.ls.keymaerax.btactics.ExpressionTraversal.{ExpressionTraversalFunction, StopTraversal}
import edu.cmu.cs.ls.keymaerax.btactics.{DerivationInfo, ExpressionTraversal}
import edu.cmu.cs.ls.keymaerax.core._

/**
  * User-Interface Axiom/Tactic Index: Indexing data structure for all canonically applicable (derived) axioms/rules/tactics in User-Interface.
  * @author aplatzer
  * @see [[edu.cmu.cs.ls.keymaerax.btactics.AxiomIndex]]
  */
object UIIndex {
  //@todo import a debug flag as in Tactics.DEBUG
  private val DEBUG = System.getProperty("DEBUG", "true")=="true"

  /** Give the canonical (derived) axiom name or tactic names that simplifies the expression expr, optionally considering that this expression occurs at the indicated position pos in the given sequent. Disregard tactics that require input */
  def theStepAt(expr: Expression, pos: Option[Position] = None): Option[String] = expr match {
    case Box(Loop(_), _) => None //@note: [*] iterate caused user confusion, so avoid left-click step on loops
    case _ => allStepsAt(expr, pos).find(DerivationInfo(_).inputs.isEmpty)
  }



  def theStepAt(pos1: Position, pos2: Position, sequent: Sequent): Option[String] = allTwoPosSteps(pos1, pos2, sequent).
    find(DerivationInfo(_).inputs.isEmpty)

  private val unknown = Nil

  /** Return ordered list of all canonical (derived) axiom names or tactic names that simplifies the expression expr, optionally considering that this expression occurs at the indicated position pos in the given sequent. */
  def allStepsAt(expr: Expression, pos: Option[Position] = None, sequent: Option[Sequent] = None): List[String] = autoPad(pos, sequent, {
    val isTop = pos.nonEmpty && pos.get.isTopLevel
    //@note the truth-value of isAnte is nonsense if !isTop ....
    val isAnte = pos.nonEmpty && pos.get.isAnte
    //@todo cutL and cutR are always applicable on top level
    val alwaysApplicable = Nil
    if (DEBUG) println("allStepsAt(" + expr + ") at " + pos + " which " + (if (isTop) "is top" else "is not top") + " and " + (if (isAnte) "is ante" else "is succ"))
    expr match {
      case Differential(t) =>
        val tactics =
          t match {
            case _: Variable => "DvariableTactic" :: alwaysApplicable
            case _: Number => "c()' derive constant fn" :: alwaysApplicable
            // optimizations
            case t: Term if StaticSemantics.freeVars(t).isEmpty => "c()' derive constant fn" :: alwaysApplicable
            case _: Neg => "-' derive neg" :: alwaysApplicable
            case _: Plus => "+' derive sum" :: alwaysApplicable
            case _: Minus => "-' derive minus" :: alwaysApplicable
            // optimizations
            case Times(num, _) if StaticSemantics.freeVars(num).isEmpty => "' linear" :: alwaysApplicable
            case Times(_, num) if StaticSemantics.freeVars(num).isEmpty => "' linear right" :: alwaysApplicable
            case _: Times => "*' derive product" :: alwaysApplicable
            case _: Divide => "/' derive quotient" :: alwaysApplicable
            case _: Power => "^' derive power" :: alwaysApplicable
            case FuncOf(_, Nothing) => "c()' derive constant fn" :: alwaysApplicable
            case _ => alwaysApplicable
          }
        "derive" :: tactics

      case DifferentialFormula(f) =>
        val tactics =
          f match {
            case _: Equal => "=' derive =" :: alwaysApplicable
            case _: NotEqual => "!=' derive !=" :: alwaysApplicable
            case _: Greater => ">' derive >" :: alwaysApplicable
            case _: GreaterEqual => ">=' derive >=" :: alwaysApplicable
            case _: Less => "<' derive <" :: alwaysApplicable
            case _: LessEqual => "<=' derive <=" :: alwaysApplicable
            case _: And => "&' derive and" :: alwaysApplicable
            case _: Or => "|' derive or" :: alwaysApplicable
            case _: Imply => "->' derive imply" :: alwaysApplicable
            case _: Forall => "forall' derive forall" :: alwaysApplicable
            case _: Exists => "exists' derive exists" :: alwaysApplicable
            case _ => alwaysApplicable
          }
        "derive" :: tactics
      case Box(a, True) if isTop && !isAnte =>
        "dualFree" :: Nil

      case Box(a, post) =>
        val maybeSplit = post match {
          case _: And => "[] split" :: Nil
          case _ => Nil
        }
        def containsPrime = {
          var foundPrime = false
          ExpressionTraversal.traverse(new ExpressionTraversalFunction() {
            override def preF(p: PosInExpr, e: Formula): Either[Option[StopTraversal], Formula] = e match {
              case _: DifferentialFormula => foundPrime = true; Left(Some(ExpressionTraversal.stop))
              case _ => Left(None)
            }

            override def preT(p: PosInExpr, e: Term): Either[Option[StopTraversal], Term] = e match {
              case _: DifferentialSymbol => foundPrime = true; Left(Some(ExpressionTraversal.stop))
              case _ => Left(None)
            }
          }, post)
          foundPrime
        }
        val rules = "abstractionb" :: "generalizeb" :: maybeSplit
        a match {
          case _: Assign => "assignb" :: rules
          case _: AssignAny => "[:*] assign nondet" :: rules
          case _: DiffAssign => "[':=] differential assign" :: rules
          case _: Test => "[?] test" :: rules
          case _: Compose => "[;] compose" :: rules
          case _: Choice => "[++] choice" :: rules
          case _: Dual => ("[^d] dual" :: alwaysApplicable) ensuring (r => r.intersect(List("hideG", "V vacuous")).isEmpty, "unsound for hybrid games anyhow")
          case _: Loop => "loop" :: "[*] iterate" :: rules
          case ODESystem(ode, constraint) if containsPrime => ode match {
            case _: AtomicODE => "DE differential effect" :: "diffWeaken" :: "diffCut" :: rules
            case _: DifferentialProduct => "DE differential effect (system)" :: "diffWeaken" :: "diffCut" :: rules
            case _ => rules
          }
          case ODESystem(ode, constraint) =>
            val tactics: List[String] = /*@todo diffSolve once done*/ "autoDiffSolve" :: "diffCut" :: "diffInd" :: "DIRule" ::  Nil
            if (constraint == True)
              (tactics :+ "DG differential ghost") ++ rules
            else
              (tactics :+ "diffWeaken" :+ "DG differential ghost") ++ rules
          case _ => rules
        }

      case Diamond(a, post) => 
        val maybeSplit = post match {case _ : Or => "<> split" :: Nil case _ => Nil }
        val rules = alwaysApplicable ++ maybeSplit
        a match {
        case _: Assign => "<:=> assign" :: rules
        case _: AssignAny => "<:*> assign nondet" :: rules
        case _: Test => "<?> test" :: rules
        case _: Compose => "<;> compose" :: rules
        case _: Choice => "<++> choice" :: rules
        case _: Dual => "<^d> dual" :: rules
        case _: ODESystem => println("AxiomIndex for <ODE> still missing"); unknown
        case _ => rules
      }

      case Not(f) => f match {
        case Box(_, Not(_)) => "<> diamond" :: alwaysApplicable
        case _: Box => "![]" :: alwaysApplicable
        case Diamond(_, Not(_)) => "[] box" :: alwaysApplicable
        case _: Diamond => "!<>" :: alwaysApplicable
        case _: Forall => "!all" :: alwaysApplicable
        case _: Exists => "!exists" :: alwaysApplicable
        case _: Equal => "! =" :: alwaysApplicable
        case _: NotEqual => "! !=" :: alwaysApplicable
        case _: Less => "! <" :: alwaysApplicable
        case _: LessEqual => "! <=" :: alwaysApplicable
        case _: Greater => "! >" :: alwaysApplicable
        case _: GreaterEqual => "! >=" :: alwaysApplicable
        case _: Not => "!! double negation" :: alwaysApplicable
        case _: And => "!& deMorgan" :: alwaysApplicable
        case _: Or => "!| deMorgan" :: alwaysApplicable
        case _: Imply => "!-> deMorgan" :: alwaysApplicable
        case _: Equiv => "!<-> deMorgan" :: alwaysApplicable
        case _ => alwaysApplicable
      }

      case _ =>
        // Check for axioms vs. rules separately because sometimes we might want to apply these axioms when we don't
        // have positions available (which we need to check rule applicability
        val axioms =
          expr match {
            case (And(True, _)) => "true&" :: Nil
            case (And(_, True)) => "&true" :: Nil
            case (Imply(True, _)) => "true->" :: Nil
            case (Imply(_, True)) => "->true" :: Nil
              //@todo add true|, false&, and similar as new DerivedAxioms
            case _ => Nil
          }
        if (!isTop) axioms
        else {
          (expr, isAnte) match {
            case (True, false) => "closeTrue" :: alwaysApplicable
            case (False, true) => "closeFalse" :: alwaysApplicable
            case (_: Not, true) => "notL" :: alwaysApplicable
            case (_: Not, false) => "notR" :: alwaysApplicable
            case (_: And, true) => axioms ++ ("andL" :: alwaysApplicable)
            case (_: And, false) => axioms ++ ("andR" :: alwaysApplicable)
            case (_: Or, true) => "orL" :: alwaysApplicable
            case (_: Or, false) => "orR" :: alwaysApplicable
            case (_: Imply, true) => axioms ++ ("implyL" :: alwaysApplicable)
            case (_: Imply, false) => axioms ++ ("implyR" :: alwaysApplicable)
            case (_: Equiv, true) => "equivL" :: alwaysApplicable
            case (_: Equiv, false) => "equivR" :: alwaysApplicable
            case (_: Forall, true) => "allL" :: alwaysApplicable
            case (_: Forall, false) => "allR" :: alwaysApplicable
            case (_: Exists, true) => "existsL" :: alwaysApplicable
            case (_: Exists, false) => "existsR" :: alwaysApplicable
            case _ => alwaysApplicable
          }
        }
    }
  })

  def allTwoPosSteps(pos1: Position, pos2: Position, sequent: Sequent): List[String] = {
    val expr1 = sequent.sub(pos1)
    val expr2 = sequent.sub(pos2)
    (pos1, pos2, expr1, expr2) match {
      case (p1: AntePosition, p2: SuccPosition, Some(e1), Some(e2)) if p1.isTopLevel &&  p2.isTopLevel && e1 == e2 => "closeId" :: Nil
      case (p1: AntePosition, p2: SuccPosition, Some(e1), Some(e2)) if p1.isTopLevel && !p2.isTopLevel && e1 == e2 => /*@todo "knownR" ::*/ Nil
      case (_, _, Some(Equal(_, _)), _) => "L2R" :: Nil
      case (_, _: AntePosition, Some(_: Term), Some(_: Forall)) => /*@todo "all instantiate pos" ::*/ Nil
      case (_, _: SuccPosition, Some(_: Term), Some(_: Exists)) => /*@todo "exists instantiate pos" ::*/ Nil
      case _ => Nil
      //@todo more drag-and-drop support
    }
  }

  def comfortOf(stepName: String): Option[String] = stepName match {
    case "diffCut" => Some("diffInvariant")
    case "DIRule" => Some("autoDIRule")
    case "diffInd" => Some("autoDiffInd")
    case "diffSolve" => Some("autoDiffSolve")
    case _ => None
  }

  private def autoPad(pos: Option[Position], sequent: Option[Sequent], axioms: List[String]): List[String] = {
    //@note don't augment with hide since UI has a special button for it already.
    //@note don't augment with cutL+cutR since too cluttered.
    //    if (!axioms.isEmpty && pos.isDefined && pos.get.isTopLevel)
    //      axioms ++ (if (pos.get.isAnte) "hideL" :: /*"cutL" ::*/ Nil else "hideR" :: /*"cutR" ::*/ Nil)
    //    else
    if (DEBUG) println("allStepsAt=" + axioms)
    axioms
  }
}
