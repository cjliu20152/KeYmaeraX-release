package edu.cmu.cs.ls.keymaerax.bellerophon

import edu.cmu.cs.ls.keymaerax.core.{Formula, SeqPos, Sequent, Provable}
import edu.cmu.cs.ls.keymaerax.pt.ProofTerm

/**
 * Algebraic Data Type whose elements are well-formed Bellephoron expressions.
 * See Table 1 of "Bellerophon: A Typed Language for Automated Deduction in a Uniform Substitution Calculus"
 * @author Nathan Fulton
 */
abstract class BelleExpr {
  // Syntactic sugar for cominators.
  def &(other: BelleExpr)      = SeqTactic(this, other)
  def |(other: BelleExpr)      = EitherTactic(this, other)
  def *(annotation: BelleType) = SaturateTactic(this, annotation)

  /** @todo This should have a formal specification.*/
  final val belleType : BelleType = this match {
    case builtIn: BuiltInTactic => builtIn.belleTypeAnnotation
    case builtIn: BuiltInPosTactic => builtIn.belleTypeAnnotation
    case SeqTactic(l,r)  => (l, l.belleType, r, r.belleType) match {
      case (TAbs(lv2, lBody), ForAllType(lv, leftType), TAbs(rv2, rBody), ForAllType(rv, rightType)) if(lv == lv2 && rv == rv2) =>
        ForAllType(lv, (lBody & rBody).belleType)
      case (left, leftType: ProvableMapping, right, rightType: ProvableMapping) =>
        ProvableMapping(leftType.dom, rightType.cod)
      case (left, leftType : SequentMapping, right, rightType : SequentMapping) =>
        SequentMapping(leftType.dom, rightType.cod)
      case _ => throw BelleError(s"Cannot synthesize a sequential type for ${l.belleType} and ${r.belleType}")
    }
  }
}

abstract case class BuiltInTactic(name: String) extends BelleExpr {
  def apply(provable : Provable) : Provable
  val belleTypeAnnotation : BelleType
}

abstract case class BuiltInPosTactic(name: String) extends BelleExpr {
  def apply(provable: Provable, pos: SeqPos) : Provable
  val belleTypeAnnotation : BelleType
}

case class SeqTactic(left: BelleExpr, right: BelleExpr) extends BelleExpr
case class EitherTactic(left: BelleExpr, right: BelleExpr) extends BelleExpr
//case class ParallelTactic(left: BelleExpr, right: BelleExpr) extends BelleExpr
case class ExactIterTactic(child: BelleExpr, count: Int) extends BelleExpr
case class SaturateTactic(child: BelleExpr, annotation: BelleType) extends BelleExpr
case class BranchTactic(children: Seq[BelleExpr]) extends BelleExpr
case class OptionalTactic(child: BelleExpr) extends BelleExpr
case class USubstPatternTactic(options: Seq[(BelleType, BelleExpr)]) extends BelleExpr

case class TAbs(t: TypeVar, body: BelleExpr) extends BelleExpr
case class TApp(fn : TAbs, arg: BelleType) extends BelleExpr

case class SumProjection(left: BelleExpr, right: BelleExpr) extends BelleExpr

/** @todo eisegesis */
case class Map(e: BelleExpr) extends BelleExpr

abstract trait BelleValue
case class BelleProvable(p : Provable) extends BelleExpr with BelleValue
case class LeftResult(v: BelleValue) extends BelleExpr with BelleValue
case class RightResult(v: BelleValue) extends BelleExpr  with BelleValue

abstract class BelleType

case class TypeVar(name: String) extends BelleType
case class ForAllType(v: TypeVar, body: BelleType) extends BelleType
case class ApplyType(abs: ForAllType, body: BelleType) extends BelleType

case class EitherType(left: BelleType, right: BelleType) extends BelleType
case class FormulaType(f: Formula) extends BelleType
///** @todo Need types for positional tactics. This is one proposal. */
//case class Formula(dom: FormulaType, cod: FormulaType) extends BelleType with MappingType

case class SequentType(ante: Seq[BelleType], succ: Seq[BelleType]) extends BelleType
case class SequentMapping(val dom: SequentType, val cod: BelleType) extends BelleType

case class ProvableType(sequents: Seq[SequentType]) extends BelleType
case class ProvableMapping(val dom: ProvableType, val cod: BelleType) extends BelleType

case class BelleClosedProofType() extends BelleType

case class BelleError(message: String)
  extends Exception(s"[Bellerophon Runtime] $message")

class CompoundException(left: BelleError, right: BelleError)
  extends BelleError(s"Left Message: ${left.getMessage}\nRight Message: ${right.getMessage})")