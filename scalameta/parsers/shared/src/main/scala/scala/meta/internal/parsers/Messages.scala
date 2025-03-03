package scala.meta
package internal
package parsers

import scala.compat.Platform.EOL

object Messages {
  def QuasiquoteRankMismatch(found: Int, required: Int, hint: String = ""): String = {
    val s_found = "." * (found + 1) + "$"
    val s_required = 0.to(required + 1).filter(_ != 1).map(i => "." * i + "$").mkString(" or ")
    var message = s"rank mismatch when unquoting;$EOL found   : $s_found$EOL required: $s_required"
    if (hint.nonEmpty) message = message + EOL + hint
    message
  }

  def QuasiquoteAdjacentEllipsesInPattern(rank: Int): String = {
    val hint = {
      "Note that you can extract a list into an unquote when pattern matching," + EOL +
        "it just cannot follow another list either directly or indirectly."
    }
    QuasiquoteRankMismatch(rank, rank - 1, hint)
  }

  def IllegalCombinationModifiers(mod1: Mod, mod2: Mod): String =
    s"illegal combination of modifiers: $mod1 and $mod2"

  val InvalidSealed = "`sealed' modifier can be used only for classes"
  val InvalidOpen = "`open' modifier can be used only for classes"
  val InvalidImplicit =
    "`implicit' modifier can be used only for values, variables, methods and classes"
  val InvalidImplicitTrait = "traits cannot be implicit"
  val InvalidAbstract = "`abstract' modifier can be used only for classes"
  val InvalidLazyClasses = "classes cannot be lazy"
}
