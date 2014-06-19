package se.marcuslonnberg.stark.utils

sealed trait Validation[+S, +E] {
  def map[T](f: S => T): Validation[T, E]

  def flatMap[SN, EN >: E](f: S => Validation[SN, EN]): Validation[SN, EN]

  def toOption: Option[S]
}

case class Success[+S, +E](value: S) extends Validation[S, E] {
  def map[T](f: S => T) = Success(f(value))

  def flatMap[SN, EN >: E](f: S => Validation[SN, EN]) = f(value)

  def toOption = Some(value)
}

case class Failure[+S, +E](value: E) extends Validation[S, E] {
  def map[T](f: S => T) = Failure(value)

  def flatMap[SN, EN >: E](f: S => Validation[SN, EN]) = Failure(value)

  def toOption = None
}