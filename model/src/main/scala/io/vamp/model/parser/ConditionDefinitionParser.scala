package io.vamp.model.parser

import org.parboiled.scala._

sealed trait ConditionDefinitionOperand extends Operand

case class PathPrefix(value: String) extends ConditionDefinitionOperand

case class Host(value: String) extends ConditionDefinitionOperand

case class Cookie(value: String) extends ConditionDefinitionOperand

case class Header(value: String) extends ConditionDefinitionOperand

case class UserAgent(value: String) extends ConditionDefinitionOperand

case class CookieContains(name: String, value: String) extends ConditionDefinitionOperand

case class HeaderContains(name: String, value: String) extends ConditionDefinitionOperand

trait ConditionDefinitionParser extends BooleanParser {

  override def Operand: Rule1[AstNode] = rule {
    UrlPathBeginOperand | HostOperand | UserAgentOperand | HeaderOperand | CookieOperand | CookieContainsOperand | HeaderContainsOperand | ValueOperand
  }

  override def ValueOperand: Rule1[AstNode] = rule {
    OptionalWhiteSpace ~ "<" ~ oneOrMore(noneOf("<>")) ~> ((value: String) ⇒ Value(value.trim)) ~ ">"
  }

  def UrlPathBeginOperand = rule {
    UrlPathString ~ ComparisonOperator ~ String ~~> ((equal: Boolean, value: String) ⇒ if (equal) PathPrefix(value) else Negation(PathPrefix(value)))
  }

  def UrlPathString = rule {
    OptionalWhiteSpace ~ ignoreCase("path") ~ optional(anyOf("-.")) ~ ignoreCase("prefix") ~ OptionalWhiteSpace
  }

  def HostOperand = rule {
    HostString ~ ComparisonOperator ~ String ~~> ((equal: Boolean, value: String) ⇒ if (equal) Host(value) else Negation(Host(value)))
  }

  def HostString = rule {
    OptionalWhiteSpace ~ ignoreCase("host") ~ OptionalWhiteSpace
  }

  def UserAgentOperand = rule {
    UserAgentString ~ ComparisonOperator ~ String ~~> ((equal: Boolean, value: String) ⇒ if (equal) UserAgent(value) else Negation(UserAgent(value)))
  }

  def UserAgentString = rule {
    OptionalWhiteSpace ~ ignoreCase("user") ~ optional(anyOf("-.")) ~ ignoreCase("agent") ~ OptionalWhiteSpace
  }

  def CookieOperand = rule {
    OptionalWhiteSpace ~ ContainsOperator ~ CookieString ~ WhiteSpace ~ String ~~> ((equal: Boolean, value: String) ⇒ if (equal) Cookie(value) else Negation(Cookie(value)))
  }

  def CookieContainsOperand = rule {
    OptionalWhiteSpace ~ CookieString ~ WhiteSpace ~ String ~ WhiteSpace ~ ContainsOperator ~ String ~~> ((name: String, equal: Boolean, value: String) ⇒ if (equal) CookieContains(name, value) else Negation(CookieContains(name, value)))
  }

  def CookieString = rule {
    ignoreCase("cookie")
  }

  def HeaderOperand = rule {
    OptionalWhiteSpace ~ ContainsOperator ~ HeaderString ~ WhiteSpace ~ String ~~> ((equal: Boolean, value: String) ⇒ if (equal) Header(value) else Negation(Header(value)))
  }

  def HeaderContainsOperand = rule {
    OptionalWhiteSpace ~ HeaderString ~ WhiteSpace ~ String ~ WhiteSpace ~ ContainsOperator ~ String ~~> ((name: String, equal: Boolean, value: String) ⇒ if (equal) HeaderContains(name, value) else Negation(HeaderContains(name, value)))
  }

  def HeaderString = rule {
    ignoreCase("header")
  }

  def ComparisonOperator = rule {
    Equal | NonEqual | Has | Is | Misses | (Not ~> (_ ⇒ false))
  }

  def ContainsOperator = rule {
    Has | Misses
  }

  def Is = rule {
    ignoreCase("is") ~ WhiteSpace ~> (_ ⇒ true)
  }

  def Has = rule {
    (ignoreCase("has") | ignoreCase("contains")) ~ WhiteSpace ~> (_ ⇒ true)
  }

  def Misses = rule {
    ignoreCase("misses") ~ WhiteSpace ~> (_ ⇒ false)
  }

  def NonEqual = rule {
    ("!=" | "!") ~> (_ ⇒ false)
  }

  def Equal = rule {
    ("==" | "=") ~> (_ ⇒ true)
  }

  def String = rule {
    OptionalWhiteSpace ~ oneOrMore(noneOf(" \n\r\t\f")) ~> (value ⇒ value)
  }
}
