/**
 * Copyright (c) Carnegie Mellon University. CONFIDENTIAL
 * See LICENSE.txt for the conditions of this license.
 */
package edu.cmu.cs.ls.keymaerax.btactics

import edu.cmu.cs.ls.keymaerax.bellerophon.{RenUSubst, BelleExpr, PosInExpr, SuccPosition}
import edu.cmu.cs.ls.keymaerax.btactics.TactixLibrary._
import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.lemma.LemmaDBFactory
import edu.cmu.cs.ls.keymaerax.parser.StringConverter._
import edu.cmu.cs.ls.keymaerax.tools.{DiffSolutionTool, ToolEvidence}

import scala.annotation.switch
import scala.collection.immutable
import scala.collection.immutable._

/**
 * Derived Axioms.
  *
  * @author Andre Platzer
 * @see [[edu.cmu.cs.ls.keymaerax.core.AxiomBase]]
  * @note To simplify bootstrap and avoid dependency management, the proofs of the derived axioms are
  *       written with explicit reference to other scala-objects representing provables (which will be proved on demand)
  *       as opposed to by referring to the names, which needs a map canonicalName->tacticOnDemand.
 */
object DerivedAxioms {

  /** Database for derived axioms */
  val derivedAxiomDB = LemmaDBFactory.lemmaDB
  /*@note must be initialized from outside; is var so that unit tests can setup/tear down */
  implicit var qeTool: QETool with DiffSolutionTool = null
  type LemmaID = String

  /** A Provable proving the derived axiom/rule named id (convenience) */
  def derivedAxiomOrRule(name: String): Provable = {
    val lemmaName = DerivationInfo(name).codeName
    require(derivedAxiomDB.contains(lemmaName), "Lemma " + lemmaName + " has already been added")
    derivedAxiomDB.get(lemmaName).getOrElse(throw new IllegalArgumentException("Lemma " + lemmaName + " for derived axiom/rule " + name + " should have been added already")).fact
  }

  private val AUTO_INSERT = true

  /** Derive an axiom from the given provable, package it up as a Lemma and make it available */
  private[btactics] def derivedAxiom(name: String, fact: Provable): Lemma = {
    require(fact.isProved, "only proved Provables would be accepted as derived axioms: " + name + " got\n" + fact)
    // create evidence (traces input into tool and output from tool)
    val evidence = new ToolEvidence(immutable.Map("input" -> fact.toString, "output" -> "true")) :: Nil
    val lemmaName = AxiomInfo(name).codeName
    val lemma = Lemma(fact, evidence, Some(lemmaName))
    if (!AUTO_INSERT) {
      lemma
    } else {
      // first check whether the lemma DB already contains identical lemma name
      val lemmaID = if (derivedAxiomDB.contains(lemmaName)) {
        // identical lemma contents with identical name, so reuse ID
        if (derivedAxiomDB.get(lemmaName).contains(lemma)) lemma.name.get
        else throw new IllegalStateException("Prover already has a different lemma filed under the same name " + derivedAxiomDB.get(lemmaName) + " (lemma " + name + " stored in file name " + lemmaName + ") instnead of " + lemma )
      } else {
        derivedAxiomDB.add(lemma)
      }
      derivedAxiomDB.get(lemmaID).get
    }
  }

  private[btactics] def derivedRule(name: String, fact: Provable): Lemma = {
    // create evidence (traces input into tool and output from tool)
    val evidence = new ToolEvidence(immutable.Map("input" -> fact.toString, "output" -> "true")) :: Nil
    val lemmaName = DerivedRuleInfo(name).codeName
    val lemma = Lemma(fact, evidence, Some(lemmaName))
    if (!AUTO_INSERT) {
      lemma
    } else {
      // first check whether the lemma DB already contains identical lemma name
      val lemmaID = if (derivedAxiomDB.contains(lemmaName)) {
        // identical lemma contents with identical name, so reuse ID
        if (derivedAxiomDB.get(lemmaName).contains(lemma)) lemma.name.get
        else throw new IllegalStateException("Prover already has a different lemma filed under the same name " + derivedAxiomDB.get(lemmaName) + " (lemma " + name + " stored in file name " + lemmaName + ") instnead of " + lemma )
      } else {
        derivedAxiomDB.add(lemma)
      }
      derivedAxiomDB.get(lemmaID).get
    }
  }

  private[btactics] def derivedRule(name: String, derived: Sequent, tactic: BelleExpr): Lemma =
    derivedAxiomDB.get(DerivedRuleInfo(name).codeName) match {
      case Some(lemma) => lemma
      case None => {
        val witness = TactixLibrary.proveBy(derived, tactic)
        derivedRule(name, witness)
      }
    }

  /** Derive an axiom for the given derivedAxiom with the given tactic, package it up as a Lemma and make it available */
  private[btactics] def derivedAxiom(name: String, derived: Sequent, tactic: BelleExpr): Lemma =
    derivedAxiomDB.get(AxiomInfo(name).codeName) match {
      case Some(lemma) => lemma
      case None =>
        val witness = TactixLibrary.proveBy(derived, tactic)
        assert(witness.isProved, "tactics proving derived axioms should produce proved Provables: " + name + " got\n" + witness)
        derivedAxiom(name, witness)
    }

  private val x = Variable("x_", None, Real)
  private val px = PredOf(Function("p_", None, Real, Bool), x)
  private val pany = PredOf(Function("p_", None, Real, Bool), Anything)
  private val qx = PredOf(Function("q_", None, Real, Bool), x)
  private val qany = PredOf(Function("q_", None, Real, Bool), Anything)
  private val fany = FuncOf(Function("f_", None, Real, Real), Anything)
  private val gany = FuncOf(Function("g_", None, Real, Real), Anything)
  private val ctxt = Function("ctx_", None, Real, Real) // function symbol
  private val ctxf = Function("ctx_", None, Real, Bool) // predicate symbol
  private val context = Function("ctx_", None, Bool, Bool) // predicational symbol

  /**
   * Looks up information about a derived axiom by name.
    *
    * @param name The name of the derived axiom.
   * @return The axiom formula and tactic, if found. None otherwise.
   * @note Central index for looking up derived axioms by names.
   * @todo this is only used for prepopulation and nothing else now
   */
  @deprecated("Use AxiomInfo(name) instead")
  private def derivedAxiomInfo(name: String): Option[(Formula, BelleExpr)] = (name: @switch) match {
    //@note implemented as match rather than lookup in a map to retain lazy evaluation
    //@note Every entry should be added to derivedAxiomMap (we need a map when prepopulating the lemma database, so whenever adding a case to this case match also add an entry to the hashmap below.)
    case "<-> reflexive" => Some(equivReflexiveF, equivReflexiveT)
    case "-> distributes over &" => Some(implyDistAndF, implyDistAndT)
    case "-> distributes over <->" => Some(implyDistEquivF, implyDistEquivT)
    case "-> weaken" => Some(implWeakenF, implWeakenT)
    case "!! double negation" => Some(doubleNegationF, doubleNegationT)
    case "exists dual" => Some(existsDualF, existsDualT)
    case "!exists" => Some(notExistsF, notExistsT)
    case "!all" => Some(notAllF, notAllT)
    case "![]" => Some(notBoxF, notBoxT)
    case "!<>" => Some(notDiamondF, notDiamondT)
    case "[] box" => Some(boxF, boxT)
    //case "K1" => Some(K1F, K1T)
    //case "K2" => Some(K2F, K2T)
    case "[] split" => Some(boxAndF, boxAndT)
    //case "[] split left" => Some(boxSplitLeftF, boxSplitLeft)
    //case "[] split right" => Some(boxSplitRightF, boxSplitRightT)
    case "<> split" => Some(diamondOrF, diamondOrT)
    //case "<> split left" => Some(diamondSplitLeftF, diamondSplitLeftT)
    case "<:=> assign" => Some(assigndF, assigndT)
    case ":= assign dual" => Some(assignDualF, assignDualT)
    case "[:=] assign equational" => Some(assignbEquationalF, assignbEquationalT)
    case "[:=] assign equality exists" => Some(assignbExistsF, assignbExistsT)
    case "[:=] assign exists" => Some(assignbImpliesExistsF, assignbImpliesExistsT)
    case "\\exists& exists and" => Some(existsAndF, existsAndT)
    case "[:=] assign update" => Some(assignbUpdateF, assignbUpdateT)
    case "[:=] vacuous assign" => Some(vacuousAssignbF, vacuousAssignbT)
    case "<:=> assign equational" => ??? //Some(assigndEquationalF, assigndEquationalT)
    case "<:=> assign equality" => Some(assigndEqualityF, assigndEqualityT)
    case "<:=> assign update" => Some(assigndUpdateF, assigndUpdateT)
    case "<:=> vacuous assign" => Some(vacuousAssigndF, vacuousAssigndT)
    case "<':=> differential assign" => Some(assignDF, assignDT)
    case "<:*> assign nondet" => Some(nondetassigndF, nondetassigndT)
    case "<?> test" => Some(testdF, testdT)
    case "<++> choice" => Some(choicedF, choicedT)
    case "<;> compose" => Some(composedF, composedT)
    case "<*> iterate" => Some(iteratedF, iteratedT)
    case "<*> approx" => Some(loopApproxdF, loopApproxdT)
    case "[*] approx" => Some(loopApproxbF, loopApproxbT)
    case "exists generalize" => Some(existsGeneralizeF, existsGeneralizeT)
    case "exists eliminate" => Some(existsEliminateF, existsEliminateT)
    case "all substitute" => Some(allSubstituteF, allSubstituteT)
    case "vacuous exists quantifier" => Some(vacuousExistsF, vacuousExistsT)
    case "V[:*] vacuous assign nondet" => Some(vacuousBoxAssignNondetF, vacuousBoxAssignNondetT)
    case "V<:*> vacuous assign nondet" => Some(vacuousDiamondAssignNondetF, vacuousDiamondAssignNondetT)
    case "DX diamond differential skip" => Some(DskipdF, DskipdT)
    case "DW differential weakening" => Some(DWeakeningF, DWeakeningT)
    case "DS differential equation solution" => Some(DSnodomainF, DSnodomainT)
    case "Dsol& differential equation solution" => Some(DSddomainF, DSddomainT)
    case "Dsol differential equation solution" => Some(DSdnodomainF, DSdnodomainT)
    case "Domain Constraint Conjunction Reordering" => Some(domainCommuteF, domainCommuteT)
    case "[] post weaken" => Some(postconditionWeakenF, postconditionWeakenT)
    case "& commute" => Some(andCommuteF, andCommuteT)
    case "& associative" => Some(andAssocF, andAssocT)
    case "& reflexive" => Some(andReflexiveF, andReflexiveT)
    case "!& deMorgan" => Some(notAndF, notAndT)
    case "!| deMorgan" => Some(notOrF, notOrT)
    case "!-> deMorgan" => Some(notImplyF, notImplyT)
    case "!<-> deMorgan" => Some(notEquivF, notEquivT)
    case "-> expand" => Some(implyExpandF, implyExpandT)
    case "-> self" => Some(implySelfF, implySelfT)
    case "<-> true" => Some(equivTrueF, equivTrueT)
    case "PC1" => Some(PC1F, PC1T)
    case "PC2" => Some(PC2F, PC2T)
    case "PC3" => Some(PC3F, PC3T)
    case "PC9" => Some(PC9F, PC9T)
    case "PC10" => Some(PC10F, PC10T)
    case "-> tautology" => Some(implyTautologyF, implyTautologyT)
    case "->' derive imply" => Some(DimplyF, DimplyT)
    case "\\forall->\\exists" => Some(forallThenExistsF, forallThenExistsT)
    case "->true" => Some(impliesTrueF, impliesTrueT)
    case "true->" => Some(trueImpliesF, trueImpliesT)
    case "&true" => Some(andTrueF, andTrueT)
    case "true&" => Some(trueAndF, trueAndT)
    case "0*" => Some(zeroTimesF, zeroTimesT)
    case "0+" => Some(zeroPlusF, zeroPlusT)
    case "*0" => Some(timesZeroF, timesZeroT)
    case "+0" => Some(plusZeroF, plusZeroT)
    //    case "x' derive var" => Some(DvarF, DvarT)
    case "x' derive var commuted" => Some(DvariableCommutedF, DvariableCommutedT)
    case "x' derive variable" => Some(DvariableF, DvariableT)
    case "' linear" => Some(DlinearF, DlinearT)
    case "' linear right" => Some(DlinearRightF, DlinearRightT)
    case "DG differential pre-ghost" => Some(DGpreghostF, DGpreghostT)
    case "= reflexive" => Some(equalReflexiveF, equalReflexiveT)
    case "* commute" => Some(timesCommuteF, timesCommuteT)
    case "= commute" => Some(equalCommuteF, equalCommuteT)
    case "<=" => Some(lessEqualF, lessEqualT)
    case "! !=" => Some(notNotEqualF, notNotEqualT)
    case "! =" => Some(notEqualF, notEqualT)
    case "! <" => Some(notLessF, notLessT)
    case "! <=" => Some(notLessEqualF, notLessEqualT)
    case "! >" => Some(notGreaterF, notGreaterT)
    case "! >=" => derivedAxiomInfo("< negate")
    case "< negate" => Some(notGreaterEqualF, notGreaterEqualT)
    case ">= flip" => Some(flipGreaterEqualF, flipGreaterEqualT)
    case "> flip" => Some(flipGreaterF, flipGreaterT)
    case "<" => Some(lessF, lessT)
    case ">" => Some(greaterF, greaterT)
    case "abs" => Some(absF, absT)
    case "min" => Some(minF, minT)
    case "max" => Some(maxF, maxT)
    case "<*> stuck" => Some(loopStuckF, loopStuckT)
    case "<'> stuck" => Some(odeStuckF, odeStuckT)
    case "+<= up" => Some(intervalUpPlusF, intervalUpPlusT)
    case "-<= up" => Some(intervalUpMinusF, intervalUpMinusT)
    case "*<= up" => Some(intervalUpTimesF, intervalUpTimesT)
    case "1Div<= up" => Some(intervalUp1DivideF, intervalUp1DivideT)
    case "Div<= up" => Some(intervalUpDivideF, intervalUpDivideT)
    case "<=+ down" => Some(intervalDownPlusF, intervalDownPlusT)
    case "<=- down" => Some(intervalDownMinusF, intervalDownMinusT)
    case "<=* down" => Some(intervalDownTimesF, intervalDownTimesT)
    case "<=1Div down" => Some(intervalDown1DivideF, intervalDown1DivideT)
    case "<=Div down" => Some(intervalDownDivideF, intervalDownDivideT)
    case "all distribute" => Some(allDistributeF, allDistributeT)
    case "[]~><> propagation" => Some(boxDiamondPropagationF, boxDiamondPropagationT)
    case "* associative" => Some(timesAssociativeF, timesAssociativeT)
    case "* inverse" => Some(timesInverseF, timesInverseT)
    case "* closed" => Some(timesClosedF, timesClosedT)
    case "* identity" => Some(timesIdentityF, timesIdentityT)
    case "+ associative" => Some(plusAssociativeF, plusAssociativeT)
    case "+ commute" => Some(plusCommutativeF, plusCommutativeT)
    case "+ inverse" => Some(plusInverseF, plusInverseT)
    case "+ closed" => Some(plusClosedF, plusClosedT)
    case "positivity" => Some(positivityF, positivityT)
    case "distributive" => Some(distributiveF, distributiveT)
    case _ => None
  }

  /** populates the derived lemma database with all of the lemmas in the case statement above.*/
  private[keymaerax] def prepopulateDerivedLemmaDatabase() = {
    require(AUTO_INSERT, "AUTO_INSERT should be on if lemma database is being pre-populated.")
    DerivedAxiomInfo.allInfo.map(key => {
      try {
        //@note: cannot use any of AxiomInfo.provable and AxiomInfo.formula, since
        // both ask DerivedAxioms.deriveAxiom, which in turn requires the lemma that we want to prepopulate to be
        // present in the database already; derivedAxiomInfo, as a side effect, stores the lemma to the DB
        derivedAxiomInfo(key.canonicalName)
        // nothing else to do, populated in the DB as a side effect of derivedAxiomInfo
      } catch {
        case _:NotImplementedError => println("WARNING: Axiom not implemented:  " + key.canonicalName)
        case _:Throwable => println("WARNING: Unknown error populating axiom: " + key.canonicalName)
      }
    })
    // populate derived rules
    derivedRule("[] monotone", boxMonotone.fact)
    derivedRule("[] monotone 2", boxMonotone2.fact)
    derivedRule("CT term congruence", CTtermCongruence.fact)
    //@todo all generalization must be populated after [] monotone -> need better dependency handling for derived rules
    derivedRule("all generalization", allGeneralize.fact)
  }

  // derived rules

  /**
    * Rule "all generalization".
    * Premise p(??)
    * Conclusion \forall x p(??)
    * End.
    *
    * @derived from G or from [] monotone with program x:=*
    * @derived from Skolemize
    * @Note generalization of p(x) to p(??) as in Theorem 14
    */
  lazy val allGeneralize = derivedRule("all generalization",
    //(immutable.IndexedSeq(Sequent(immutable.Seq(), immutable.IndexedSeq(), immutable.IndexedSeq(pany))),
    Sequent(immutable.IndexedSeq(), immutable.IndexedSeq("\\forall x_ p_(??)".asFormula)),
    useAt("[:*] assign nondet", PosInExpr(1::Nil))(1) &
    cut(Box(AssignAny(Variable("x_",None,Real)), True)) <(
      monb & hide(-1) partial
      ,
      dualFree(2) //closeT
      )
  )

  /**
    * Rule "CT term congruence".
    * Premise f_(??) = g_(??)
    * Conclusion ctxT_(f_(??)) = ctxT_(g_(??))
    * End.
    *
    * @derived ("Could also use CQ equation congruence with p(.)=(ctx_(.)=ctx_(g_(x))) and reflexivity of = instead.")
    */
  lazy val CTtermCongruence = derivedRule("CT term congruence",
    Sequent(immutable.IndexedSeq(), immutable.IndexedSeq("ctx_(f_(??)) = ctx_(g_(??))".asFormula)),
    cutR("ctx_(g_(??)) = ctx_(g_(??))".asFormula)(SuccPos(0)) <(
      byUS(equalReflex)
      ,
      equivifyR(1) &
        CQ(PosInExpr(0::0::Nil)) &
        commuteEqual(1)
        partial
      )
  )

  /**
    * Rule "[] monotone".
    * Premise p(??) ==> q(??)
    * Conclusion [a;]p(??) ==> [a;]q(??)
    * End.
    *
    * @derived useAt("<> diamond") & by("<> monotone")
    * @see "André Platzer. Differential Game Logic. ACM Trans. Comput. Log. 2015"
    * @see "André Platzer. Differential Hybrid Games."
    * @note Notation changed to p instead of p_ just for the sake of the derivation.
    */
  lazy val boxMonotone = derivedRule("[] monotone",
    Sequent(immutable.IndexedSeq("[a_;]p_(??)".asFormula), immutable.IndexedSeq("[a_;]q_(??)".asFormula)),
    useAt("[] box", PosInExpr(1::Nil))(-1) & useAt("[] box", PosInExpr(1::Nil))(1) &
      notL(-1) & notR(1) &
      by("<> monotone", USubst(
        SubstitutionPair(PredOf(Function("p_", None, Real, Bool), Anything), Not(PredOf(Function("q_", None, Real, Bool), Anything))) ::
        SubstitutionPair(PredOf(Function("q_", None, Real, Bool), Anything), Not(PredOf(Function("p_", None, Real, Bool), Anything))) :: Nil)) &
      notL(-1) & notR(1)
      partial
  )

  /**
    * Rule "[] monotone 2".
    * Premise q(??) ==> p(??)
    * Conclusion [a;]q(??) ==> [a;]p(??)
    * End.
    *
    * @derived useAt(boxMonotone) with p and q swapped
    * @see "André Platzer. Differential Game Logic. ACM Trans. Comput. Log. 2015"
    * @see "André Platzer. Differential Hybrid Games."
    * @note Renamed form of boxMonotone.
    */
  lazy val boxMonotone2 = derivedRule("[] monotone 2",
    Sequent(immutable.IndexedSeq("[a_;]q_(??)".asFormula), immutable.IndexedSeq("[a_;]p_(??)".asFormula)),
    useAt("[] box", PosInExpr(1::Nil))(-1) & useAt("[] box", PosInExpr(1::Nil))(1) &
      notL(-1) & notR(1) &
      byUS("<> monotone") &
//      ProofRuleTactics.axiomatic("<> monotone", USubst(
//        SubstitutionPair(PredOf(Function("p_", None, Real, Bool), Anything), Not(PredOf(Function("q_", None, Real, Bool), Anything))) ::
//          SubstitutionPair(PredOf(Function("q_", None, Real, Bool), Anything), Not(PredOf(Function("p_", None, Real, Bool), Anything))) :: Nil)) &
      notL(-1) & notR(1)
      partial
  )

  // derived axioms and their proofs

  /**
    * {{{Axiom "<-> reflexive".
    *  p() <-> p()
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val equivReflexiveF = "p_() <-> p_()".asFormula
  lazy val equivReflexiveAxiom = derivedAxiom("<-> reflexive",
    Provable.startProof(Sequent(IndexedSeq(), IndexedSeq(equivReflexiveF)))
    (EquivRight(SuccPos(0)), 0)
      // right branch
      (Close(AntePos(0),SuccPos(0)), 1)
      // left branch
      (Close(AntePos(0),SuccPos(0)), 0)
  )

  lazy val equivReflexiveT = TactixLibrary.byUS(equivReflexiveAxiom)

  /**
    * {{{Axiom "-> distributes over &".
    *  (p() -> (q()&r())) <-> ((p()->q()) & (p()->r()))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val implyDistAndF = "(p_() -> (q_()&r_())) <-> ((p_()->q_()) & (p_()->r_()))".asFormula
  lazy val implyDistAndAxiom = derivedAxiom("-> distributes over &",
    Sequent(IndexedSeq(), IndexedSeq(implyDistAndF)),
    prop
  )

  lazy val implyDistAndT = TactixLibrary.byUS(implyDistAndAxiom)

  /**
    * {{{Axiom "-> weaken".
    *  (p() -> q()) -> (p()&c() -> q())
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val implWeakenF = "(p_() -> q_()) -> ((p_()&c_()) -> q_())".asFormula
  lazy val implWeaken = derivedAxiom("-> weaken",
    Sequent(IndexedSeq(), IndexedSeq(implWeakenF)),
    prop
  )

  lazy val implWeakenT = TactixLibrary.byUS(implWeaken)

  /**
    * {{{Axiom "-> distributes over <->".
    *  (p() -> (q()<->r())) <-> ((p()->q()) <-> (p()->r()))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val implyDistEquivF = "(p_() -> (q_()<->r_())) <-> ((p_()->q_()) <-> (p_()->r_()))".asFormula
  lazy val implyDistEquivAxiom = derivedAxiom("-> distributes over <->",
    Sequent(IndexedSeq(), IndexedSeq(implyDistEquivF)),
    prop
  )

  lazy val implyDistEquivT = TactixLibrary.byUS(implyDistEquivAxiom)

  /**
    * {{{Axiom "!! double negation".
    *  (!(!p())) <-> p()
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val doubleNegationF = "(!(!p_())) <-> p_()".asFormula
  lazy val doubleNegationAxiom = derivedAxiom("!! double negation",
    Provable.startProof(Sequent(IndexedSeq(), IndexedSeq(doubleNegationF)))
    (EquivRight(SuccPos(0)), 0)
      // right branch
      (NotRight(SuccPos(0)), 1)
      (NotLeft(AntePos(1)), 1)
      (Close(AntePos(0),SuccPos(0)), 1)
      // left branch
      (NotLeft(AntePos(0)), 0)
      (NotRight(SuccPos(1)), 0)
      (Close(AntePos(0),SuccPos(0)), 0)
  )

  lazy val doubleNegationT = TactixLibrary.byUS(doubleNegationAxiom)

  /**
    * {{{Axiom "vacuous all quantifier".
    *  (\forall x_ p()) <-> p()
    * End.
    * }}}
    *
    * @Derived from new axiom "p() -> (\forall x_ p())" and ""all instantiate" or "all eliminate".
    * @todo replace by weaker axiom in AxiomBase and prove it.
    */

  /**
    * {{{Axiom "exists dual".
    *   (!\forall x (!p(??))) <-> (\exists x p(??))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val existsDualF = "(!\\forall x_ (!p_(??))) <-> \\exists x_ p_(??)".asFormula
  lazy val existsDualAxiom = derivedAxiom("exists dual",
    Sequent(IndexedSeq(), IndexedSeq(existsDualF)),
    useAt("all dual", PosInExpr(1::Nil))(1, 0::0::Nil) &
      useAt(doubleNegationAxiom)(1, 0::Nil) &
      useAt(doubleNegationAxiom)(1, 0::0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val existsDualT = TactixLibrary.byUS(existsDualAxiom)

  /**
    * {{{Axiom "!exists".
    *   (!\exists x (p(x))) <-> \forall x (!p(x))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val notExistsF = "(!\\exists x_ (p_(x_))) <-> \\forall x_ (!p_(x_))".asFormula
  lazy val notExists = derivedAxiom("!exists",
    Sequent(IndexedSeq(), IndexedSeq(notExistsF)),
    useAt(doubleNegationAxiom, PosInExpr(1::Nil))(1, 0::0::0::Nil) &
      useAt("all dual")(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val notExistsT = TactixLibrary.byUS(notExists)

  /**
    * {{{Axiom "!all".
    *   (!\forall x (p(x))) <-> \exists x (!p(x))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val notAllF = "(!\\forall x_ (p_(x_))) <-> \\exists x_ (!p_(x_))".asFormula
  lazy val notAll = derivedAxiom("!all",
    Sequent(IndexedSeq(), IndexedSeq(notAllF)),
    useAt(doubleNegationAxiom, PosInExpr(1::Nil))(1, 0::0::0::Nil) &
      useAt(existsDualAxiom)(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val notAllT = TactixLibrary.byUS(notAll)

  /**
    * {{{Axiom "![]".
    *   ![a;]p(x) <-> <a;>!p(x)
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val notBoxF = "(![a_;]p_(x_)) <-> (<a_;>!p_(x_))".asFormula
  lazy val notBox = derivedAxiom("![]",
    Sequent(IndexedSeq(), IndexedSeq(notBoxF)),
    useAt(doubleNegationAxiom, PosInExpr(1::Nil))(1, 0::0::1::Nil) &
      useAt("<> diamond")(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val notBoxT = TactixLibrary.byUS(notBox)

  /**
    * {{{Axiom "!<>".
    *   !<a;>p(x) <-> [a;]!p(x)
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val notDiamondF = "(!<a_;>p_(x_)) <-> ([a_;]!p_(x_))".asFormula
  lazy val notDiamond = derivedAxiom("!<>",
    Sequent(IndexedSeq(), IndexedSeq(notDiamondF)),
    useAt(doubleNegationAxiom, PosInExpr(1::Nil))(1, 0::0::1::Nil) &
      useAt(boxAxiom)(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val notDiamondT = TactixLibrary.byUS(notDiamond)

  /**
    * {{{Axiom "all eliminate".
    *    (\forall x p(??)) -> p(??)
    * End.
    * }}}
    *
    * @todo will clash unlike the converse proof.
    */
  lazy val allEliminateF = "(\\forall x_ p_(??)) -> p_(??)".asFormula
  lazy val allEliminateAxiom = ??? /*derivedAxiom("all eliminate",
    Sequent(Nil, IndexedSeq(), IndexedSeq(allEliminateF)),
    US(
      USubst(SubstitutionPair(PredOf(Function("p",None,Real,Bool),DotTerm), PredOf(Function("p",None,Real,Bool),Anything))::Nil),
      Sequent(Nil, IndexedSeq(), IndexedSeq(allEliminateF)))
  )*/
  lazy val allEliminateT = ??? //TactixLibrary.byUS(allEliminateAxiom)

  /**
    * {{{Axiom "all distribute".
    *   (\forall x (p(x)->q(x))) -> ((\forall x p(x))->(\forall x q(x)))
    * }}}
    */
  // Imply(Forall(Seq(x), Imply(PredOf(p,x),PredOf(q,x))), Imply(Forall(Seq(x),PredOf(p,x)), Forall(Seq(x),PredOf(q,x))))
  lazy val allDistributeF = "(\\forall x_ (p(x_)->q(x_))) -> ((\\forall x_ p(x_))->(\\forall x_ q(x_)))".asFormula
  lazy val allDistributeAxiom = derivedAxiom("all distribute",
    Sequent(IndexedSeq(), IndexedSeq(allDistributeF)),
    implyR(1) & implyR(1) & allR(1) & allL(-2) & allL(-1) & prop)
  lazy val allDistributeT = TactixLibrary.byUS(allDistributeAxiom)

  /**
    * {{{Axiom "all quantifier scope".
    *    (\forall x (p(x) & q())) <-> ((\forall x p(x)) & q())
    * End.
    * }}}
    *
    * @todo follows from "all distribute" and "all vacuous"
    */


  /**
    * {{{Axiom "[] box".
    *    (!<a;>(!p(??))) <-> [a;]p(??)
    * End.
    * }}}
    *
    * @note almost same proof as "exists dual"
    * @Derived
    */
  lazy val boxF = "(!<a_;>(!p_(??))) <-> [a_;]p_(??)".asFormula
  lazy val boxAxiom = derivedAxiom("[] box",
    Sequent(IndexedSeq(), IndexedSeq(boxF)),
    useAt("<> diamond", PosInExpr(1::Nil))(1, 0::0::Nil) &
      useAt(doubleNegationAxiom)(1, 0::Nil) &
      useAt(doubleNegationAxiom)(1, 0::1::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val boxT = TactixLibrary.byUS(boxAxiom)

  /**
    * {{{Axiom "[]~><> propagation".
    *    [a;]p(??) & <a;>q(??) -> <a;>(p(??) & q(??))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val boxDiamondPropagationF = "([a_;]p_(??) & <a_;>q_(??)) -> <a_;>(p_(??) & q_(??))".asFormula
  lazy val boxDiamondPropagation = derivedAxiom("[]~><> propagation",
    Sequent(IndexedSeq(), IndexedSeq(boxDiamondPropagationF)),
    useAt("<> diamond", PosInExpr(1::Nil))(1, 0::1::Nil) &
      useAt("<> diamond", PosInExpr(1::Nil))(1, 1::Nil) &
      cut("[a_;]p_(??) & [a_;]!(p_(??)&q_(??)) -> [a_;]!q_(??)".asFormula) <(
        /* use */ prop,
        /* show */ hideR(1) &
        cut("[a_;](p_(??) & !(p_(??)&q_(??)))".asFormula) <(
          /* use */ implyR(1) & hideL(-2) & /* monb fails renaming substitution */ implyRi() & CMon(PosInExpr(1::Nil)) & prop,
          /* show */ implyR(1) & splitb(1) & prop
          )
        )
  )

  lazy val boxDiamondPropagationT = TactixLibrary.byUS(boxDiamondPropagation)

  /**
    * {{{Axiom "K1".
    *   [a;](p(??)&q(??)) -> [a;]p(??) & [a;]q(??)
    * End.
    * }}}
    *
    * @Derived
    * @Note implements Cresswell, Hughes. A New Introduction to Modal Logic, K1 p. 26
    * @internal
    */
  private lazy val K1F = "[a_;](p_(??)&q_(??)) -> [a_;]p_(??) & [a_;]q_(??)".asFormula
  private lazy val K1 = TactixLibrary.proveBy(//derivedAxiom("K1",
    Sequent(IndexedSeq(), IndexedSeq(K1F)),
    implyR(1) & andR(1) <(
      useAt(boxSplitLeft, PosInExpr(0::Nil))(-1) & close,
      useAt(boxSplitRight, PosInExpr(0::Nil))(-1) & close
      )
  )
  //private lazy val K1T = derivedAxiomT(K1)

  /**
    * {{{Axiom "K2".
    *   [a;]p(??) & [a;]q(??) -> [a;](p(??)&q(??))
    * End.
    * }}}
    *
    * @Derived
    * @Note implements Cresswell, Hughes. A New Introduction to Modal Logic, K2 p. 27
    *      @internal
    */
  private lazy val K2F = "[a_;]p_(??) & [a_;]q_(??) -> [a_;](p_(??)&q_(??))".asFormula
  private lazy val K2 = TactixLibrary.proveBy(//derivedAxiom("K2",
    Sequent(IndexedSeq(), IndexedSeq(K2F)),
    cut(/*(9)*/"([a_;](q_(??)->p_(??)&q_(??)) -> ([a_;]q_(??) -> [a_;](p_(??)&q_(??))))  ->  (([a_;]p_(??) & [a_;]q_(??)) -> [a_;](p_(??)&q_(??)))".asFormula) <(
      /* use */ cut(/*(6)*/"[a_;](q_(??) -> (p_(??)&q_(??)))  ->  ([a_;]q_(??) -> [a_;](p_(??)&q_(??)))".asFormula) <(
      /* use */ modusPonens(AntePos(1), AntePos(0)) & close,
      /* show */ cohide(2) & byUS("K modal modus ponens")
      ),
      /* show */ cut(/*(8)*/"([a_;]p_(??) -> [a_;](q_(??) -> p_(??)&q_(??)))  ->  (([a_;](q_(??)->p_(??)&q_(??)) -> ([a_;]q_(??) -> [a_;](p_(??)&q_(??))))  ->  (([a_;]p_(??) & [a_;]q_(??)) -> [a_;](p_(??)&q_(??))))".asFormula) <(
      /* use */ cut(/*(5)*/"[a_;]p_(??) -> [a_;](q_(??) -> p_(??)&q_(??))".asFormula) <(
      /* use */ modusPonens(AntePos(1), AntePos(0)) & close,
      /* show */ cohide(3) & useAt("K modal modus ponens", PosInExpr(1::Nil))(1) & useAt(implyTautology)(1, 1::Nil) & V(1) & close
      ),
      /* show */ cohide(3) & prop
      )
      )
  )
  //private lazy val K2T = derivedAxiomT(K2)

  /**
    * {{{Axiom "[] split".
    *    [a;](p(??)&q(??)) <-> [a;]p(??)&[a;]q(??)
    * End.
    * }}}
    *
    * @Derived
    * @Note implements Cresswell, Hughes. A New Introduction to Modal Logic, K3 p. 28
    */
  lazy val boxAndF = "[a_;](p_(??)&q_(??)) <-> [a_;]p_(??)&[a_;]q_(??)".asFormula
  lazy val boxAnd = derivedAxiom("[] split",
    Sequent(IndexedSeq(), IndexedSeq(boxAndF)),
    equivR(1) <(
      useAt(K1, PosInExpr(1::Nil))(1) & close,
      useAt(K2, PosInExpr(1::Nil))(1) & close
      )
  )
  lazy val boxAndT = TactixLibrary.byUS(boxAnd)

  /**
    * {{{Axiom "boxSplitLeft".
    *    [a;](p(??)&q(??)) -> [a;]p(??)
    * End.
    * }}}
    *
    * @Derived
    * @Note implements (1)-(5) of Cresswell, Hughes. A New Introduction to Modal Logic, K1
    *      @internal
    */
  private lazy val boxSplitLeftF = "[a_;](p_(??)&q_(??)) -> [a_;]p_(??)".asFormula
  private lazy val boxSplitLeft = TactixLibrary.proveBy(//derivedAxiom("[] split left",
    Sequent(IndexedSeq(), IndexedSeq(boxSplitLeftF)),
    cut(/*(2)*/"[a_;](p_(??)&q_(??) -> p_(??))".asFormula) <(
      /* use */ cut(/*(4)*/"[a_;](p_(??)&q_(??)->p_(??)) -> ([a_;](p_(??)&q_(??)) -> [a_;]p_(??))".asFormula) <(
      /* use */ modusPonens(AntePos(0), AntePos(1)) & close,
      /* show */ cohide(2) & byUS("K modal modus ponens")
      ),
      /* show */ cohide(2) & useAt(PC1)(1, 1::0::Nil) & useAt(implySelf)(1, 1::Nil) & V(1) & close
      )
  )

  //private lazy val boxSplitLeftT = derivedAxiomT(boxSplitLeft)

  /**
    * {{{Axiom "<> split".
    *    <a;>(p(??)|q(??)) <-> <a;>p(??)|<a;>q(??)
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val diamondOrF = "<a_;>(p_(??)|q_(??)) <-> <a_;>p_(??)|<a_;>q_(??)".asFormula
  lazy val diamondOr = derivedAxiom("<> split",
    Sequent(IndexedSeq(), IndexedSeq(diamondOrF)),
    useAt("<> diamond", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("<> diamond", PosInExpr(1::Nil))(1, 1::0::Nil) &
      useAt("<> diamond", PosInExpr(1::Nil))(1, 1::1::Nil) &
      useAt(notOr)(1, 0::0::1::Nil) &
      useAt(boxAnd)(1, 0::0::Nil) &
      useAt(notAnd)(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )
  lazy val diamondOrT = TactixLibrary.byUS(diamondOr)

  /**
    * {{{Axiom "<> split left".
    *    <a;>(p(??)&q(??)) -> <a;>p(??)
    * End.
    * }}}
    *
    * @Derived
    *         @internal
    */
  private lazy val diamondSplitLeftF = "<a_;>(p_(??)&q_(??)) -> <a_;>p_(??)".asFormula
  private lazy val diamondSplitLeft = TactixLibrary.proveBy(//derivedAxiom("<> split left",
    Sequent(IndexedSeq(), IndexedSeq(diamondSplitLeftF)),
    useAt(PC1)(1, 0::1::Nil) & useAt(implySelf)(1) & close
  )
  //private lazy val diamondSplitLeftT = derivedAxiomT(diamondSplitLeft)

  /**
    * {{{Axiom "boxSplitRight".
    *    [a;](p(??)&q(??)) -> q(??)
    * End.
    * }}}
    *
    * @Derived
    * @Note implements (6)-(9) of Cresswell, Hughes. A New Introduction to Modal Logic, K1
    *      @internal
    */
  private lazy val boxSplitRightF = "[a_;](p_(??)&q_(??)) -> [a_;]q_(??)".asFormula
  private lazy val boxSplitRight = TactixLibrary.proveBy(//derivedAxiom("[] split right",
    Sequent(IndexedSeq(), IndexedSeq(boxSplitRightF)),
    cut(/*7*/"[a_;](p_(??)&q_(??) -> q_(??))".asFormula) <(
      /* use */ cut(/*(8)*/"[a_;](p_(??)&q_(??)->q_(??)) -> ([a_;](p_(??)&q_(??)) -> [a_;]q_(??))".asFormula) <(
      /* use */ modusPonens(AntePos(0), AntePos(1)) & close,
      /* show */ cohide(2) & byUS("K modal modus ponens")
      ),
      /* show */ cohide(2) & useAt(PC2)(1, 1::0::Nil) & useAt(implySelf)(1, 1::Nil) & V(1) & close
      )
  )
  //private lazy val boxSplitRightT = derivedAxiomT(boxSplitRight)

  /**
    * {{{Axiom "[:=] assign equality exists".
    *   [x:=f();]p(??) <-> \exists x (x=f() & p(??))
    * End.
    * }}}
    *
    * @Derived by ":= assign dual" from "<:=> assign equality".
    * @todo does not derive yet
    */
  lazy val assignbExistsF = "[x_:=f_();]p_(??) <-> \\exists x_ (x_=f_() & p_(??))".asFormula
  lazy val assignbExistsAxiom = derivedAxiom("[:=] assign equality exists",
    Sequent(IndexedSeq(), IndexedSeq(assignbExistsF)),
    useAt("<:=> assign equality", PosInExpr(1::Nil))(1, 1::Nil) &
      //@note := assign dual is not applicable since [v:=t()]p(v) <-> <v:=t()>p(t),
      //      and [v:=t()]p(??) <-> <v:=t()>p(??) not derivable since clash in allL
      useAt(":= assign dual")(1, 1::Nil) & byUS(equivReflexiveAxiom)
  )
  lazy val assignbExistsT = TactixLibrary.byUS(assignbExistsAxiom)

  /**
    * {{{Axiom "[:=] assign exists".
    *  [x_:=f_();]p_(??) -> \exists x_ p_(??)
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val assignbImpliesExistsF = "[x_:=f_();]p_(??) -> \\exists x_ p_(??)".asFormula
  lazy val assignbImpliesExistsAxiom = derivedAxiom("[:=] assign exists",
    Sequent(IndexedSeq(), IndexedSeq(assignbImpliesExistsF)),
//    useAt(existsAndAxiom, PosInExpr(1::Nil))(1, 1::Nil)
//      & byUS("[:=] assign equality exists")
    useAt("[:=] assign equality exists", PosInExpr(0::Nil))(1, 0::Nil) &
    byUS(existsAndAxiom)
  )
  lazy val assignbImpliesExistsT = TactixLibrary.byUS(assignbImpliesExistsAxiom)

  /**
    * {{{Axiom "\\exists& exists and".
    *  \exists x_ (q_(??) & p_(??)) -> \exists x_ (p_(??))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val existsAndF = "\\exists x_ (q_(??) & p_(??)) -> \\exists x_ (p_(??))".asFormula
  lazy val existsAndAxiom = derivedAxiom("\\exists& exists and",
    Sequent(IndexedSeq(), IndexedSeq(existsAndF)),
    /*implyR(1) &*/ CMon(PosInExpr(0::Nil)) & prop // & andL(-1) & closeId//(-2,1)
  )
  lazy val existsAndT = TactixLibrary.byUS(existsAndAxiom)

  /**
    * {{{Axiom "<:=> assign equality".
    *    <x:=f();>p(??) <-> \exists x (x=f() & p(??))
    * End.
    * }}}
    *
    * @Derived from [:=] assign equality, quantifier dualities
    * @Derived by ":= assign dual" from "[:=] assign equality exists".
    */
  lazy val assigndEqualityF = "<x_:=f_();>p_(??) <-> \\exists x_ (x_=f_() & p_(??))".asFormula
  lazy val assigndEqualityAxiom = derivedAxiom("<:=> assign equality",
    Sequent(IndexedSeq(), IndexedSeq(assigndEqualityF)),
    useAt("<> diamond", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("exists dual", PosInExpr(1::Nil))(1, 1::Nil) &
      useAt("!& deMorgan")(1, 1::0::0::Nil) &
      useAt(implyExpand, PosInExpr(1::Nil))(1, 1::0::0::Nil) &
      CE(PosInExpr(0::Nil)) &
      byUS("[:=] assign equality")
  )
  lazy val assigndEqualityT = TactixLibrary.byUS(assigndEqualityAxiom)


  /**
    * {{{Axiom "<:=> assign".
    *    <v:=t();>p(v) <-> p(t())
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val assigndF = "<x_:=f();>p(x_) <-> p(f())".asFormula
  lazy val assigndAxiom = derivedAxiom("<:=> assign",
    Sequent(IndexedSeq(), IndexedSeq(assigndF)),
    useAt("<> diamond", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("[:=] assign")(1, 0::0::Nil) &
      useAt(doubleNegationAxiom)(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val assigndT = TactixLibrary.byUS(assigndAxiom)

  /**
    * {{{Axiom ":= assign dual".
    *    <v:=t();>p(v) <-> [v:=t();]p(v)
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val assignDualF = "<x_:=f();>p(x_) <-> [x_:=f();]p(x_)".asFormula
  lazy val assignDualAxiom = derivedAxiom(":= assign dual",
    Sequent(IndexedSeq(), IndexedSeq(assignDualF)),
    useAt(assigndAxiom)(1, 0::Nil) &
      useAt("[:=] assign")(1, 1::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val assignDualT = TactixLibrary.byUS(assignDualAxiom)

  /**
    * {{{Axiom "[:=] assign equational".
    *    [v:=t();]p(v) <-> \forall v (v=t() -> p(v))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val assignbEquationalF = "[x_:=f();]p(x_) <-> \\forall x_ (x_=f() -> p(x_))".asFormula
  lazy val assignbEquationalAxiom = derivedAxiom("[:=] assign equational",
    Sequent(IndexedSeq(), IndexedSeq(assignbEquationalF)),
    useAt("[:=] assign")(1, 0::Nil) &
      commuteEquivR(1) &
      byUS(allSubstitute)
  )

  lazy val assignbEquationalT = TactixLibrary.byUS(assignbEquationalAxiom)

  /**
    * {{{Axiom "[:=] assign update".
    *    [x:=t();]p(x) <-> [x:=t();]p(x)
    * End.
    * }}}
    *
    * @Derived
    * @note Trivial reflexive stutter axiom, only used with a different recursor pattern in AxiomIndex.
    */
  lazy val assignbUpdateF = "[x_:=t_();]p_(x_) <-> [x_:=t_();]p_(x_)".asFormula
  lazy val assignbUpdate = derivedAxiom("[:=] assign update",
    Sequent(IndexedSeq(), IndexedSeq(assignbUpdateF)),
    byUS(equivReflexiveAxiom)
  )

  lazy val assignbUpdateT = TactixLibrary.byUS(assignbUpdate)

  /**
    * {{{Axiom "<:=> assign update".
    *    <x:=t();>p(x) <-> <x:=t();>p(x)
    * End.
    * }}}
    *
    * @Derived
    * @note Trivial reflexive stutter axiom, only used with a different recursor pattern in AxiomIndex.
    */
  lazy val assigndUpdateF = "<x_:=t_();>p_(x_) <-> <x_:=t_();>p_(x_)".asFormula
  lazy val assigndUpdate = derivedAxiom("<:=> assign update",
    Sequent(IndexedSeq(), IndexedSeq(assigndUpdateF)),
    byUS(equivReflexiveAxiom)
  )

  lazy val assigndUpdateT = TactixLibrary.byUS(assigndUpdate)

  /**
    * {{{Axiom "[:=] vacuous assign".
    *    [v:=t();]p() <-> p()
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val vacuousAssignbF = "[v_:=t_();]p_() <-> p_()".asFormula
  lazy val vacuousAssignbAxiom = derivedAxiom("[:=] vacuous assign",
    Sequent(IndexedSeq(), IndexedSeq(vacuousAssignbF)),
    useAt("[:=] assign")(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val vacuousAssignbT = TactixLibrary.byUS(vacuousAssignbAxiom)

  /**
    * {{{Axiom "<:=> vacuous assign".
    *    <v:=t();>p() <-> p()
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val vacuousAssigndF = "<v_:=t_();>p_() <-> p_()".asFormula
  lazy val vacuousAssigndAxiom = derivedAxiom("<:=> vacuous assign",
    Sequent(IndexedSeq(), IndexedSeq(vacuousAssigndF)),
    useAt("<> diamond", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt(vacuousAssignbAxiom)(1, 0::0::Nil) &
      useAt(doubleNegationAxiom)(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val vacuousAssigndT = TactixLibrary.byUS(vacuousAssigndAxiom)

  /**
    * {{{Axiom "<':=> differential assign".
    *    <v':=t();>p(v') <-> p(t())
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val assignDF = "<x_':=f();>p(x_') <-> p(f())".asFormula
  lazy val assignDAxiom = derivedAxiom("<':=> differential assign",
    Sequent(IndexedSeq(), IndexedSeq(assignDF)),
    useAt("<> diamond", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("[':=] differential assign")(1, 0::0::Nil) &
      useAt(doubleNegationAxiom)(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val assignDT = TactixLibrary.byUS(assignDAxiom)

  /**
    * {{{Axiom "<:*> assign nondet".
    *    <x:=*;>p(x) <-> (\exists x p(x))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val nondetassigndF = "<x_:=*;>p_(x_) <-> (\\exists x_ p_(x_))".asFormula
  lazy val nondetassigndAxiom = derivedAxiom("<:*> assign nondet",
    Sequent(IndexedSeq(), IndexedSeq(nondetassigndF)),
    useAt("<> diamond", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("[:*] assign nondet")(1, 0::0::Nil) &
      useAt("all dual", PosInExpr(1::Nil))(1, 0::0::Nil) &
      useAt(doubleNegationAxiom)(1, 0::Nil) &
      useAt(doubleNegationAxiom)(1, 0::0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val nondetassigndT = TactixLibrary.byUS(nondetassigndAxiom)

  /**
    * {{{Axiom "<?> test".
    *    <?H();>p() <-> (H() & p())
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val testdF = "<?H_();>p_() <-> (H_() & p_())".asFormula
  lazy val testdAxiom = derivedAxiom("<?> test",
    Sequent(IndexedSeq(), IndexedSeq(testdF)),
    useAt("<> diamond", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("[?] test")(1, 0::0::Nil) &
      prop
  )

  lazy val testdT = TactixLibrary.byUS(testdAxiom)

  /**
    * {{{Axiom "<++> choice".
    *    <a;++b;>p(??) <-> (<a;>p(??) | <b;>p(??))
    * End.
    * }}}
    *
    * @todo first show de Morgan
    */
  lazy val choicedF = "<a_;++b_;>p_(??) <-> (<a_;>p_(??) | <b_;>p_(??))".asFormula
  lazy val choicedAxiom = derivedAxiom("<++> choice",
    Sequent(IndexedSeq(), IndexedSeq(choicedF)),
    useAt("<> diamond", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("[++] choice")(1, 0::0::Nil) &
      useAt("<> diamond", PosInExpr(1::Nil))(1, 1::0::Nil) &
      useAt("<> diamond", PosInExpr(1::Nil))(1, 1::1::Nil) &
      prop
  )

  lazy val choicedT = TactixLibrary.byUS(choicedAxiom)

  /**
    * {{{Axiom "<;> compose".
    *    <a;b;>p(??) <-> <a;><b;>p(??)
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val composedF = "<a_;b_;>p_(??) <-> <a_;><b_;>p_(??)".asFormula
  lazy val composedAxiom = derivedAxiom("<;> compose",
    Sequent(IndexedSeq(), IndexedSeq(composedF)),
    useAt("<> diamond", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("[;] compose")(1, 0::0::Nil) &
      useAt("<> diamond", PosInExpr(1::Nil))(1, 1::1::Nil) &
      useAt("<> diamond", PosInExpr(1::Nil))(1, 1::Nil) &
      useAt(doubleNegationAxiom)(1, 1::0::1::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val composedT = TactixLibrary.byUS(composedAxiom)

  /**
    * {{{Axiom "<*> iterate".
    *    <{a;}*>p(??) <-> (p(??) | <a;><{a;}*> p(??))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val iteratedF = "<{a_;}*>p_(??) <-> (p_(??) | <a_;><{a_;}*> p_(??))".asFormula
  lazy val iteratedAxiom = derivedAxiom("<*> iterate",
    Sequent(IndexedSeq(), IndexedSeq(iteratedF)),
    useAt("<> diamond", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("[*] iterate")(1, 0::0::Nil) &
      useAt("<> diamond", PosInExpr(1::Nil))(1, 1::1::1::Nil) &
      useAt("<> diamond", PosInExpr(1::Nil))(1, 1::1::Nil) &
      HilbertCalculus.stepAt(1, 0::Nil) &
      useAt(doubleNegationAxiom)(1, 1::1::0::1::Nil) &
      prop
  )

  lazy val iteratedT = TactixLibrary.byUS(iteratedAxiom)

  /**
    * {{{Axiom "<*> approx".
    *    <a;>p(??) -> <{a;}*>p(??)
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val loopApproxdF = "<a_;>p_(??) -> <{a_;}*>p_(??)".asFormula
  lazy val loopApproxd = derivedAxiom("<*> approx",
    Sequent(IndexedSeq(), IndexedSeq(loopApproxdF)),
    useAt("<*> iterate")(1, 1::Nil) &
      useAt("<*> iterate")(1, 1::1::1::Nil) &
      cut("<a_;>p_(??) -> <a_;>(p_(??) | <a_;><{a_;}*>p_(??))".asFormula) <(
        /* use */ prop,
        /* show */ hideR(1) & implyR('_) & mond & prop
        )
  )
  lazy val loopApproxdT = TactixLibrary.byUS(loopApproxd)


  /**
    * {{{Axiom "[*] approx".
    *    [{a;}*]p(??) -> [a;]p(??)
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val loopApproxbF = "[{a_;}*]p_(??) -> [a_;]p_(??)".asFormula
  lazy val loopApproxb = derivedAxiom("[*] approx",
    Sequent(IndexedSeq(), IndexedSeq(loopApproxbF)),
    useAt("[*] iterate")(1, 0::Nil) &
      useAt("[*] iterate")(1, 0::1::1::Nil) &
      cut("[a_;](p_(??) & [a_;][{a_;}*]p_(??)) -> [a_;]p_(??)".asFormula) <(
        /* use */ prop,
        /* show */ hideR(1) & implyR('_) & monb & prop

        )
  )
  lazy val loopApproxbT = TactixLibrary.byUS(loopApproxb)

  //@todo this is somewhat indirect. Maybe it'd be better to represent derived axioms merely as Lemma and auto-wrap them within their ApplyRule[LookupLemma] tactics on demand.
  //private def useAt(lem: ApplyRule[LookupLemma]): PositionTactic = TactixLibrary.useAt(lem.rule.lemma.fact)

  /**
    * {{{Axiom "exists generalize".
    *    p(t()) -> (\exists x p(x))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val existsGeneralizeF = "p_(f()) -> (\\exists x_ p_(x_))".asFormula
  lazy val existsGeneralize = derivedAxiom("exists generalize",
    Sequent(IndexedSeq(), IndexedSeq(existsGeneralizeF)),
    useAt(existsDualAxiom, PosInExpr(1::Nil))(1, 1::Nil) &
      implyR(SuccPos(0)) &
      notR(SuccPos(0)) &
      useAt("all instantiate", PosInExpr(0::Nil))(-2) &
      prop
  )
  lazy val existsGeneralizeT = TactixLibrary.byUS(existsGeneralize)

  /**
    * {{{Axiom "exists eliminate".
    *    p(??) -> (\exists x p(??))
    * End.
    * }}}
    *
    * @Derived
    * @todo prove
    */
  lazy val existsEliminateF = "p_(??) -> (\\exists x_ p_(??))".asFormula
  lazy val existsEliminate = derivedAxiom("exists eliminate",
    Sequent(IndexedSeq(), IndexedSeq(existsEliminateF)),
    useAt(existsDualAxiom, PosInExpr(1::Nil))(1, 1::Nil) &
      implyR(1) &
      notR(1) &
      useAt("all eliminate", PosInExpr(0::Nil))(-2) &
      prop
  )

  lazy val existsEliminateT = TactixLibrary.byUS(existsEliminate)

  /**
    * {{{Axiom "all substitute".
    *    (\forall x (x=t() -> p(x))) <-> p(t())
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val allSubstituteF = "(\\forall x_ (x_=t_() -> p_(x_))) <-> p_(t_())".asFormula
  lazy val allSubstitute = derivedAxiom("all substitute",
    Sequent(IndexedSeq(), IndexedSeq(allSubstituteF)),
    equivR(SuccPos(0)) <(
      /* equiv left */ allL(Variable("x_"), "t_()".asTerm)(-1) & implyL(-1) <(cohide(2) & byUS(equalReflex), close),
      /* equiv right */ allR(1) & implyR(1) & eqL2R(-2)(1) & close
      )
  )
  lazy val allSubstituteT = TactixLibrary.byUS(allSubstitute)


  /**
    * {{{Axiom "vacuous exists quantifier".
    *    (\exists x p()) <-> p()
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val vacuousExistsF = "(\\exists x_ p_()) <-> p_()".asFormula
  lazy val vacuousExistsAxiom = derivedAxiom("vacuous exists quantifier",
    Sequent(IndexedSeq(), IndexedSeq(vacuousExistsF)),
    useAt(existsDualAxiom, PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("vacuous all quantifier")(1, 0::0::Nil) &
      useAt(doubleNegationAxiom)(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val vacuousExistsT = TactixLibrary.byUS(vacuousExistsAxiom)

  /**
    * {{{Axiom "V[:*] vacuous assign nondet".
    *    [x:=*;]p() <-> p()
    * End.
    * @todo reorient
    * @Derived
    * */
  lazy val vacuousBoxAssignNondetF = "([x_:=*;]p_()) <-> p_()".asFormula
  lazy val vacuousBoxAssignNondetAxiom = derivedAxiom("V[:*] vacuous assign nondet",
    Sequent(IndexedSeq(), IndexedSeq(vacuousBoxAssignNondetF)),
    useAt("[:*] assign nondet")(1, 0::Nil) &
      useAt("vacuous all quantifier")(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val vacuousBoxAssignNondetT = TactixLibrary.byUS(vacuousBoxAssignNondetAxiom)

  /**
    * {{{Axiom "V<:*> vacuous assign nondet".
    *    <x:=*;>p() <-> p()
    * End.
    * }}}
    *
    * @todo reorient
    * @Derived
    */
  lazy val vacuousDiamondAssignNondetF = "(<x_:=*;>p_()) <-> p_()".asFormula
  lazy val vacuousDiamondAssignNondetAxiom = derivedAxiom("V<:*> vacuous assign nondet",
    Sequent(IndexedSeq(), IndexedSeq(vacuousDiamondAssignNondetF)),
    useAt(nondetassigndAxiom)(1, 0::Nil) &
      useAt(vacuousExistsAxiom)(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val vacuousDiamondAssignNondetT = TactixLibrary.byUS(vacuousDiamondAssignNondetAxiom)


  /**
    * {{{Axiom "Domain Constraint Conjunction Reordering".
    *    [{c & (H(??) & q(??))}]p(??) <-> [{c & (q(??) & H(??))}]p(??)
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val domainCommuteF = "[{c_ & (H_(??) & q_(??))}]p_(??) <-> [{c_ & (q_(??) & H_(??))}]p_(??)".asFormula
  lazy val domainCommute = derivedAxiom("Domain Constraint Conjunction Reordering",
    Sequent(IndexedSeq(), IndexedSeq(domainCommuteF)),
    useAt(andCommute)(1, 0::0::1::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val domainCommuteT = TactixLibrary.byUS(domainCommute)

  /**
    * {{{Axiom "[] post weaken".
    *   [a;]p(??)  ->  [a;](q(??)->p(??))
    * End.
    * }}}
    *
    * @Derived from M (or also from K)
    */
  lazy val postconditionWeakenF = "([a_;]p_(??))  ->  [a_;](q_(??)->p_(??))".asFormula
  lazy val postconditionWeaken = derivedAxiom("[] post weaken",
    Sequent(IndexedSeq(), IndexedSeq(postconditionWeakenF)),
    implyR(1) & monb & prop
  )
  lazy val postconditionWeakenT = TactixLibrary.byUS(postconditionWeaken)


  /**
    * {{{Axiom "& commute".
    *    (p() & q()) <-> (q() & p())
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val andCommuteF = "(p_() & q_()) <-> (q_() & p_())".asFormula
  lazy val andCommute = derivedAxiom("& commute", Sequent(IndexedSeq(), IndexedSeq(andCommuteF)), prop)
  lazy val andCommuteT = TactixLibrary.byUS(andCommute)

  /**
    * {{{Axiom "& associative".
    *    ((p() & q()) & r()) <-> (p() & (q() & r()))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val andAssocF = "((p_() & q_()) & r_()) <-> (p_() & (q_() & r_()))".asFormula
  lazy val andAssoc = derivedAxiom("& associative", Sequent(IndexedSeq(), IndexedSeq(andAssocF)), prop)
  lazy val andAssocT = TactixLibrary.byUS(andAssoc)

  /**
    * {{{Axiom "& reflexive".
    *    p() & p() <-> p()
    * End.
    * }}}
    */
  lazy val andReflexiveF = "p_() & p_() <-> p_()".asFormula
  lazy val andReflexive = derivedAxiom("& reflexive", Sequent(IndexedSeq(), IndexedSeq(andReflexiveF)), prop)
  lazy val andReflexiveT = TactixLibrary.byUS(andReflexive)

  /**
    * {{{Axiom "<-> true".
    *    (p() <-> true) <-> p()
    * End.
    * }}}
    */
  lazy val equivTrueF = "(p() <-> true) <-> p()".asFormula
  lazy val equivTrue = derivedAxiom("<-> true", Sequent(IndexedSeq(), IndexedSeq(equivTrueF)), prop)
  lazy val equivTrueT = TactixLibrary.byUS(equivTrue)

  /**
    * {{{Axiom "-> self".
    *    (p() -> p()) <-> true
    * End.
    * }}}
    */
  lazy val implySelfF = "(p_() -> p_()) <-> true".asFormula
  lazy val implySelf = derivedAxiom("-> self", Sequent(IndexedSeq(), IndexedSeq(implySelfF)), prop)
  lazy val implySelfT = TactixLibrary.byUS(implySelf)

  /**
    * {{{Axiom "!& deMorgan".
    *    (!(p() & q())) <-> ((!p()) | (!q()))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val notAndF = "(!(p_() & q_())) <-> ((!p_()) | (!q_()))".asFormula
  lazy val notAnd = derivedAxiom("!& deMorgan", Sequent(IndexedSeq(), IndexedSeq(notAndF)), prop)
  lazy val notAndT = TactixLibrary.byUS(notAnd)

  /**
    * {{{Axiom "!| deMorgan".
    *    (!(p() | q())) <-> ((!p()) & (!q()))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val notOrF = "(!(p_() | q_())) <-> ((!p_()) & (!q_()))".asFormula
  lazy val notOr = derivedAxiom("!| deMorgan", Sequent(IndexedSeq(), IndexedSeq(notOrF)), prop)
  lazy val notOrT = TactixLibrary.byUS(notOr)

  /**
    * {{{Axiom "!-> deMorgan".
    *    (!(p() -> q())) <-> ((p()) & (!q()))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val notImplyF = "(!(p_() -> q_())) <-> ((p_()) & (!q_()))".asFormula
  lazy val notImply = derivedAxiom("!-> deMorgan", Sequent(IndexedSeq(), IndexedSeq(notImplyF)), prop)
  lazy val notImplyT = TactixLibrary.byUS(notImply)

  /**
    * {{{Axiom "!<-> deMorgan".
    *    (!(p() <-> q())) <-> (((p()) & (!q())) | ((!p()) & (q())))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val notEquivF = "(!(p_() <-> q_())) <-> (((p_()) & (!q_())) | ((!p_()) & (q_())))".asFormula
  lazy val notEquiv = derivedAxiom("!<-> deMorgan", Sequent(IndexedSeq(), IndexedSeq(notEquivF)), prop)
  lazy val notEquivT = TactixLibrary.byUS(notEquiv)

  /**
    * {{{Axiom "-> expand".
    *    (p() -> q()) <-> ((!p()) | q())
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val implyExpandF = "(p_() -> q_()) <-> ((!p_()) | q_())".asFormula
  lazy val implyExpand = derivedAxiom("-> expand", Sequent(IndexedSeq(), IndexedSeq(implyExpandF)), prop)
  lazy val implyExpandT = TactixLibrary.byUS(implyExpand)

  /**
    * {{{Axiom "PC1".
    *    p()&q() -> p()
    * End.
    * }}}
    *
    * @Derived
    * @Note implements Cresswell, Hughes. A New Introduction to Modal Logic, PC1
    */
  lazy val PC1F = "p_()&q_() -> p_()".asFormula
  lazy val PC1 = derivedAxiom("PC1", Sequent(IndexedSeq(), IndexedSeq(PC1F)), prop)
  lazy val PC1T = TactixLibrary.byUS(PC1)

  /**
    * {{{Axiom "PC2".
    *    p()&q() -> q()
    * End.
    * }}}
    *
    * @Derived
    * @Note implements Cresswell, Hughes. A New Introduction to Modal Logic, PC2
    */
  lazy val PC2F = "p_()&q_() -> q_()".asFormula
  lazy val PC2 = derivedAxiom("PC2", Sequent(IndexedSeq(), IndexedSeq(PC2F)), prop)
  lazy val PC2T = TactixLibrary.byUS(PC2)

  /**
    * {{{Axiom "PC3".
    *    p()&q() -> ((p()->r())->(p()->q()&r())) <-> true
    * End.
    * }}}
    *
    * @Derived
    * @Note implements Cresswell, Hughes. A New Introduction to Modal Logic, PC3
    */
  lazy val PC3F = "p_()&q_() -> ((p_()->r_())->(p_()->q_()&r_())) <-> true".asFormula
  lazy val PC3 = derivedAxiom("PC3", Sequent(IndexedSeq(), IndexedSeq(PC3F)), prop)
  lazy val PC3T = TactixLibrary.byUS(PC3)

  /**
    * {{{Axiom "PC9".
    *    p() -> p() | q()
    * End.
    * }}}
    *
    * @Derived
    * @Note implements Cresswell, Hughes. A New Introduction to Modal Logic, PC9
    */
  lazy val PC9F = "p_() -> p_() | q_()".asFormula
  lazy val PC9 = derivedAxiom("PC9", Sequent(IndexedSeq(), IndexedSeq(PC9F)), prop)
  lazy val PC9T = TactixLibrary.byUS(PC9)

  /**
    * {{{Axiom "PC10".
    *    q() -> p() | q()
    * End.
    * }}}
    *
    * @Derived
    * @Note implements Cresswell, Hughes. A New Introduction to Modal Logic, PC10
    */
  lazy val PC10F = "q_() -> p_() | q_()".asFormula
  lazy val PC10 = derivedAxiom("PC10", Sequent(IndexedSeq(), IndexedSeq(PC10F)), prop)
  lazy val PC10T = TactixLibrary.byUS(PC10)

  /**
    * {{{Axiom "-> tautology".
    *    (p() -> (q() -> p()&q())) <-> true
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val implyTautologyF = "(p_() -> (q_() -> p_()&q_())) <-> true".asFormula
  lazy val implyTautology = derivedAxiom("-> tautology", Sequent(IndexedSeq(), IndexedSeq(implyTautologyF)), prop)
  lazy val implyTautologyT = TactixLibrary.byUS(implyTautology)

  /**
    * {{{Axiom "->' derive imply".
    *    (p(??) -> q(??))' <-> (!p(??) | q(??))'
    * End.
    * }}}
    *
    * @Derived by CE
    */
  lazy val DimplyF = "(p_(??) -> q_(??))' <-> (!p_(??) | q_(??))'".asFormula
  lazy val Dimply = derivedAxiom("->' derive imply",
    Sequent(IndexedSeq(), IndexedSeq(DimplyF)),
    useAt(implyExpand)(1, 0::0::Nil) &
      byUS(equivReflexiveAxiom)
  )
  lazy val DimplyT = TactixLibrary.byUS(Dimply)

  /**
    * {{{Axiom "\forall->\exists".
    *    (\forall x p(x)) -> (\exists x p(x))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val forallThenExistsF = "(\\forall x_ p_(x_)) -> (\\exists x_ p_(x_))".asFormula
  lazy val forallThenExistsAxiom = derivedAxiom("\\forall->\\exists",
    Sequent(IndexedSeq(), IndexedSeq(forallThenExistsF)),
    implyR(1) &
      useAt(existsGeneralize, PosInExpr(1::Nil))(1) &
      useAt("all instantiate")(-1) &
      closeId
  )
  lazy val forallThenExistsT = TactixLibrary.byUS(forallThenExistsAxiom)

  /**
    * {{{Axiom "->true".
    *    (p()->true) <-> true
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val impliesTrueF = "(p_()->true) <-> true".asFormula
  lazy val impliesTrue = derivedAxiom("->true", Sequent(IndexedSeq(), IndexedSeq(impliesTrueF)), prop)
  lazy val impliesTrueT = TactixLibrary.byUS(impliesTrue)

  /**
    * {{{Axiom "true->".
    *    (true->p()) <-> p()
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val trueImpliesF = "(true->p_()) <-> p_()".asFormula
  lazy val trueImplies = derivedAxiom("true->", Sequent(IndexedSeq(), IndexedSeq(trueImpliesF)), prop)
  lazy val trueImpliesT = TactixLibrary.byUS(trueImplies)

  /**
   * {{{Axiom "&true".
   *    (p()&true) <-> p()
   * End.
   * }}}
    *
    * @Derived
   */
  lazy val andTrueF = "(p_()&true) <-> p_()".asFormula
  lazy val andTrue = derivedAxiom("&true", Sequent(IndexedSeq(), IndexedSeq(andTrueF)), prop)
  lazy val andTrueT = TactixLibrary.byUS(andTrue)

  /**
   * {{{Axiom "true&".
   *    (true&p()) <-> p()
   * End.
   * }}}
    *
    * @Derived
   */
  lazy val trueAndF = "(true&p_()) <-> p_()".asFormula
  lazy val trueAnd = derivedAxiom("true&", Sequent(IndexedSeq(), IndexedSeq(trueAndF)), prop)
  lazy val trueAndT = TactixLibrary.byUS(trueAnd)

  /**
   * {{{Axiom "0*".
   *    (0*f()) = 0
   * End.
   * }}}
    *
    * @Derived
   */
  lazy val zeroTimesF = "(0*f()) = 0".asFormula
  lazy val zeroTimes = derivedAxiom("0*", Sequent(IndexedSeq(), IndexedSeq(zeroTimesF)), QE)
  lazy val zeroTimesT = TactixLibrary.byUS(zeroTimes)

  /**
    * {{{Axiom "*0".
    *    (f()*0) = 0
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val timesZeroF = "(f()*0) = 0".asFormula
  lazy val timesZero = derivedAxiom("*0", Sequent(IndexedSeq(), IndexedSeq(timesZeroF)),
    if (false) useAt(timesCommutative)(1, 0::Nil) &
      byUS(zeroTimes)
    else QE
  )
  lazy val timesZeroT = TactixLibrary.byUS(timesZero)

  /**
   * {{{Axiom "0+".
   *    (0+f()) = f()
   * End.
   * }}}
    *
    * @Derived
   */
  lazy val zeroPlusF = "(0+f()) = f()".asFormula
  lazy val zeroPlus = derivedAxiom("0+", Sequent(IndexedSeq(), IndexedSeq(zeroPlusF)), QE)
  lazy val zeroPlusT = TactixLibrary.byUS(zeroPlus)

  /**
    * {{{Axiom "+0".
    *    (f()+0) = f()
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val plusZeroF = "(f()+0) = f()".asFormula
  lazy val plusZero = derivedAxiom("+0", Sequent(IndexedSeq(), IndexedSeq(plusZeroF)),
    if (false) useAt(plusCommutative)(1, 0::Nil) &
      byUS(zeroPlus)
    else QE
  )
  lazy val plusZeroT = TactixLibrary.byUS(plusZero)


  // differential equations

  /**
    * {{{Axiom "DW differential weakening".
    *    [{c&H(??)}]p(??) <-> ([{c&H(??)}](H(??)->p(??)))
    *    /* [x'=f(x)&q(x);]p(x) <-> ([x'=f(x)&q(x);](q(x)->p(x))) THEORY */
    * End.
    * }}}
    *
    * @see footnote 3 in "Andre Platzer. A uniform substitution calculus for differential dynamic logic. In Amy P. Felty and Aart Middeldorp, editors, International Conference on Automated Deduction, CADE'15, Berlin, Germany, Proceedings, volume 9195 of LNCS, pages 467-481. Springer, 2015. arXiv 1503.01981, 2015."
    */
  lazy val DWeakeningF = "[{c_&H_(??)}]p_(??) <-> ([{c_&H_(??)}](H_(??)->p_(??)))".asFormula
  lazy val DWeakening = derivedAxiom("DW differential weakening",
    Sequent(IndexedSeq(), IndexedSeq(DWeakeningF)),
    equivR(1) <(
      /* equiv left */
      cut("[{c_&H_(??)}](p_(??)->(H_(??)->p_(??)))".asFormula) <(
        /* use */ useAt("K modal modus ponens", PosInExpr(0::Nil))(-2) & implyL(-2) <(close, close),
        /* show */ cohide(2) & G & prop
        ),
      /* equiv right */
      useAt("K modal modus ponens", PosInExpr(0::Nil))(-1) & implyL(-1) <(cohide(2) & byUS("DW"), close)
      )
  )
  lazy val DWeakeningT = TactixLibrary.byUS(DWeakening)

  /**
    * {{{Axiom "DI differential invariance".
    *  ([{c&H(??)}]p(??) <-> [?H(??);]p(??)) <- (H(??) -> [{c&H(??)}]((p(??))'))
    *  //([x'=f(x)&q(x);]p(x) <-> [?q(x);]p(x)) <- (q(x) -> [x'=f(x)&q(x);]((p(x))')) THEORY
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val DIinvarianceF = "([{c&H(??)}]p(??) <-> [?H(??);]p(??)) <- (H(??) -> [{c&H(??)}]((p(??))'))".asFormula
  lazy val DIinvariance = derivedAxiom("DI differential invariance",
    Sequent(IndexedSeq(), IndexedSeq(DIinvarianceF)),
    implyR(1) & equivR(1) <(
      testb(1) &
        useAt("DX differential skip")(-2) &
        close
      ,
      testb(-2) &
        useAt("DI differential invariant")(1) &
        prop & onAll(close)
    )
  )
  lazy val DIinvarianceT = TactixLibrary.byUS(DIinvariance)

  /**
    * {{{Axiom "DI differential invariant".
    *    [{c&H(??)}]p(??) <- (H(??)-> (p(??) & [{c&H(??)}]((p(??))')))
    *    // [x'=f(x)&q(x);]p(x) <- (q(x) -> (p(x) & [x'=f(x)&q(x);]((p(x))'))) THEORY
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val DIinvariantF = "[{c&H(??)}]p(??) <- (H(??)-> (p(??) & [{c&H(??)}]((p(??))')))".asFormula
  lazy val DIinvariant = derivedAxiom("DI differential invariant",
    Sequent(IndexedSeq(), IndexedSeq(DIinvariantF)),
    implyR(1) & useAt(implyDistAndAxiom, PosInExpr(0::Nil))(-1) & andL(-1) &
      useAt("[?] test", PosInExpr(1::Nil))(-1) &
      cut(DIinvarianceF) <(
        prop & onAll(close)
        ,
        cohide(2) & by(DIinvariance)
        )
  )
  lazy val DIinvariantT = TactixLibrary.byUS(DIinvariant)

  /**
    * {{{Axiom "DX diamond differential skip".
    *    <{c&H(??)}>p(??) <- H(??)&p(??)
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val DskipdF = "<{c&H(??)}>p(??) <- H(??)&p(??)".asFormula
  lazy val Dskipd = derivedAxiom("DX diamond differential skip",
    Sequent(IndexedSeq(), IndexedSeq(DskipdF)),
    useAt(doubleNegationAxiom, PosInExpr(1::Nil))(1, 0::Nil) &
      useAt(notAnd)(1, 0::0::Nil) &
      useAt(implyExpand, PosInExpr(1::Nil))(1, 0::0::Nil) &
      useAt("DX differential skip", PosInExpr(1::Nil))(1, 0::0::Nil) &
      useAt("<> diamond")(1, 0::Nil) & implyR(1) & close
  )
  lazy val DskipdT = TactixLibrary.byUS(Dskipd)

  /**
    * {{{Axiom "DS differential equation solution".
    *    [{x'=c()}]p(x) <-> \forall t (t>=0 -> [x:=x+(c()*t);]p(x))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val DSnodomainF = "[{x_'=c_()}]p_(x_) <-> \\forall t_ (t_>=0 -> [x_:=x_+(c_()*t_);]p_(x_))".asFormula
  lazy val DSnodomain = derivedAxiom("DS differential equation solution",
    Sequent(IndexedSeq(), IndexedSeq(DSnodomainF)),
    useAt("DS& differential equation solution")(1, 0::Nil) &
      useAt(impliesTrue)(1, 0::0::1::0::0::Nil) &
      useAt("vacuous all quantifier")(1, 0::0::1::0::Nil) &
      useAt(trueImplies)(1, 0::0::1::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val DSnodomainT = TactixLibrary.byUS(DSnodomain)

  /**
    * {{{Axiom "Dsol differential equation solution".
    *    <{x'=c()}>p(x) <-> \exists t (t>=0 & <x:=x+(c()*t);>p(x))
    * End.
    * }}}
    *
    * @Derived
    */
  lazy val DSdnodomainF = "<{x_'=c_()}>p_(x_) <-> \\exists t_ (t_>=0 & <x_:=x_+(c_()*t_);>p_(x_))".asFormula
  lazy val DSdnodomain = derivedAxiom("Dsol differential equation solution",
    Sequent(IndexedSeq(), IndexedSeq(DSdnodomainF)),
    useAt("Dsol& differential equation solution")(1, 0::Nil) &
      useAt(impliesTrue)(1, 0::0::1::0::0::Nil) &
      useAt("vacuous all quantifier")(1, 0::0::1::0::Nil) &
      useAt(trueAnd)(1, 0::0::1::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val DSdnodomainT = TactixLibrary.byUS(DSdnodomain)

  /**
    * {{{Axiom "Dsol& differential equation solution".
    *    <{x'=c()&q(x)}>p(x) <-> \exists t (t>=0 & ((\forall s ((0<=s&s<=t) -> q(x+(c()*s)))) & <x:=x+(c()*t);>p(x)))
    * End.
    * }}}
    */
  lazy val DSddomainF = "<{x_'=c()&q(x_)}>p(x_) <-> \\exists t_ (t_>=0 & ((\\forall s_ ((0<=s_&s_<=t_) -> q(x_+(c()*s_)))) & <x_:=x_+(c()*t_);>p(x_)))".asFormula
  lazy val DSddomain = derivedAxiom("Dsol& differential equation solution",
    Sequent(IndexedSeq(), IndexedSeq(DSddomainF)),
    useAt("<> diamond", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("DS& differential equation solution")(1, 0::0::Nil) &
      useAt(notAll)(1, 0::Nil) & //step(1, 0::Nil) &
      useAt(notImply)(1, 0::0::Nil) &
      useAt(notImply)(1, 0::0::1::Nil) &
      useAt("<> diamond")(1, 0::0::1::1::Nil) &
      //useAt("& associative", PosInExpr(1::Nil))(1, 0::0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val DSddomainT = TactixLibrary.byUS(DSddomain)

  //  lazy val existsDualAxiom: LookupLemma = derivedAxiom("exists dual",
  //    Provable.startProof(Sequent(Nil, IndexedSeq(), IndexedSeq("\\exists x q(x) <-> !(\\forall x (!q(x)))".asFormula)))
  //      (CutRight("\\exists x q(x) <-> !!(\\exists x (!!q(x)))".asFormula, SuccPos(0)), 0)
  //      // right branch
  //      (EquivifyRight(SuccPos(0)), 1)
  //      (AxiomaticRule("CE congruence", USubst(
  //        SubstitutionPair(PredicationalOf(context, DotFormula), "\\exists x q(x) <-> !⎵".asFormula) ::
  //          SubstitutionPair(pany, "!\\exists x !!q(x)".asFormula) ::
  //          SubstitutionPair(qany, "\\forall x !q(x)".asFormula) :: Nil
  //      )), 1)
  //      (CommuteEquivRight(SuccPos(0)), 1)
  //      (Axiom("all dual"), 1)
  //      (Close(AntePos(0),SuccPos(0)), 1)
  //  )


  /**
    * {{{Axiom "DG differential pre-ghost".
    *    [{c&H(??)}]p(??) <-> \exists y [{y'=(t()*y)+s(),c&H(??)}]p(??)
    *    // [x'=f(x)&q(x);]p(x) <-> \exists y [{y'=(a(x)*y)+b(x), x'=f(x))&q(x)}]p(x) THEORY
    * End.
    * }}}
    * Pre Differential Auxiliary / Differential Ghost -- not strictly necessary but saves a lot of reordering work.
    */
  lazy val DGpreghostF = "[{c&H(??)}]p(??) <-> \\exists y_ [{y_'=(t()*y_)+s(),c&H(??)}]p(??)".asFormula
  lazy val DGpreghost = derivedAxiom("DG differential pre-ghost",
    Sequent(IndexedSeq(), IndexedSeq(DGpreghostF)),
    useAt("DG differential ghost")(1, 0::Nil) &
      useAt(", commute")(1, 0::0::Nil) &
      byUS(equivReflexiveAxiom)
  )
  lazy val DGpreghostT = TactixLibrary.byUS(DGpreghost)

  /**
    * {{{Axiom "x' derive var commuted".
    *    (x_') = (x_)'
    * End.
    * }}}
    */
  lazy val DvariableCommutedF = "(x_') = (x_)'".asFormula
  lazy val DvariableCommuted = derivedAxiom("x' derive var commuted",
    Sequent(IndexedSeq(), IndexedSeq(DvariableCommutedF)),
    useAt(equalCommute)(1) &
      byUS("x' derive var")
  )
  lazy val DvariableCommutedT = TactixLibrary.byUS(DvariableCommuted)

  /**
    * {{{Axiom "x' derive variable".
    *    \forall x_ ((x_)' = x_')
    * End.
    * }}}
    */
  lazy val DvariableF = "\\forall x_ ((x_)' = x_')".asFormula
  lazy val Dvariable = derivedAxiom("x' derive variable",
    Provable.startProof(Sequent(IndexedSeq(), IndexedSeq(DvariableF)))
    (Skolemize(SuccPos(0)), 0)
    (Provable.axioms("x' derive var"), 0)
  )
  lazy val DvariableT = TactixLibrary.byUS(Dvariable)
  //  /**
  //   * {{{Axiom "x' derive var".
  //   *    (x_)' = x_'
  //   * End.
  //   * }}}
  //   * @todo derive
  //   */
  //  lazy val DvarF = "((x_)' = x_')".asFormula
  //  lazy val Dvar = derivedAxiom("'x derive var",
  //    Provable.startProof(Sequent(Nil, IndexedSeq(), IndexedSeq(DvarF)))
  //      (CutRight("\\forall x_ ((x_)' = x_')".asFormula, SuccPos(0)), 0)
  //      // right branch
  //      (UniformSubstitutionRule.UniformSubstitutionRuleForward(Axiom.axiom("all eliminate"),
  //        USubst(SubstitutionPair(PredOf(Function("p_",None,Real,Bool),Anything), DvarF)::Nil)), 0)
  //      // left branch
  //      (Axiom.axiom("x' derive variable"), 0)
  //    /*TacticLibrary.instantiateQuanT(Variable("x_",None,Real), Variable("x",None,Real))(1) &
  //      byUS("= reflexive")*/
  //  )
  //  lazy val DvarT = TactixLibrary.byUS(Dvar)
  /**
    * {{{Axiom "' linear".
    *    (c()*f(??))' = c()*(f(??))'
    * End.
    * }}}
    */
  lazy val DlinearF = "(c_()*f_(??))' = c_()*(f_(??))'".asFormula
  lazy val Dlinear = derivedAxiom("' linear",
    Sequent(IndexedSeq(), IndexedSeq(DlinearF)),
    useAt("*' derive product")(1, 0::Nil) &
      useAt("c()' derive constant fn")(1, 0::0::0::Nil) &
      useAt(zeroTimes)(1, 0::0::Nil) &
      useAt(zeroPlus)(1, 0::Nil) &
      byUS(equalReflex)
  )
  lazy val DlinearT = TactixLibrary.byUS(Dlinear)
  /**
    * {{{Axiom "' linear right".
    *    (f(??)*c())' = f(??)'*c()
    * End.
    * }}}
    */
  lazy val DlinearRightF = "(f(??)*c())' = (f(??))'*c()".asFormula
  lazy val DlinearRight = derivedAxiom("' linear right",
    Sequent(IndexedSeq(), IndexedSeq(DlinearRightF)),
    useAt("*' derive product")(1, 0:: Nil) &
      useAt("c()' derive constant fn")(1, 0:: 1::1::Nil) &
      useAt(timesZero)(1, 0:: 1::Nil) &
      useAt(plusZero)(1, 0:: Nil) &
      byUS(equalReflex)
  )
  //@note elegant proof that clashes for some reason
  //  derivedAxiom("' linear right",
  //  Sequent(Nil, IndexedSeq(), IndexedSeq(DlinearRightF)),
  //  useAt("* commute")(1, 0::0::Nil) &
  //    useAt("* commute")(1, 1::Nil) &
  //    by(Dlinear)
  //)
  lazy val DlinearRightT = TactixLibrary.byUS(DlinearRight)

  // real arithmetic

  /**
   * {{{Axiom "= reflexive".
   *    s() = s()
   * End.
   * }}}
   */
  lazy val equalReflexiveF = "s_() = s_()".asFormula
  lazy val equalReflex = derivedAxiom("= reflexive", Sequent(IndexedSeq(), IndexedSeq(equalReflexiveF)), QE)
  lazy val equalReflexiveT = TactixLibrary.byUS(equalReflex)

  /**
    * {{{Axiom "= commute".
    *   (f()=g()) <-> (g()=f())
    * End.
    * }}}
    */
  lazy val equalCommuteF = "(f_()=g_()) <-> (g_()=f_())".asFormula
  lazy val equalCommute = derivedAxiom("= commute", Sequent(IndexedSeq(), IndexedSeq(equalCommuteF)), QE)
  lazy val equalCommuteT = TactixLibrary.byUS(equalCommute)

  /**
    * {{{Axiom "* commute".
    *   (f()*g()) = (g()*f())
    * End.
    * }}}
    */
  lazy val timesCommuteF = "(f_()*g_()) = (g_()*f_())".asFormula
  lazy val timesCommute = derivedAxiom("* commute", Sequent(IndexedSeq(), IndexedSeq(timesCommuteF)), QE)
  lazy val timesCommuteT = TactixLibrary.byUS(timesCommute)

  /**
    * {{{Axiom "<=".
    *   (f()<=g()) <-> ((f()<g()) | (f()=g()))
    * End.
    * }}}
    */
  lazy val lessEqualF = "(f_()<=g_()) <-> ((f_()<g_()) | (f_()=g_()))".asFormula
  lazy val lessEqual = derivedAxiom("<=", Sequent(IndexedSeq(), IndexedSeq(lessEqualF)), QE)
  lazy val lessEqualT = TactixLibrary.byUS(lessEqual)

  /**
    * {{{Axiom "! !=".
    *   (!(f() != g())) <-> (f() = g())
    * End.
    * }}}
    */
  lazy val notNotEqualF = "(!(f_() != g_())) <-> (f_() = g_())".asFormula
  lazy val notNotEqual = derivedAxiom("! !=", Sequent(IndexedSeq(), IndexedSeq(notNotEqualF)), QE)
  lazy val notNotEqualT = TactixLibrary.byUS(notNotEqual)

  /**
    * {{{Axiom "! =".
    *   !(f() = g()) <-> f() != g()
    * End.
    * }}}
    */
  lazy val notEqualF = "(!(f() = g())) <-> (f() != g())".asFormula
  lazy val notEqual = derivedAxiom("! =", Sequent(IndexedSeq(), IndexedSeq(notEqualF)), QE)
  lazy val notEqualT = TactixLibrary.byUS(notEqual)

  /**
    * {{{Axiom "! >".
    *   (!(f() > g())) <-> (f() <= g())
    * End.
    * }}}
    */
  lazy val notGreaterF = "(!(f() > g())) <-> (f() <= g())".asFormula
  lazy val notGreater = derivedAxiom("! >", Sequent(IndexedSeq(), IndexedSeq(notGreaterF)), QE)
  lazy val notGreaterT = TactixLibrary.byUS(notGreater)

  /**
    * {{{Axiom "! <".
    *   (!(f() < g())) <-> (f() >= g())
    * End.
    * }}}
    *
    * @todo derive more efficiently via flip
    */
  lazy val notLessF = "(!(f() < g())) <-> (f() >= g())".asFormula
  lazy val notLess = derivedAxiom("! <", Sequent(IndexedSeq(), IndexedSeq(notLessF)), QE)
  lazy val notLessT = TactixLibrary.byUS(notLess)

  /**
    * {{{Axiom "! <=".
    *   (!(f() <= g())) <-> (f() > g())
    * End.
    * }}}
    *
    * @todo derive more efficiently via flip
    */
  lazy val notLessEqualF = "(!(f() <= g())) <-> (f() > g())".asFormula
  lazy val notLessEqual = derivedAxiom("! <=", Sequent(IndexedSeq(), IndexedSeq(notLessEqualF)), QE)
  lazy val notLessEqualT = TactixLibrary.byUS(notLessEqual)

  /**
    * {{{Axiom "< negate".
    *   (!(f() >= g())) <-> (f() < g())
    * End.
    * }}}
    */
  lazy val notGreaterEqualF = "(!(f() >= g())) <-> (f() < g())".asFormula
  lazy val notGreaterEqual = derivedAxiom("< negate", Sequent(IndexedSeq(), IndexedSeq(notGreaterEqualF)), QE)
  lazy val notGreaterEqualT = TactixLibrary.byUS(notGreaterEqual)

  /**
    * {{{Axiom ">= flip".
    *   (f() >= g()) <-> (g() <= f())
    * End.
    * }}}
    */
  lazy val flipGreaterEqualF = "(f_() >= g_()) <-> (g_() <= f_())".asFormula
  lazy val flipGreaterEqual = derivedAxiom(">= flip", Sequent(IndexedSeq(), IndexedSeq(flipGreaterEqualF)), QE)
  lazy val flipGreaterEqualT = TactixLibrary.byUS(flipGreaterEqual)

  /**
    * {{{Axiom "> flip".
    *   (f() > g()) <-> (g() < f())
    * End.
    * */
  lazy val flipGreaterF = "(f_() > g_()) <-> (g_() < f_())".asFormula
  lazy val flipGreater = derivedAxiom("> flip", Sequent(IndexedSeq(), IndexedSeq(flipGreaterF)), QE)
  lazy val flipGreaterT = TactixLibrary.byUS(flipGreater)

  /**
    * {{{Axiom "+ associative".
    *    (f()+g()) + h() = f() + (g()+h())
    * End.
    * }}}
    */
  lazy val plusAssociativeF = "(f_() + g_()) + h_() = f_() + (g_() + h_())".asFormula
  lazy val plusAssociative = derivedAxiom("+ associative", Sequent(IndexedSeq(), IndexedSeq(plusAssociativeF)), QE)
  lazy val plusAssociativeT = TactixLibrary.byUS(plusAssociative)

  /**
    * {{{Axiom "* associative".
    *    (f()*g()) * h() = f() * (g()*h())
    * End.
    * }}}
    */
  lazy val timesAssociativeF = "(f_() * g_()) * h_() = f_() * (g_() * h_())".asFormula
  lazy val timesAssociative = derivedAxiom("* associative", Sequent(IndexedSeq(), IndexedSeq(timesAssociativeF)), QE)
  lazy val timesAssociativeT = TactixLibrary.byUS(timesAssociative)

  /**
    * {{{Axiom "+ commute".
    *    f()+g() = g()+f()
    * End.
    * }}}
    */
  lazy val plusCommutativeF = "f_()+g_() = g_()+f_()".asFormula
  lazy val plusCommutative = derivedAxiom("+ commute", Sequent(IndexedSeq(), IndexedSeq(plusCommutativeF)), QE)
  lazy val plusCommutativeT = TactixLibrary.byUS(plusCommutative)

  /**
    * {{{Axiom "* commute".
    *    f()*g() = g()*f()
    * End.
    * }}}
    */
  lazy val timesCommutativeF = "f_()*g_() = g_()*f_()".asFormula
  lazy val timesCommutative = derivedAxiom("* commute", Sequent(IndexedSeq(), IndexedSeq(timesCommutativeF)), QE)
  lazy val timesCommutativeT = TactixLibrary.byUS(timesCommutative)

  /**
    * {{{Axiom "distributive".
    *    f()*(g()+h()) = f()*g() + f()*h()
    * End.
    * }}}
    */
  lazy val distributiveF = "f_()*(g_()+h_()) = f_()*g_() + f_()*h_()".asFormula
  lazy val distributive = derivedAxiom("distributive", Sequent(IndexedSeq(), IndexedSeq(distributiveF)), QE)
  lazy val distributiveT = TactixLibrary.byUS(distributive)

  /**
    * {{{Axiom "+ identity".
    *    f()+0 = f()
    * End.
    * }}}
    */
  lazy val plusIdentityF = zeroPlusF
  lazy val plusIdentity = zeroPlus
  lazy val plusIdentityT = zeroPlusT

  /**
    * {{{Axiom "* identity".
    *    f()*1 = f()
    * End.
    * }}}
    */
  lazy val timesIdentityF = "f_()*1 = f_()".asFormula
  lazy val timesIdentity = derivedAxiom("* identity", Sequent(IndexedSeq(), IndexedSeq(timesIdentityF)), QE)
  lazy val timesIdentityT = TactixLibrary.byUS(timesIdentity)

  /**
    * {{{Axiom "+ inverse".
    *    f() + (-f()) = 0
    * End.
    * }}}
    */
  lazy val plusInverseF = "f_() + (-f_()) = 0".asFormula
  lazy val plusInverse = derivedAxiom("+ inverse", Sequent(IndexedSeq(), IndexedSeq(plusInverseF)), QE)
  lazy val plusInverseT = TactixLibrary.byUS(plusInverse)

  /**
    * {{{Axiom "* inverse".
    *    f() != 0 -> f()*(f()^-1) = 1
    * End.
    * }}}
    */
  lazy val timesInverseF = "f_() != 0 -> f_()*(f_()^-1) = 1".asFormula
  lazy val timesInverse = derivedAxiom("* inverse", Sequent(IndexedSeq(), IndexedSeq(timesInverseF)), QE)
  lazy val timesInverseT = TactixLibrary.byUS(timesInverse)

  /**
    * {{{Axiom "positivity".
    *    f() < 0 | f() = 0 | 0 < f()
    * End.
    * }}}
    */
  lazy val positivityF = "f_() < 0 | f_() = 0 | 0 < f_()".asFormula
  lazy val positivity = derivedAxiom("positivity", Sequent(IndexedSeq(), IndexedSeq(positivityF)), QE)
  lazy val positivityT = TactixLibrary.byUS(positivity)

  /**
    * {{{Axiom "+ closed".
    *    0 < f() & 0 < g() -> 0 < f()+g()
    * End.
    * }}}
    */
  lazy val plusClosedF = "0 < f_() & 0 < g_() -> 0 < f_()+g_()".asFormula
  lazy val plusClosed = derivedAxiom("+ closed", Sequent(IndexedSeq(), IndexedSeq(plusClosedF)), QE)
  lazy val plusClosedT = TactixLibrary.byUS(plusClosed)

  /**
    * {{{Axiom "* closed".
    *    0 < f() & 0 < g() -> 0 < f()*g()
    * End.
    * }}}
    */
  lazy val timesClosedF = "0 < f_() & 0 < g_() -> 0 < f_()*g_()".asFormula
  lazy val timesClosed = derivedAxiom("* closed", Sequent(IndexedSeq(), IndexedSeq(timesClosedF)), QE)
  lazy val timesClosedT = TactixLibrary.byUS(timesClosed)

  /**
    * {{{Axiom "<".
    *    f() < g() <-> 0 < g()-f()
    * End.
    * }}}
    */
  lazy val lessF = "f_() < g_() <-> 0 < g_()-f_()".asFormula
  lazy val less = derivedAxiom("<", Sequent(IndexedSeq(), IndexedSeq(lessF)), QE)
  lazy val lessT = TactixLibrary.byUS(less)

  /**
    * {{{Axiom ">".
    *    f() > g() <-> g() < f()
    * End.
    * }}}
    */
  lazy val greaterF = "f_() > g_() <-> g_() < f_()".asFormula
  lazy val greater = derivedAxiom(">", Sequent(IndexedSeq(), IndexedSeq(greaterF)), QE)
  lazy val greaterT = TactixLibrary.byUS(greater)

  // built-in arithmetic

  /**
    * {{{Axiom "!= elimination".
    *   f()!=g() <-> \exists z (f()-g())*z=1
    * End.
    * }}}
    * @see André Platzer, Jan-David Quesel, and Philipp Rümmer. Real world verification. CADE 2009.
    */
  lazy val notEqualElimF = "(f_()!=g_()) <-> \\exists z_ ((f_()-g_())*z_=1)".asFormula
  lazy val notEqualElim = derivedAxiom("!= elimination", Sequent(IndexedSeq(), IndexedSeq(notEqualElimF)), QE)
  lazy val notEqualElimT = TactixLibrary.byUS(notEqualElim)

  /**
    * {{{Axiom ">= elimination".
    *   f()>=g() <-> \exists z f()-g()=z^2
    * End.
    * }}}
    * @see André Platzer, Jan-David Quesel, and Philipp Rümmer. Real world verification. CADE 2009.
    */
  lazy val greaterEqualElimF = "(f_()>=g_()) <-> \\exists z_ (f_()-g_()=z_^2)".asFormula
  lazy val greaterEqualElim = derivedAxiom(">= elimination", Sequent(IndexedSeq(), IndexedSeq(greaterEqualElimF)), QE)
  lazy val greaterEqualElimT = TactixLibrary.byUS(greaterEqualElim)

  /**
    * {{{Axiom "> elimination".
    *   f()>g() <-> \exists z (f()-g())*z^2=1
    * End.
    * }}}
    * @see André Platzer, Jan-David Quesel, and Philipp Rümmer. Real world verification. CADE 2009.
    */
  lazy val greaterElimF = "(f_()>g_()) <-> \\exists z_ ((f_()-g_())*z_^2=1)".asFormula
  lazy val greaterElim = derivedAxiom("> elimination", Sequent(IndexedSeq(), IndexedSeq(greaterElimF)), QE)
  lazy val greaterElimT = TactixLibrary.byUS(greaterElim)

  /**
    * {{{Axiom "1>0".
    *   1>0
    * End.
    * }}}
    */
  lazy val oneGreaterZeroF = "1>0".asFormula
  lazy val oneGreaterZero = derivedAxiom("1>0", Sequent(IndexedSeq(), IndexedSeq(oneGreaterZeroF)), QE)
  lazy val oneGreaterZeroT = TactixLibrary.byUS(oneGreaterZero)

  /**
    * {{{Axiom "nonnegative squares".
    *   f()^2>=0
    * End.
    * }}}
    */
  lazy val nonnegativeSquaresF = "f_()^2>=0".asFormula
  lazy val nonnegativeSquares = derivedAxiom("nonnegative squares", Sequent(IndexedSeq(), IndexedSeq(nonnegativeSquaresF)), QE)
  lazy val nonnegativeSquaresT = TactixLibrary.byUS(nonnegativeSquares)

  /**
    * {{{Axiom ">2!=".
    *   f()>g() -> f()!=g()
    * End.
    * }}}
    */
  lazy val greaterImpliesNotEqualF = "f_()>g_() -> f_()!=g_()".asFormula
  lazy val greaterImpliesNotEqual = derivedAxiom(">2!=", Sequent(IndexedSeq(), IndexedSeq(greaterImpliesNotEqualF)), QE)
  lazy val greaterImpliesNotEqualT = TactixLibrary.byUS(greaterImpliesNotEqual)

  /**
    * {{{Axiom "> monotone".
    *   f()+h()>g() <- f()>g() & h()>=0
    * End.
    * }}}
    */
  lazy val greaterMonotoneF = "f_()+h_()>g_() <- f_()>g_() & h_()>=0".asFormula
  lazy val greaterMonotone = derivedAxiom("> monotone", Sequent(IndexedSeq(), IndexedSeq(greaterMonotoneF)), QE)
  lazy val greaterMonotoneT = TactixLibrary.byUS(greaterMonotone)

  // stuff

  /**
    * {{{Axiom "abs".
    *   (abs(s()) = t()) <->  ((s()>=0 & t()=s()) | (s()<0 & t()=-s()))
    * End.
    * }}}
    *
    * @Derived from built-in arithmetic abs in [[edu.cmu.cs.ls.keymaerax.tools.Mathematica]]
    */
  lazy val absF = "(abs(s_()) = t_()) <->  ((s_()>=0 & t_()=s_()) | (s_()<0 & t_()=-s_()))".asFormula
  lazy val absDef = derivedAxiom("abs", Sequent(IndexedSeq(), IndexedSeq(absF)), QE)
  lazy val absT = TactixLibrary.byUS(absDef)

  /**
    * {{{Axiom "min".
    *    (min(f(), g()) = h()) <-> ((f()<=g() & h()=f()) | (f()>g() & h()=g()))
    * End.
    * }}}
    *
    * @Derived from built-in arithmetic abs in [[edu.cmu.cs.ls.keymaerax.tools.Mathematica]]
    */
  lazy val minF = "(min(f_(), g_()) = h_()) <-> ((f_()<=g_() & h_()=f_()) | (f_()>g_() & h_()=g_()))".asFormula
  lazy val minDef = derivedAxiom("min", Sequent(IndexedSeq(), IndexedSeq(minF)), QE)
  lazy val minT = TactixLibrary.byUS(minDef)

  /**
    * {{{Axiom "max".
    *    (max(f(), g()) = h()) <-> ((f()>=g() & h()=f()) | (f()<g() & h()=g()))
    * End.
    * }}}
    *
    * @Derived from built-in arithmetic abs in [[edu.cmu.cs.ls.keymaerax.tools.Mathematica]]
    */
  lazy val maxF = "(max(f_(), g_()) = h_()) <-> ((f_()>=g_() & h_()=f_()) | (f_()<g_() & h_()=g_()))".asFormula
  lazy val maxDef = derivedAxiom("max", Sequent(IndexedSeq(), IndexedSeq(maxF)), QE)
  lazy val maxT = TactixLibrary.byUS(maxDef)

  /**
    * {{{Axiom "<*> stuck".
    *    <{a;}*>p(??) <-> <{a;}*>p(??)
    * End.
    * }}}
    *
    * @Derived
    * @note Trivial reflexive stutter axiom, only used with a different recursor pattern in AxiomIndex.
    */
  lazy val loopStuckF = "<{a_;}*>p_(??) <-> <{a_;}*>p_(??)".asFormula
  lazy val loopStuck = derivedAxiom("<*> stuck",
    Sequent(IndexedSeq(), IndexedSeq(loopStuckF)),
    byUS(equivReflexiveAxiom)
  )
  lazy val loopStuckT = TactixLibrary.byUS(loopStuck)

  /**
    * {{{Axiom "<'> stuck".
    *    <{c&H(??)}>p(??) <-> <{c&H(??)}>p(??)
    * End.
    * }}}
    *
    * @Derived
    * @note Trivial reflexive stutter axiom, only used with a different recursor pattern in AxiomIndex.
    */
  lazy val odeStuckF = "<{c_&H_(??)}>p_(??) <-> <{c_&H_(??)}>p_(??)".asFormula
  lazy val odeStuck = derivedAxiom("<'> stuck",
    Sequent(IndexedSeq(), IndexedSeq(odeStuckF)),
    byUS(equivReflexiveAxiom)
  )
  lazy val odeStuckT = TactixLibrary.byUS(odeStuck)

  /**
   * {{{Axiom "+<= up".
   *    f()+g()<=h() <- ((f()<=F() & g()<=G()) & F()+G()<=h())
   * End.
   * }}}
   */
  lazy val intervalUpPlusF = "f_()+g_()<=h_() <- ((f_()<=F_() & g_()<=G_()) & F_()+G_()<=h_())".asFormula
  lazy val intervalUpPlus = derivedAxiom("+<= up", Sequent(IndexedSeq(), IndexedSeq(intervalUpPlusF)), QE)
  lazy val intervalUpPlusT = TactixLibrary.byUS(intervalUpPlus)

  /**
   * {{{Axiom "-<= up".
   *    f()-g()<=h() <- ((f()<=F() & G()<=g()) & F()-G()<=h())
   * End.
   * }}}
   */
  lazy val intervalUpMinusF = "f_()-g_()<=h_() <- ((f_()<=F_() & G_()<=g_()) & F_()-G_()<=h_())".asFormula
  lazy val intervalUpMinus = derivedAxiom("-<= up", Sequent(IndexedSeq(), IndexedSeq(intervalUpMinusF)), QE)
  lazy val intervalUpMinusT = TactixLibrary.byUS(intervalUpMinus)


  /**
   * {{{Axiom "*<= up".
   *    f()*g()<=h() <- ((ff()<=f() & f()<=F() & gg()<=g() & g()<=G()) & (ff()*gg()<=h() & ff()*G()<=h() & F()*gg()<=h() & F()*G()<=h()))
   * End.
   * }}}
   */
  lazy val intervalUpTimesF = "f_()*g_()<=h_() <- ((ff_()<=f_() & f_()<=F_() & gg_()<=g_() & g_()<=G_()) & (ff_()*gg_()<=h_() & ff_()*G_()<=h_() & F_()*gg_()<=h_() & F_()*G_()<=h_()))".asFormula
  lazy val intervalUpTimes = derivedAxiom("*<= up", Sequent(IndexedSeq(), IndexedSeq(intervalUpTimesF)), QE)
  lazy val intervalUpTimesT = TactixLibrary.byUS(intervalUpTimes)


  /**
   * {{{Axiom "1Div<= up".
   *    1/f()<=h() <- ((F()<=f() & F()*f()>0) & (1/F()<=h()))
   * End.
   * }}}
   */
  lazy val intervalUp1DivideF = "1/f_()<=h_() <- ((F_()<=f_() & F_()*f_()>0) & (1/F_()<=h_()))".asFormula
  lazy val intervalUp1Divide = derivedAxiom("1Div<= up", Sequent(IndexedSeq(), IndexedSeq(intervalUp1DivideF)), QE)
  lazy val intervalUp1DivideT = TactixLibrary.byUS(intervalUp1Divide)


  /**
   * {{{Axiom "Div<= up".
   *    f()/g()<=h() <- f()*(1/g())<=h() & g()!=0
   * End.
   * }}}
   */
  lazy val intervalUpDivideF = "f_()/g_()<=h_() <- (f_()*(1/g_())<=h_()) & g_()!=0".asFormula
  lazy val intervalUpDivide = derivedAxiom("Div<= up", Sequent(IndexedSeq(), IndexedSeq(intervalUpDivideF)), QE)
  lazy val intervalUpDivideT = TactixLibrary.byUS(intervalUpDivide)


  /**
   * {{{Axiom "<=+ down".
   *    h()<=f()+g() <- ((F()<=f() & G()<=g()) & h()<=F()+G())
   * End.
   * }}}
   */
  lazy val intervalDownPlusF = "h_()<=f_()+g_() <- ((F_()<=f_() & G_()<=g_()) & h_()<=F_()+G_())".asFormula
  lazy val intervalDownPlus = derivedAxiom("<=+ down", Sequent(IndexedSeq(), IndexedSeq(intervalDownPlusF)), QE)
  lazy val intervalDownPlusT = TactixLibrary.byUS(intervalDownPlus)

  /**
   * {{{Axiom "<=- down".
   *    h()<=f()-g() <- ((F()<=f() & g()<=G()) & h()<=F()-G())
   * End.
   * }}}
   */
  lazy val intervalDownMinusF = "h_()<=f_()-g_() <- ((F_()<=f_() & g_()<=G_()) & h_()<=F_()-G_())".asFormula
  lazy val intervalDownMinus = derivedAxiom("<=- down", Sequent(IndexedSeq(), IndexedSeq(intervalDownMinusF)), QE)
  lazy val intervalDownMinusT = TactixLibrary.byUS(intervalDownMinus)


  /**
   * {{{Axiom "<=* down".
   *    h()<=f()*g()<- ((ff()<=f() & f()<=F() & gg()<=g() & g()<=G()) & (h()<=ff()*gg() & h()<=ff()*G() & h()<=F()*gg() & h()<=F()*G()))
   * End.
   * }}}
   */
  lazy val intervalDownTimesF = "h_()<=f_()*g_()<- ((ff_()<=f_() & f_()<=F_() & gg_()<=g_() & g_()<=G_()) & (h_()<=ff_()*gg_() & h_()<=ff_()*G_() & h_()<=F_()*gg_() & h_()<=F_()*G_()))".asFormula
  lazy val intervalDownTimes = derivedAxiom("<=* down", Sequent(IndexedSeq(), IndexedSeq(intervalDownTimesF)), QE)
  lazy val intervalDownTimesT = TactixLibrary.byUS(intervalDownTimes)


  /**
   * {{{Axiom "<=1Div down".
   *    h()<=1/f() <- ((f()<=F() & F()*f()>0) & (h()<=1/F()))
   * End.
   * }}}
   */
  lazy val intervalDown1DivideF = "h_()<=1/f_() <- ((f_()<=F_() & F_()*f_()>0) & (h_()<=1/F_()))".asFormula
  lazy val intervalDown1Divide = derivedAxiom("<=1Div down", Sequent(IndexedSeq(), IndexedSeq(intervalDown1DivideF)), QE)
  lazy val intervalDown1DivideT = TactixLibrary.byUS(intervalDown1Divide)


  /**
   * {{{Axiom "<=Div down".
   *    h()<=f()/g() <- h()<=f()*(1/g()) & g()!=0
   * End.
   * }}}
   */
  lazy val intervalDownDivideF = "h_()<=f_()/g_() <- h_()<=f_()*(1/g_()) & g_()!=0".asFormula
  lazy val intervalDownDivide = derivedAxiom("<=Div down", Sequent(IndexedSeq(), IndexedSeq(intervalDownDivideF)), QE)
  lazy val intervalDownDivideT = TactixLibrary.byUS(intervalDownDivide)

}
