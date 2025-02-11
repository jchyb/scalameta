package scala.meta.tests.parsers.dotty

import scala.meta._

class MatchTypeSuite extends BaseDottySuite {

  implicit def parseBlock(code: String, dialect: Dialect): Stat = blockStat(code)(dialect)

  test("simple") {
    val intput =
      runTestAssert[Stat](
        """|type Elem[X] = X match {
           |  case String => Char
           |  case Array[t] => t
           |}
           |""".stripMargin
      )(
        Defn.Type(
          Nil,
          Type.Name("Elem"),
          List(Type.Param(Nil, Type.Name("X"), Nil, Type.Bounds(None, None), Nil, Nil)),
          Type.Match(
            Type.Name("X"),
            List(
              TypeCase(Type.Name("String"), Type.Name("Char")),
              TypeCase(Type.Apply(Type.Name("Array"), List(Type.Name("t"))), Type.Name("t"))
            )
          )
        )
      )
  }

  test("simple-indentation") {
    val intput =
      runTestAssert[Stat](
        """|type Elem[X] = 
           |  X match
           |    case String => 
           |      Char
           |    case Array[t] => t
           |""".stripMargin,
        assertLayout = Some(
          """|type Elem[X] = X match {
             |  case String => Char
             |  case Array[t] => t
             |}
             |""".stripMargin
        )
      )(
        Defn.Type(
          Nil,
          Type.Name("Elem"),
          List(Type.Param(Nil, Type.Name("X"), Nil, Type.Bounds(None, None), Nil, Nil)),
          Type.Match(
            Type.Name("X"),
            List(
              TypeCase(Type.Name("String"), Type.Name("Char")),
              TypeCase(Type.Apply(Type.Name("Array"), List(Type.Name("t"))), Type.Name("t"))
            )
          )
        )
      )
  }

  test("tuple") {
    runTestAssert[Stat](
      """|type Head[X <: Tuple] = X match {
         |  case (x1, ?) => x1
         |}
         |""".stripMargin
    )(
      Defn.Type(
        Nil,
        Type.Name("Head"),
        List(
          Type.Param(Nil, pname("X"), Nil, Type.Bounds(None, Some(pname("Tuple"))), Nil, Nil)
        ),
        Type.Match(
          Type.Name("X"),
          List(
            TypeCase(
              Type.Tuple(
                List(Type.Name("x1"), Type.Wildcard(Type.Bounds(None, None)))
              ),
              Type.Name("x1")
            )
          )
        )
      )
    )
  }

  test("recursive") {
    runTestAssert[Stat](
      """|type Len[X] <: Int = X match {
         |  case Unit => 0
         |  case x *: xs => S[Len[xs]]
         |}
         |""".stripMargin
    )(
      Defn.Type(
        Nil,
        Type.Name("Len"),
        List(Type.Param(Nil, Type.Name("X"), Nil, Type.Bounds(None, None), Nil, Nil)),
        Type.Match(
          Type.Name("X"),
          List(
            TypeCase(Type.Name("Unit"), Lit.Int(0)),
            TypeCase(
              Type.ApplyInfix(Type.Name("x"), Type.Name("*:"), Type.Name("xs")),
              Type.Apply(Type.Name("S"), List(Type.Apply(Type.Name("Len"), List(Type.Name("xs")))))
            )
          )
        ),
        Type.Bounds(None, Some(Type.Name("Int")))
      )
    )
  }

  test("concat") {
    runTestAssert[Stat](
      """|type Concat[X <: Tuple, Y <: Tuple] <: Tuple = X match {
         |  case Unit => Y
         |  case x1 *: xs1 => x1 *: Concat[xs1, Y]
         |}
         |""".stripMargin
    )(
      Defn.Type(
        Nil,
        Type.Name("Concat"),
        List(
          Type.Param(Nil, pname("X"), Nil, Type.Bounds(None, Some(pname("Tuple"))), Nil, Nil),
          Type.Param(Nil, pname("Y"), Nil, Type.Bounds(None, Some(pname("Tuple"))), Nil, Nil)
        ),
        Type.Match(
          Type.Name("X"),
          List(
            TypeCase(Type.Name("Unit"), Type.Name("Y")),
            TypeCase(
              Type.ApplyInfix(Type.Name("x1"), Type.Name("*:"), Type.Name("xs1")),
              Type.ApplyInfix(
                Type.Name("x1"),
                Type.Name("*:"),
                Type.Apply(Type.Name("Concat"), List(Type.Name("xs1"), Type.Name("Y")))
              )
            )
          )
        ),
        Type.Bounds(None, Some(Type.Name("Tuple")))
      )
    )
  }

  test("indent") {
    runTestAssert[Stat](
      """|type Elem[X] = X match
         |  case String => Char
         |  case Array[t] => t
         |
         |""".stripMargin,
      assertLayout = Some(
        """|type Elem[X] = X match {
           |  case String => Char
           |  case Array[t] => t
           |}
           |""".stripMargin
      )
    )(
      Defn.Type(
        Nil,
        Type.Name("Elem"),
        List(Type.Param(Nil, Type.Name("X"), Nil, Type.Bounds(None, None), Nil, Nil)),
        Type.Match(
          Type.Name("X"),
          List(
            TypeCase(Type.Name("String"), Type.Name("Char")),
            TypeCase(Type.Apply(Type.Name("Array"), List(Type.Name("t"))), Type.Name("t"))
          )
        )
      )
    )
  }
  test("double-newline") {
    runTestAssert[Stat](
      """|object match_types:
         |
         |  type Combine[Left, Right] = Left match
         |    case Unit => Right
         |
         |    case ? => Left
         |""".stripMargin,
      assertLayout = Some(
        """|object match_types {
           |  type Combine[Left, Right] = Left match {
           |    case Unit => Right
           |    case ? => Left
           |  }
           |}
           |""".stripMargin
      )
    )(
      Defn.Object(
        Nil,
        Term.Name("match_types"),
        Template(
          Nil,
          Nil,
          Self(Name(""), None),
          List(
            Defn.Type(
              Nil,
              Type.Name("Combine"),
              List(
                Type.Param(Nil, Type.Name("Left"), Nil, Type.Bounds(None, None), Nil, Nil),
                Type.Param(Nil, Type.Name("Right"), Nil, Type.Bounds(None, None), Nil, Nil)
              ),
              Type.Match(
                Type.Name("Left"),
                List(
                  TypeCase(Type.Name("Unit"), Type.Name("Right")),
                  TypeCase(Type.Name("?"), Type.Name("Left"))
                )
              ),
              Type.Bounds(None, None)
            )
          ),
          Nil
        )
      )
    )
  }

  test("wildcard") {
    implicit val dialect: Dialect = dialects.Scala3Future
    runTestAssert[Stat](
      """|object match_types:
         |  type Combine[L, R] = L match
         |    case Foo[_] => L
         |    case Bar[?] => L
         |    case _ => R
         |    case ? => L
         |""".stripMargin,
      assertLayout = Some(
        """|object match_types {
           |  type Combine[L, R] = L match {
           |    case Foo[_] => L
           |    case Bar[?] => L
           |    case _ => R
           |    case ? => L
           |  }
           |}
           |""".stripMargin
      )
    )(
      Defn.Object(
        Nil,
        Term.Name("match_types"),
        Template(
          Nil,
          Nil,
          Self(Name(""), None),
          List(
            Defn.Type(
              Nil,
              Type.Name("Combine"),
              List(
                Type.Param(Nil, Type.Name("L"), Nil, Type.Bounds(None, None), Nil, Nil),
                Type.Param(Nil, Type.Name("R"), Nil, Type.Bounds(None, None), Nil, Nil)
              ),
              Type.Match(
                Type.Name("L"),
                List(
                  TypeCase(
                    Type.AnonymousLambda(
                      Type.Apply(
                        Type.Name("Foo"),
                        List(Type.AnonymousParam(None))
                      )
                    ),
                    Type.Name("L")
                  ),
                  TypeCase(
                    Type.Apply(
                      Type.Name("Bar"),
                      List(Type.Wildcard(Type.Bounds(None, None)))
                    ),
                    Type.Name("L")
                  ),
                  TypeCase(
                    Type.PatWildcard(),
                    Type.Name("R")
                  ),
                  TypeCase(
                    Type.Name("?"),
                    Type.Name("L")
                  )
                )
              ),
              Type.Bounds(None, None)
            )
          ),
          Nil
        )
      )
    )
  }
}
