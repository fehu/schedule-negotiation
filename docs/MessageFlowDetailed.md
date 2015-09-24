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

Terminology
-----------

- A **discipline** is an *abstract* **class** descriptor; the one that the students choose to study.
- A **class** is a *specific implementation* of a **discipline**, that has *time*, a *group*, a *professor* and a *class room* assigned.
- An agent's **scope** is a set of agents, that can be comunicated with.
- ![discipline-priority](https://latex.codecogs.com/png.latex?{\\mathrm{discipline~priority}~ =~\\frac{\\sum \\mathrm{professors}~|~\\mathrm{can~teach}~\\mathbf{discipline}}{\\sum \\mathrm{groups}~|~\\mathrm{chose}~\\mathbf{discipline}}})

Group Agent
-----------
A *group* agent represents a group of *students*, united for the purpose of studying a *discipline*, **one per group**. It's goal is to encounter and come to an agreement over a *class* with the best suited *professor*, able to teach the *discipline*.

---

In the current implementation a group agent's behaviour is divided into three *partial functions*:
- `handleNewNegotiations`
- `handleMessage`
- `handleStudents`

#### Handle New Negotiations

This `PartialFunction` creates new negotiations with the *professors* in *scope* over the *discipline*.

1. The agent stars to search for the professors, able to teach the *discipline*, on receive of a `StartSearchingProfessors` message. It sends a `NegotiationProposition` message, including in it the desired *discipline*, to every *professor* agent in the *scope*. 
2. A *group* agent then awaits for a response, that is expected to be either `NegotiationAcceptance` or `NegotiationRejection`. It guards the responses until all of them have been received.
3. Then it sends a `CounterpartsFound` message, including the number of *professors* that responded positively to each of such *professors*. Thus starts the `handleMessage` part of the negotiation. 

* In case that all the *professors* asked responded with a `NegotiationRejection`, a group agent ask the *coordinator* for extra *scope* &mdash; the *part-time professors*. In case that even then an agent is unable to establish at least one negotiation, it notifies the *coordinator* of it's failure with a `NoCounterpartFound` message.

It interracts with *professors*' decision partial functions `handleNewNegotiations` and `handleNegotiationStart`.

