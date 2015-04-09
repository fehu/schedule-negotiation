package feh.tec.agents.util

trait AsString{
  def asString: String
}

trait FromString[T]{
  def fromString: String => T
}
object FromString{
  def apply[T](f: String => T): FromString[T] = new FromString[T] { def fromString = f  }
}
