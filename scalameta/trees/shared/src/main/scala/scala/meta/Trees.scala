package scala.meta

import org.scalameta.invariants._
import scala.meta.classifiers._
import scala.meta.inputs._
import scala.meta.tokens._
import scala.meta.trees._
import scala.meta.prettyprinters._
import scala.meta.internal.trees._
import scala.meta.internal.trees.Metadata.{newField, replacedField, replacesFields}
import scala.{meta => sm}

@root trait Tree extends InternalTree {
  def parent: Option[Tree]
  def children: List[Tree]

  def pos: Position
  def tokens(implicit dialect: Dialect): Tokens

  override def canEqual(that: Any): Boolean = that.isInstanceOf[Tree]
  override def equals(that: Any): Boolean = this eq that.asInstanceOf[AnyRef]
  override def hashCode: Int = System.identityHashCode(this)
  override def toString = internal.prettyprinters.TreeToString(this)
}

object Tree extends InternalTreeXtensions {
  implicit def classifiable[T <: Tree]: Classifiable[T] = null
  implicit def showStructure[T <: Tree]: Structure[T] =
    internal.prettyprinters.TreeStructure.apply[T]
  implicit def showSyntax[T <: Tree](implicit dialect: Dialect): Syntax[T] =
    internal.prettyprinters.TreeSyntax.apply[T](dialect)

  @branch trait WithBody extends Tree { def body: Tree }
  @branch trait WithCases extends Tree { def cases: List[CaseTree] }
  @branch trait WithDeclTpe extends Tree { def decltpe: Type }
  @branch trait WithDeclTpeOpt extends Tree { def decltpe: Option[Type] }
  @branch trait WithEnums extends Tree { def enums: List[Enumerator] }
  @branch trait WithParamClauses extends Tree { def paramClauses: Seq[Term.ParamClause] }
  @branch trait WithTParamClause extends Tree { def tparamClause: Type.ParamClause }
  @branch trait WithParamClauseGroup extends Tree with WithParamClauses {
    def paramClauseGroup: Option[Member.ParamClauseGroup]

    final def paramClauses: Seq[Term.ParamClause] =
      paramClauseGroup.fold(Seq.empty[Term.ParamClause])(_.paramClauses)
  }
  @branch trait WithParamClauseGroups extends Tree {
    def paramClauseGroups: List[Member.ParamClauseGroup]
  }

}

@branch trait Ref extends Tree
@branch trait Stat extends Tree

object Stat {
  @branch trait WithCtor extends Stat { def ctor: Ctor.Primary }
  @branch trait WithMods extends Stat { def mods: List[Mod] }
  @branch trait WithTemplate extends Stat { def templ: Template }
}

@branch trait Name extends Ref { def value: String }
object Name {
  def apply(value: String): Name = value match {
    case "this" => Name.This()
    case "" => Name.Anonymous()
    case "_" => Name.Placeholder()
    case _ => Name.Indeterminate(value)
  }
  def unapply(name: Name): Option[String] = Some(name.value)
  @ast class Anonymous() extends Name {
    def value = ""
    checkParent(ParentChecks.NameAnonymous)
  }
  @ast class This extends Name {
    def value = "this"
    checkParent(ParentChecks.NameThis)
  }
  @ast class Indeterminate(value: Predef.String @nonEmpty) extends Name
  @ast class Placeholder() extends Name {
    def value = "_"
    checkParent(ParentChecks.NamePlaceholder)
  }
}

@branch trait Lit extends Term with Pat with Type {
  def value: Any
}
object Lit {
  def unapply(arg: Lit): Option[Any] = Some(arg.value)
  @ast class Null() extends Lit { def value: Any = null }
  @ast class Int(value: scala.Int) extends Lit
  // NOTE: Lit.Double/Float are strings to work the same across JS/JVM. Example:
  // 1.4f.toString == "1.399999976158142" // in JS
  // 1.4f.toString == "1.4"               // in JVM
  // See https://www.scala-js.org/doc/semantics.html#tostring-of-float-double-and-unit
  @ast class Double(format: scala.Predef.String) extends Lit { val value = format.toDouble }
  object Double { def apply(double: scala.Double): Double = Lit.Double(double.toString) }
  @ast class Float(format: scala.Predef.String) extends Lit { val value = format.toFloat }
  object Float { def apply(float: scala.Float): Float = Lit.Float(float.toString) }
  @ast class Byte(value: scala.Byte) extends Lit
  @ast class Short(value: scala.Short) extends Lit
  @ast class Char(value: scala.Char) extends Lit
  @ast class Long(value: scala.Long) extends Lit
  @ast class Boolean(value: scala.Boolean) extends Lit
  @ast class Unit() extends Lit { def value: Any = () }
  @ast class String(value: scala.Predef.String) extends Lit
  @ast class Symbol(value: scala.Symbol) extends Lit
}

@branch trait Term extends Stat
object Term {
  @branch trait Ref extends Term with sm.Ref
  @ast class ArgClause(values: List[Term], mod: Option[Mod.ArgsType] = None)
      extends Member.ArgClause

  @ast class This(qual: sm.Name) extends Term.Ref
  @ast class Super(thisp: sm.Name, superp: sm.Name) extends Term.Ref
  @ast class Name(value: Predef.String @nonEmpty) extends sm.Name with Term.Ref with Pat
  @ast class Anonymous() extends sm.Name with Term.Ref {
    def value = ""
    checkParent(ParentChecks.AnonymousImport)
  }
  @ast class Select(qual: Term, name: Term.Name) extends Term.Ref with Pat
  @ast class Interpolate(prefix: Name, parts: List[Lit] @nonEmpty, args: List[Term]) extends Term {
    checkFields(parts.length == args.length + 1)
  }
  @ast class Xml(parts: List[Lit] @nonEmpty, args: List[Term]) extends Term {
    checkFields(parts.length == args.length + 1)
  }
  @ast class Apply(fun: Term, argClause: ArgClause) extends Term with Member.Apply {
    @replacedField("4.6.0") final def args: List[Term] = argClause.values
  }
  @deprecated("Use Term.Apply, pass Mod.Using to Term.ArgClause", "4.6.0")
  @ast class ApplyUsing(fun: Term, argClause: ArgClause) extends Term with Member.Apply {
    @replacedField("4.6.0") final def args: List[Term] = argClause.values
  }
  @ast class ApplyType(fun: Term, targClause: Type.ArgClause @nonEmpty)
      extends Term with Member.Apply {
    @replacedField("4.6.0") final def targs: List[Type] = targClause.values
    override def argClause: Member.ArgClause = targClause
  }
  @ast class ApplyInfix(lhs: Term, op: Name, targClause: Type.ArgClause, argClause: ArgClause)
      extends Term with Member.Infix {
    @replacedField("4.6.0") final def targs: List[Type] = targClause.values
    @replacedField("4.6.0") final def args: List[Term] = argClause.values
    override final def arg: Tree = argClause
  }
  @ast class ApplyUnary(op: Name, arg: Term) extends Term.Ref {
    checkFields(op.isUnaryOp)
  }
  @ast class Assign(lhs: Term, rhs: Term) extends Term with Tree.WithBody {
    checkFields(lhs.is[Term.Quasi] || lhs.is[Term.Ref] || lhs.is[Term.Apply])
    checkParent(ParentChecks.TermAssign)
    override def body: Tree = rhs
  }
  @ast class Return(expr: Term) extends Term
  @ast class Throw(expr: Term) extends Term
  @ast class Ascribe(expr: Term, tpe: Type) extends Term
  @ast class Annotate(expr: Term, annots: List[Mod.Annot] @nonEmpty) extends Term
  @ast class Tuple(args: List[Term] @nonEmpty) extends Term with Member.Tuple {
    // tuple may have one element (see scala.Tuple1)
    // however, this element may not be another single-element Tuple
    checkParent(ParentChecks.MemberTuple)
  }
  @ast class Block(stats: List[Stat]) extends Term {
    checkParent(ParentChecks.TermBlock)
  }
  @ast class EndMarker(name: Term.Name) extends Term
  @ast class If(
      cond: Term,
      thenp: Term,
      elsep: Term,
      @newField("4.4.0") mods: List[Mod] = Nil
  ) extends Term
  @ast class QuotedMacroExpr(body: Term) extends Term
  @ast class QuotedMacroType(tpe: Type) extends Term
  @ast class SplicedMacroExpr(body: Term) extends Term
  @ast class SplicedMacroPat(pat: Pat) extends Term
  @ast class Match(
      expr: Term,
      cases: List[Case] @nonEmpty,
      @newField("4.4.5") mods: List[Mod] = Nil
  ) extends Term with Tree.WithCases
  @ast class Try(expr: Term, catchp: List[Case], finallyp: Option[Term])
      extends Term with Tree.WithCases {
    override final def cases: List[CaseTree] = catchp
  }
  @ast class TryWithHandler(expr: Term, catchp: Term, finallyp: Option[Term]) extends Term

  @branch trait FunctionTerm extends Term with Member.Function {
    def paramClause: ParamClause
    def params: List[Param] = paramClause.values
    def body: Term
  }
  @ast class ContextFunction(paramClause: ParamClause, body: Term) extends FunctionTerm {
    @replacedField("4.6.0") final override def params: List[Param] = paramClause.values
    checkFields(
      paramClause.values.forall(param =>
        param.is[Param.Quasi] ||
          (param.name.is[sm.Name.Anonymous] ==> param.default.isEmpty)
      )
    )
  }
  @ast class Function(paramClause: ParamClause, body: Term) extends FunctionTerm {
    @replacedField("4.6.0") final override def params: List[Param] = paramClause.values
    checkFields(
      paramClause.is[ParamClause.Quasi] || {
        val params = paramClause.values
        params.forall { param =>
          param.is[Param.Quasi] ||
          param.name.is[sm.Name.Anonymous] ==> param.default.isEmpty
        } && {
          params.exists(_.is[Param.Quasi]) ||
          paramClause.mod.exists(_.is[Mod.Implicit]) ==> (params.lengthCompare(1) == 0)
        }
      }
    )
  }
  @ast class AnonymousFunction(body: Term) extends Term
  @ast class PolyFunction(tparamClause: Type.ParamClause, body: Term)
      extends Term with Tree.WithTParamClause with Member.Function {
    @replacedField("4.6.0") final def tparams: List[Type.Param] = tparamClause.values
    override final def paramClause: Member.SyntaxValuesClause = tparamClause
  }
  @ast class PartialFunction(cases: List[Case] @nonEmpty) extends Term with Tree.WithCases
  @ast class While(expr: Term, body: Term) extends Term with Tree.WithBody
  @ast class Do(body: Term, expr: Term) extends Term with Tree.WithBody
  @ast class For(enums: List[Enumerator] @nonEmpty, body: Term)
      extends Term with Tree.WithBody with Tree.WithEnums {
    checkFields(checkValidEnumerators(enums))
  }
  @ast class ForYield(enums: List[Enumerator] @nonEmpty, body: Term)
      extends Term with Tree.WithBody with Tree.WithEnums {
    checkFields(checkValidEnumerators(enums))
  }
  @ast class New(init: Init) extends Term
  @ast class NewAnonymous(templ: Template) extends Term with Stat.WithTemplate
  @ast class Placeholder() extends Term
  @ast class Eta(expr: Term) extends Term
  @ast class Repeated(expr: Term) extends Term {
    checkParent(ParentChecks.TermRepeated)
  }
  @ast class Param(mods: List[Mod], name: meta.Name, decltpe: Option[Type], default: Option[Term])
      extends Member.Param with Tree.WithDeclTpeOpt
  @ast class ParamClause(values: List[Param], mod: Option[Mod.ParamsType] = None)
      extends Member.ParamClause
  object ParamClause {
    private[meta] def getMod(v: Seq[Param]): Option[Mod.ParamsType] =
      v.filter(!_.is[Param.Quasi]) match {
        case head :: tail =>
          head.mods.collectFirst {
            case x: Mod.Using => x
            case x: Mod.Implicit if tail.forall(_.mods.exists(_.is[Mod.Implicit])) => x
          }
        case _ => None
      }
  }
  def fresh(): Term.Name = fresh("fresh")
  def fresh(prefix: String): Term.Name = Term.Name(prefix + Fresh.nextId())
}

@branch trait Type extends Tree
object Type {
  @branch trait Ref extends Type with sm.Ref
  @ast class ArgClause(values: List[Type]) extends Member.ArgClause

  @ast class Name(value: String @nonEmpty) extends sm.Name with Type.Ref
  @ast class AnonymousName() extends Type
  @ast class Select(qual: Term.Ref, name: Type.Name) extends Type.Ref {
    checkFields(qual.isPath || qual.is[Term.Super] || qual.is[Term.Ref.Quasi])
  }
  @ast class Project(qual: Type, name: Type.Name) extends Type.Ref
  @ast class Singleton(ref: Term.Ref) extends Type.Ref {
    checkFields(ref.isPath || ref.is[Term.Super])
  }
  @ast class Apply(tpe: Type, argClause: ArgClause @nonEmpty) extends Type with Member.Apply {
    @replacedField("4.6.0") final def args: List[Type] = argClause.values
    override def fun: Tree = tpe
  }
  @ast class ApplyInfix(lhs: Type, op: Name, rhs: Type) extends Type with Member.Infix {
    override final def arg: Tree = rhs
  }

  @ast class FuncParamClause(values: List[Type]) extends Tree with Member.SyntaxValuesClause
  @branch trait FunctionType extends Type with Member.Function {
    def paramClause: FuncParamClause
    @deprecated("Please use paramClause instead", "4.6.0")
    def params: List[Type]
    def res: Type
    override def body: Tree = res
  }
  @ast class Function(
      @replacesFields("4.6.0", FuncParamClause)
      paramClause: FuncParamClause,
      res: Type
  ) extends FunctionType {
    @replacedField("4.6.0") final override def params: List[Type] = paramClause.values
  }
  @ast class PolyFunction(tparamClause: ParamClause, tpe: Type)
      extends Type with Tree.WithTParamClause with Member.Function {
    @replacedField("4.6.0") final def tparams: List[Param] = tparamClause.values
    override final def paramClause: Member.SyntaxValuesClause = tparamClause
    override final def body: Tree = tpe
  }
  @ast class ContextFunction(
      @replacesFields("4.6.0", FuncParamClause)
      paramClause: FuncParamClause,
      res: Type
  ) extends FunctionType {
    @replacedField("4.6.0") final override def params: List[Type] = paramClause.values
  }
  @ast @deprecated("Implicit functions are not supported in any dialect")
  class ImplicitFunction(
      params: List[Type],
      res: Type
  ) extends Type
  @ast class Tuple(args: List[Type] @nonEmpty) extends Type with Member.Tuple {
    // tuple may have one element (see scala.Tuple1)
    // however, this element may not be another single-element Tuple
    checkParent(ParentChecks.MemberTuple)
  }
  @ast class With(lhs: Type, rhs: Type) extends Type
  @deprecated("And unused, replaced by ApplyInfix", "4.5.1")
  @ast class And(lhs: Type, rhs: Type) extends Type
  @deprecated("Or unused, replaced by ApplyInfix", "4.5.1")
  @ast class Or(lhs: Type, rhs: Type) extends Type
  @ast class Refine(tpe: Option[Type], stats: List[Stat]) extends Type {
    checkFields(stats.forall(_.isRefineStat))
  }
  @ast class Existential(tpe: Type, stats: List[Stat] @nonEmpty) extends Type {
    checkFields(stats.forall(_.isExistentialStat))
  }
  @ast class Annotate(tpe: Type, annots: List[Mod.Annot] @nonEmpty) extends Type
  @ast class Lambda(tparamClause: ParamClause, tpe: Type)
      extends Type with Tree.WithTParamClause with Member.Function {
    checkParent(ParentChecks.TypeLambda)
    @replacedField("4.6.0") final def tparams: List[Param] = tparamClause.values
    override def paramClause: Member.SyntaxValuesClause = tparamClause
    override final def body: Tree = tpe
  }
  @ast class AnonymousLambda(tpe: Type) extends Type
  @ast class Macro(body: Term) extends Type with Tree.WithBody
  @deprecated("Method type syntax is no longer supported in any dialect", "4.4.3")
  @ast class Method(paramClauses: Seq[Term.ParamClause], tpe: Type) extends Type {
    checkParent(ParentChecks.TypeMethod)
    @replacedField("4.6.0") final def paramss: List[List[Term.Param]] =
      paramClauses.map(_.values).toList
  }
  @deprecated("Placeholder replaced with AnonymousParam and Wildcard", ">4.5.13")
  @branch trait Placeholder extends Type {
    def bounds: Bounds
    def copy(bounds: Bounds = this.bounds): Placeholder
  }
  @deprecated("Placeholder replaced with AnonymousParam and Wildcard", ">4.5.13")
  object Placeholder {
    @ast private[meta] class Impl(bounds: Bounds) extends Placeholder
    @inline def apply(bounds: Bounds): Placeholder = Impl(bounds)
    @inline final def unapply(tree: Placeholder): Option[Bounds] = Some(tree.bounds)
  }
  @ast class PatWildcard extends Type
  @ast class Wildcard(bounds: Bounds) extends Placeholder
  @ast class AnonymousParam(variant: Option[Mod.Variant]) extends Placeholder {
    @deprecated("Placeholder replaced with AnonymousParam and Wildcard", ">4.5.13")
    override final def bounds: Bounds = Bounds(None, None)
    override def copy(bounds: Bounds): Placeholder = Placeholder(bounds)
  }
  @ast class Bounds(lo: Option[Type], hi: Option[Type]) extends Tree
  @ast class ByName(tpe: Type) extends Type {
    checkParent(ParentChecks.TypeByName)
  }
  @ast class Repeated(tpe: Type) extends Type {
    checkParent(ParentChecks.TypeRepeated)
  }
  @ast class Var(name: Name) extends Type with Member.Type {
    checkFields(name.value(0).isLower)
    checkParent(ParentChecks.TypeVar)
  }

  @branch trait FunctionParamOrArg extends Type {
    def mods: List[Mod]
    def nameOpt: Option[Name]
    def tpe: Type
  }

  @ast class TypedParam(name: Name, typ: Type, @newField("4.7.8") mods: List[Mod] = Nil)
      extends FunctionParamOrArg with Member.Type {
    override def nameOpt: Option[Name] = Some(name)
    override def tpe: Type = typ
  }

  @ast class FunctionArg(mods: List[Mod], tpe: Type) extends FunctionParamOrArg {
    override def nameOpt: Option[Name] = None
  }

  @ast class Param(
      mods: List[Mod],
      name: meta.Name,
      tparamClause: ParamClause,
      tbounds: Type.Bounds,
      vbounds: List[Type],
      cbounds: List[Type]
  ) extends Member.Param with Tree.WithTParamClause {
    @replacedField("4.6.0") final def tparams: List[Param] = tparamClause.values
  }

  @ast class ParamClause(values: List[Param]) extends Member.ParamClause

  @ast class Match(tpe: Type, cases: List[TypeCase] @nonEmpty) extends Type with Tree.WithCases
  def fresh(): Type.Name = fresh("fresh")
  def fresh(prefix: String): Type.Name = Type.Name(prefix + Fresh.nextId())
}

@branch trait Pat extends Tree
object Pat {
  @ast class ArgClause(values: List[Pat]) extends Member.ArgClause
  @ast class Var(name: Term.Name) extends Pat with Member.Term {
    // NOTE: can't do this check here because of things like `val X = 2`
    // checkFields(name.value(0).isLower)
    checkParent(ParentChecks.PatVar)
  }
  @ast class Wildcard() extends Pat
  @ast class SeqWildcard() extends Pat {
    checkParent(ParentChecks.PatSeqWildcard)
  }
  @ast class Bind(lhs: Pat, rhs: Pat) extends Pat {
    checkFields(lhs.is[Pat.Var] || lhs.is[Pat.Quasi])
  }
  @ast class Alternative(lhs: Pat, rhs: Pat) extends Pat
  @ast class Tuple(args: List[Pat] @nonEmpty) extends Pat with Member.Tuple {
    // tuple may have one element (see scala.Tuple1)
    // however, this element may not be another single-element Tuple
    checkParent(ParentChecks.MemberTuple)
  }
  @ast class Repeated(name: Term.Name) extends Pat
  @ast class Extract(fun: Term, argClause: ArgClause) extends Pat with Member.Apply {
    @replacedField("4.6.0") final def args: List[Pat] = argClause.values
    checkFields(fun.isExtractor)
  }
  @ast class ExtractInfix(lhs: Pat, op: Term.Name, argClause: ArgClause)
      extends Pat with Member.Infix {
    @replacedField("4.6.0") final def rhs: List[Pat] = argClause.values
    override def arg: Tree = argClause
  }
  @ast class Interpolate(prefix: Term.Name, parts: List[Lit] @nonEmpty, args: List[Pat])
      extends Pat {
    checkFields(parts.length == args.length + 1)
  }
  @ast class Xml(parts: List[Lit] @nonEmpty, args: List[Pat]) extends Pat {
    checkFields(parts.length == args.length + 1)
  }
  @ast class Typed(lhs: Pat, rhs: Type) extends Pat {
    checkFields(rhs match {
      case _: Type.Var | _: Type.Placeholder | _: Type.Wildcard | _: Type.AnonymousParam => false
      case _ => true
    })
  }
  @ast class Macro(body: Term) extends Pat with Tree.WithBody {
    checkFields(body.is[Term.QuotedMacroExpr] || body.is[Term.QuotedMacroType])
  }
  @ast class Given(tpe: Type) extends Pat
  def fresh(): Pat.Var = Pat.Var(Term.fresh())
  def fresh(prefix: String): Pat.Var = Pat.Var(Term.fresh(prefix))
}

@branch trait Member extends Tree {
  def name: Name
  final def isNameAnonymous: Boolean = name.is[Name.Anonymous]
}
object Member {
  @branch trait Term extends Member {
    def name: sm.Term.Name
  }
  @branch trait Type extends Member {
    def name: sm.Type.Name
  }

  @branch trait Tuple extends Tree {
    def args: List[Tree]
    final def nonEmpty: Boolean = args.nonEmpty
  }

  @branch trait SyntaxValuesClause extends Tree {
    def values: List[Tree]
    final def nonEmpty: Boolean = values.nonEmpty
  }

  @branch trait Param extends Member {
    def mods: List[Mod]
  }
  @branch trait ParamClause extends SyntaxValuesClause {
    def values: List[Param]
  }

  @ast class ParamClauseGroup(
      tparamClause: sm.Type.ParamClause,
      paramClauses: List[sm.Term.ParamClause]
  ) extends Tree with Tree.WithTParamClause with Tree.WithParamClauses {
    checkFields(checkValidParamClauses(paramClauses))
  }

  object ParamClauseGroup {
    private[meta] def toTparams(
        paramClauseGroup: Option[ParamClauseGroup]
    ): List[sm.Type.Param] =
      paramClauseGroup.fold(List.empty[sm.Type.Param])(_.tparamClause.values)
    private[meta] def toTparams(
        paramClauseGroups: List[ParamClauseGroup]
    ): List[sm.Type.Param] =
      toTparams(paramClauseGroups.headOption)

    private[meta] def toParamss(
        paramClauseGroup: Option[ParamClauseGroup]
    ): List[List[sm.Term.Param]] =
      paramClauseGroup.fold(List.empty[List[sm.Term.Param]])(_.paramClauses.map(_.values))
    private[meta] def toParamss(
        paramClauseGroups: List[ParamClauseGroup]
    ): List[List[sm.Term.Param]] =
      toParamss(paramClauseGroups.headOption)
  }

  private[meta] object ParamClauseGroupCtor {
    def apply(
        tparams: List[sm.Type.Param],
        paramss: List[List[sm.Term.Param]]
    ): Option[ParamClauseGroup] = {
      if (tparams.isEmpty && paramss.isEmpty) None
      else Some(ParamClauseGroup(tparamClause = tparams, paramClauses = paramss))
    }
  }

  private[meta] object ParamClauseGroupCtorGiven {
    def apply(
        tparams: List[sm.Type.Param],
        sparams: List[List[sm.Term.Param]]
    ): Option[ParamClauseGroup] =
      ParamClauseGroupCtor(tparams, sparams)
  }

  private[meta] object ParamClauseGroupsCtor {
    def apply(
        tparams: List[sm.Type.Param],
        paramss: List[List[sm.Term.Param]]
    ): List[ParamClauseGroup] =
      if (tparams.isEmpty && paramss.isEmpty) Nil
      else ParamClauseGroup(tparamClause = tparams, paramClauses = paramss) :: Nil

    def apply(paramClauseGroup: Option[ParamClauseGroup]): List[ParamClauseGroup] =
      paramClauseGroup.toList
  }

  @branch trait ArgClause extends SyntaxValuesClause {
    def values: List[Tree]
  }

  @branch trait Infix extends Tree {
    def lhs: Tree
    def op: Name
    def arg: Tree
  }

  @branch trait Apply extends Tree {
    def fun: Tree
    def argClause: ArgClause
  }

  @branch trait Function extends Tree with Tree.WithBody {
    def paramClause: SyntaxValuesClause
    def body: Tree
  }

}

@branch trait Decl extends Stat
object Decl {
  @ast class Val(mods: List[Mod], pats: List[Pat] @nonEmpty, decltpe: sm.Type)
      extends Decl with Stat.WithMods with Tree.WithDeclTpe
  @ast class Var(mods: List[Mod], pats: List[Pat] @nonEmpty, decltpe: sm.Type)
      extends Decl with Stat.WithMods with Tree.WithDeclTpe

  @ast class Def(
      mods: List[Mod],
      name: Term.Name,
      @replacesFields("4.6.0", Member.ParamClauseGroupsCtor)
      @replacesFields("4.7.3", Member.ParamClauseGroupsCtor)
      paramClauseGroups: List[Member.ParamClauseGroup],
      decltpe: sm.Type
  ) extends Decl
      with Member.Term
      with Stat.WithMods
      with Tree.WithParamClauseGroup
      with Tree.WithParamClauseGroups
      with Tree.WithDeclTpe {
    @replacedField("4.6.0", pos = 2) final def tparams: List[sm.Type.Param] =
      Member.ParamClauseGroup.toTparams(paramClauseGroups)
    @replacedField("4.6.0", pos = 3) final def paramss: List[List[Term.Param]] =
      Member.ParamClauseGroup.toParamss(paramClauseGroups)

    @replacedField("4.7.3") final def paramClauseGroup: Option[Member.ParamClauseGroup] =
      paramClauseGroups.headOption
  }

  @ast class Type(
      mods: List[Mod],
      name: sm.Type.Name,
      tparamClause: sm.Type.ParamClause,
      bounds: sm.Type.Bounds
  ) extends Decl with Member.Type with Stat.WithMods with Tree.WithTParamClause {
    @replacedField("4.6.0") final def tparams: List[sm.Type.Param] = tparamClause.values
  }
  @ast class Given(
      mods: List[Mod],
      name: Term.Name,
      @replacesFields("4.6.0", Member.ParamClauseGroupCtorGiven)
      paramClauseGroup: Option[Member.ParamClauseGroup],
      decltpe: sm.Type
  ) extends Decl
      with Member.Term
      with Stat.WithMods
      with Tree.WithParamClauseGroup
      with Tree.WithDeclTpe {
    @replacedField("4.6.0", pos = 2) final def tparams: List[sm.Type.Param] =
      Member.ParamClauseGroup.toTparams(paramClauseGroup)
    @replacedField("4.6.0", pos = 3) final def sparams: List[List[Term.Param]] =
      Member.ParamClauseGroup.toParamss(paramClauseGroup)
  }
}

@branch trait Defn extends Stat
object Defn {
  @ast class Val(
      mods: List[Mod],
      pats: List[Pat] @nonEmpty,
      decltpe: Option[sm.Type],
      rhs: Term
  ) extends Defn with Stat.WithMods with Tree.WithDeclTpeOpt with Tree.WithBody {
    checkFields(!rhs.is[Term.Placeholder])
    checkFields(pats.forall(!_.is[Term.Name]))
    override def body: Tree = rhs
  }

  private[meta] object VarRhsCtor {
    def apply(rhs: Option[Term]): Term = rhs.getOrElse(Term.Placeholder())
    def toOpt(body: Term): Option[Term] = if (body.is[Term.Placeholder]) None else Some(body)
  }
  @ast class Var(
      mods: List[Mod],
      pats: List[Pat] @nonEmpty,
      decltpe: Option[sm.Type],
      @replacesFields("4.7.2", VarRhsCtor)
      body: Term
  ) extends Defn with Stat.WithMods with Tree.WithDeclTpeOpt with Tree.WithBody {
    checkFields(
      if (body.is[Term.Placeholder]) decltpe.nonEmpty && pats.forall(_.is[Pat.Var])
      else pats.forall(!_.is[Term.Name])
    )
    @replacedField("4.7.2") final def rhs: Option[Term] = VarRhsCtor.toOpt(body)
  }
  @ast class Given(
      mods: List[Mod],
      name: scala.meta.Name,
      @replacesFields("4.6.0", Member.ParamClauseGroupCtorGiven)
      paramClauseGroup: Option[Member.ParamClauseGroup],
      templ: Template
  ) extends Defn with Stat.WithMods with Tree.WithParamClauseGroup with Stat.WithTemplate {
    @replacedField("4.6.0", pos = 2) final def tparams: List[sm.Type.Param] =
      Member.ParamClauseGroup.toTparams(paramClauseGroup)
    @replacedField("4.6.0", pos = 3) final def sparams: List[List[Term.Param]] =
      Member.ParamClauseGroup.toParamss(paramClauseGroup)
  }
  @ast class Enum(
      mods: List[Mod],
      name: sm.Type.Name,
      tparamClause: sm.Type.ParamClause,
      ctor: Ctor.Primary,
      templ: Template
  ) extends Defn
      with Member.Type
      with Stat.WithMods
      with Tree.WithTParamClause
      with Stat.WithCtor
      with Stat.WithTemplate {
    @replacedField("4.6.0") final def tparams: List[sm.Type.Param] = tparamClause.values
  }
  @ast class EnumCase(
      mods: List[Mod],
      name: Term.Name,
      tparamClause: sm.Type.ParamClause,
      ctor: Ctor.Primary,
      inits: List[Init]
  ) extends Defn with Member.Term with Stat.WithMods with Tree.WithTParamClause with Stat.WithCtor {
    checkParent(ParentChecks.EnumCase)
    @replacedField("4.6.0") final def tparams: List[sm.Type.Param] = tparamClause.values
  }
  @ast class RepeatedEnumCase(
      mods: List[Mod],
      cases: List[Term.Name]
  ) extends Defn with Stat.WithMods {
    checkParent(ParentChecks.EnumCase)
  }
  @ast class GivenAlias(
      mods: List[Mod],
      name: scala.meta.Name,
      @replacesFields("4.6.0", Member.ParamClauseGroupCtorGiven)
      paramClauseGroup: Option[Member.ParamClauseGroup],
      decltpe: sm.Type,
      body: Term
  ) extends Defn
      with Stat.WithMods
      with Tree.WithParamClauseGroup
      with Tree.WithBody
      with Tree.WithDeclTpe {
    @replacedField("4.6.0", pos = 2) final def tparams: List[sm.Type.Param] =
      Member.ParamClauseGroup.toTparams(paramClauseGroup)
    @replacedField("4.6.0", pos = 3) final def sparams: List[List[Term.Param]] =
      Member.ParamClauseGroup.toParamss(paramClauseGroup)
  }
  @ast class ExtensionGroup(
      @replacesFields("4.6.0", Member.ParamClauseGroupCtor)
      paramClauseGroup: Option[Member.ParamClauseGroup],
      body: Stat
  ) extends Defn with Tree.WithParamClauseGroup with Tree.WithBody {
    @replacedField("4.6.0", pos = 0) final def tparams: List[sm.Type.Param] =
      Member.ParamClauseGroup.toTparams(paramClauseGroup)
    @replacedField("4.6.0", pos = 1) final def paramss: List[List[Term.Param]] =
      Member.ParamClauseGroup.toParamss(paramClauseGroup)
  }

  @ast class Def(
      mods: List[Mod],
      name: Term.Name,
      @replacesFields("4.6.0", Member.ParamClauseGroupsCtor)
      @replacesFields("4.7.3", Member.ParamClauseGroupsCtor)
      paramClauseGroups: List[Member.ParamClauseGroup],
      decltpe: Option[sm.Type],
      body: Term
  ) extends Defn
      with Member.Term
      with Stat.WithMods
      with Tree.WithParamClauseGroup
      with Tree.WithParamClauseGroups
      with Tree.WithDeclTpeOpt
      with Tree.WithBody {
    @replacedField("4.6.0", pos = 2) final def tparams: List[sm.Type.Param] =
      Member.ParamClauseGroup.toTparams(paramClauseGroups)
    @replacedField("4.6.0", pos = 3) final def paramss: List[List[Term.Param]] =
      Member.ParamClauseGroup.toParamss(paramClauseGroups)

    @replacedField("4.7.3") final def paramClauseGroup: Option[Member.ParamClauseGroup] =
      paramClauseGroups.headOption
  }

  @ast class Macro(
      mods: List[Mod],
      name: Term.Name,
      @replacesFields("4.6.0", Member.ParamClauseGroupsCtor)
      @replacesFields("4.7.3", Member.ParamClauseGroupsCtor)
      paramClauseGroups: List[Member.ParamClauseGroup],
      decltpe: Option[sm.Type],
      body: Term
  ) extends Defn
      with Member.Term
      with Stat.WithMods
      with Tree.WithParamClauseGroup
      with Tree.WithParamClauseGroups
      with Tree.WithDeclTpeOpt
      with Tree.WithBody {
    @replacedField("4.6.0", pos = 2) final def tparams: List[sm.Type.Param] =
      Member.ParamClauseGroup.toTparams(paramClauseGroups)
    @replacedField("4.6.0", pos = 3) final def paramss: List[List[Term.Param]] =
      Member.ParamClauseGroup.toParamss(paramClauseGroups)

    @replacedField("4.7.3") final def paramClauseGroup: Option[Member.ParamClauseGroup] =
      paramClauseGroups.headOption
  }

  @ast class Type(
      mods: List[Mod],
      name: sm.Type.Name,
      tparamClause: sm.Type.ParamClause,
      body: sm.Type,
      @newField("4.4.0") bounds: sm.Type.Bounds = sm.Type.Bounds(None, None)
  ) extends Defn with Member.Type with Stat.WithMods with Tree.WithTParamClause with Tree.WithBody {
    @replacedField("4.6.0") final def tparams: List[sm.Type.Param] = tparamClause.values
  }
  @ast class Class(
      mods: List[Mod],
      name: sm.Type.Name,
      tparamClause: sm.Type.ParamClause,
      ctor: Ctor.Primary,
      templ: Template
  ) extends Defn
      with Member.Type
      with Stat.WithMods
      with Tree.WithTParamClause
      with Stat.WithCtor
      with Stat.WithTemplate {
    @replacedField("4.6.0") final def tparams: List[sm.Type.Param] = tparamClause.values
  }
  @ast class Trait(
      mods: List[Mod],
      name: sm.Type.Name,
      tparamClause: sm.Type.ParamClause,
      ctor: Ctor.Primary,
      templ: Template
  ) extends Defn
      with Member.Type
      with Stat.WithMods
      with Tree.WithTParamClause
      with Stat.WithCtor
      with Stat.WithTemplate {
    checkFields(templ.is[Template.Quasi] || templ.stats.forall(!_.is[Ctor]))
    @replacedField("4.6.0") final def tparams: List[sm.Type.Param] = tparamClause.values
  }
  @ast class Object(mods: List[Mod], name: Term.Name, templ: Template)
      extends Defn with Member.Term with Stat.WithMods with Stat.WithTemplate {
    checkFields(templ.is[Template.Quasi] || templ.stats.forall(!_.is[Ctor]))
  }
}

@ast class Pkg(ref: Term.Ref, stats: List[Stat]) extends Member.Term with Stat {
  checkFields(ref.isQualId)
  def name: Term.Name = ref match {
    case name: Term.Name => name
    case Term.Select(_, name: Term.Name) => name
  }
}
object Pkg {
  @ast class Object(mods: List[Mod], name: Term.Name, templ: Template)
      extends Member.Term with Stat with Stat.WithMods with Stat.WithTemplate {
    checkFields(templ.is[Template.Quasi] || templ.stats.forall(!_.is[Ctor]))
  }
}

// NOTE: The names of Ctor.Primary and Ctor.Secondary here is always Name.Anonymous.
// While seemingly useless, this name is crucial to one of the key principles behind the semantic API:
// "every definition and every reference should carry a name".
@branch trait Ctor extends Tree with Member
object Ctor {
  @ast class Primary(mods: List[Mod], name: Name, paramClauses: Seq[Term.ParamClause])
      extends Ctor with Tree.WithParamClauses {
    @replacedField("4.6.0") final def paramss: List[List[Term.Param]] =
      paramClauses.map(_.values).toList
  }
  @ast class Secondary(
      mods: List[Mod],
      name: Name,
      paramClauses: Seq[Term.ParamClause] @nonEmpty,
      init: Init,
      stats: List[Stat]
  ) extends Ctor with Stat with Stat.WithMods with Tree.WithParamClauses {
    checkFields(stats.forall(_.isBlockStat))
    @replacedField("4.6.0") final def paramss: List[List[Term.Param]] =
      paramClauses.map(_.values).toList
  }
}

// NOTE: The name here is always Name.Anonymous.
// See comments to Ctor.Primary and Ctor.Secondary for justification.
@ast class Init(tpe: Type, name: Name, argClauses: Seq[Term.ArgClause]) extends Ref {
  checkFields(tpe.isConstructable)
  checkParent(ParentChecks.Init)
  @replacedField("4.6.0") final def argss: List[List[Term]] = argClauses.map(_.values).toList
}

@ast class Self(name: Name, decltpe: Option[Type]) extends Member with Tree.WithDeclTpeOpt {
  final def isEmpty: Boolean = isNameAnonymous && decltpe.isEmpty
}

@ast class Template(
    early: List[Stat],
    inits: List[Init],
    self: Self,
    stats: List[Stat],
    @newField("4.4.0") derives: List[Type] = Nil
) extends Tree {
  checkFields(early.forall(_.isEarlyStat && inits.nonEmpty))
  checkFields(stats.forall(_.isTemplateStat))
}

@branch trait Mod extends Tree
object Mod {
  @branch trait Variant extends Mod
  @branch trait ArgsType extends Mod
  @branch trait ParamsType extends Mod
  @ast class Annot(init: Init) extends Mod {
    @deprecated("Use init instead", "1.9.0")
    def body = init
  }
  @branch trait WithWithin extends Mod { def within: Ref }
  @ast class Private(within: Ref) extends Mod with WithWithin {
    checkFields(within.isWithin)
  }
  @ast class Protected(within: Ref) extends Mod with WithWithin {
    checkFields(within.isWithin)
  }
  @ast class Implicit() extends ParamsType
  @ast class Final() extends Mod
  @ast class Sealed() extends Mod
  @ast class Open() extends Mod
  @deprecated("Super traits introduced in dotty, but later removed.")
  @ast class Super() extends Mod
  @ast class Override() extends Mod
  @ast class Case() extends Mod
  @ast class Abstract() extends Mod
  @ast class Covariant() extends Variant
  @ast class Contravariant() extends Variant
  @ast class Lazy() extends Mod
  @ast class ValParam() extends Mod
  @ast class VarParam() extends Mod
  @ast class Infix() extends Mod
  @ast class Inline() extends Mod
  @ast class Using() extends ParamsType with ArgsType
  @ast class Opaque() extends Mod
  @ast class Transparent() extends Mod
  @ast class Erased() extends Mod
}

@branch trait Enumerator extends Tree
object Enumerator {
  @ast class Generator(pat: Pat, rhs: Term) extends Enumerator with Tree.WithBody {
    override def body: Tree = rhs
  }
  @ast class CaseGenerator(pat: Pat, rhs: Term) extends Enumerator with Tree.WithBody {
    override def body: Tree = rhs
  }
  @ast class Val(pat: Pat, rhs: Term) extends Enumerator with Tree.WithBody {
    override def body: Tree = rhs
  }
  @ast class Guard(cond: Term) extends Enumerator
}

@branch trait ImportExportStat extends Stat {
  def importers: List[Importer]
}
@ast class Import(importers: List[Importer] @nonEmpty) extends ImportExportStat
@ast class Export(importers: List[Importer] @nonEmpty) extends ImportExportStat

@ast class Importer(ref: Term.Ref, importees: List[Importee] @nonEmpty) extends Tree {
  checkFields(ref.isStableId)
}

@branch trait Importee extends Tree with Ref
object Importee {
  @ast class Wildcard() extends Importee
  @ast class Given(tpe: Type) extends Importee
  @ast class GivenAll() extends Importee
  @ast class Name(name: sm.Name) extends Importee {
    checkFields(name.is[sm.Name.Quasi] || name.is[sm.Name.Indeterminate])
  }
  @ast class Rename(name: sm.Name, rename: sm.Name) extends Importee {
    checkFields(name.is[sm.Name.Quasi] || name.is[sm.Name.Indeterminate])
    checkFields(rename.is[sm.Name.Quasi] || rename.is[sm.Name.Indeterminate])
  }
  @ast class Unimport(name: sm.Name) extends Importee {
    checkFields(name.is[sm.Name.Quasi] || name.is[sm.Name.Indeterminate])
  }
}

@branch trait CaseTree extends Tree with Tree.WithBody {
  def pat: Tree
  def body: Tree
}
@ast class Case(pat: Pat, cond: Option[Term], body: Term) extends CaseTree
@ast class TypeCase(pat: Type, body: Type) extends CaseTree

@ast class Source(stats: List[Stat]) extends Tree {
  // NOTE: This validation has been removed to allow dialects with top-level terms.
  // Ideally, we should push the validation into a dialect-specific prettyprinter when #220 is fixed.
  // checkFields(stats.forall(_.isTopLevelStat))
}

@ast class MultiSource(sources: List[Source]) extends Tree

package internal.trees {
  // NOTE: Quasi is a base trait for a whole bunch of classes.
  // Every root, branch and ast trait/class among scala.meta trees (except for quasis themselves)
  // has a corresponding quasi, e.g. Term.Quasi or Type.Quasi.
  //
  // Here's how quasis represent unquotes
  // (XXX below depends on the position where the unquote occurs, e.g. q"$x" will result in Term.Quasi):
  //   * $x => XXX.Quasi(0, XXX.Name("x"))
  //   * ..$xs => XXX.Quasi(1, XXX.Quasi(0, XXX.Name("xs"))
  //   * ...$xss => XXX.Quasi(2, XXX.Quasi(0, XXX.Name("xss"))
  //   * ..{$fs($args)} => Complex ellipses aren't supported yet
  @branch trait Quasi extends Tree {
    def rank: Int
    def tree: Tree
    def pt: Class[_]
    def become[T <: Tree: AstInfo]: T with Quasi
  }

  @registry object All
}
