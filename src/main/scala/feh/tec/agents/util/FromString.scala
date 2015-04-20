package feh.tec.agents.util


// todo: used ??

@deprecated
trait AsString{
  def asString: String
}

@deprecated
trait FromString[T]{
  def fromString: String => T
}

@deprecated
object FromString{
  def apply[T](f: String => T): FromString[T] = new FromString[T] { def fromString = f  }
}
