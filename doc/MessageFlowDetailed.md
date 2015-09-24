Message Flow 
============ 
The agents used for the negotiation are reactive: they act only in response to a message.
The action taken is determined by `messageReceived` method. It’s type is `PartialFunction[Message, Unit]`.

The `messageReceived` functions for the agents are presented in the 
[Message Flow diagram](http://fehu.github.io/schedule-negotiation/docs/MessageFlow.pdf).

#### PartialFunction
<blockquote>
<p>
A partial function of type `PartialFunction[A, B]` is a unary function
  where the domain does not necessarily include all values of type `A`.
  The function `isDefinedAt` allows to test dynamically if a value is in
  the domain of the function.
</p>
<footer>
— <cite><a href="http://www.scala-lang.org/api/2.11.7/#scala.PartialFunction/">Scala API</a></cite>
</footer>
</blockquote>

A `PartialFunction[A, B]` also defines
```scala
orElse[A1 <: A, B1 >: B](that: PartialFunction[A1, B1]): PartialFunction[A1, B1]
```
<blockquote>
Composes this partial function with a fallback partial function which
gets applied where this partial function is not defined.
</blockquote>

It is used widely used on the diagram.
