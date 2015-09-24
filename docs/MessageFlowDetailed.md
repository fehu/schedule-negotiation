Contents
========

* [Message Flow](#message-flow)
  * [PartialFunction](#partialfunction)
* [Terminology](#terminology)
* [Group Agent](#group-agent)
  * [Handle New Negotiations](#handle-new-negotiations)
  * [Handle Message](#handle-message)
  * [Handle Students](#handle-students)


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

It is widely used on the diagram.

Terminology
-----------

- A **discipline** is an *abstract* **class** descriptor; the one that the students choose to study.
- A **class** is a *specific implementation* of a **discipline**, that has *time*, a *group*, a *professor* and a *class room* assigned.
- An agent's **scope** is a set of agents, that can be comunicated with.
- ![discipline-priority](https://latex.codecogs.com/png.latex?{\\mathrm{discipline~priority}~ =~\\frac{\\sum \\mathrm{professors}~|~\\mathrm{can~teach}~\\mathbf{discipline}}{\\sum \\mathrm{groups}~|~\\mathrm{chose}~\\mathbf{discipline}}})

  **TO DO**: the *discipline priority* should be dynamic.

- **timetable** TBD


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
2. A *group* agent then awaits for a response, that is expected to be either `NegotiationAcceptance` or `NegotiationRejection`. It guards the responses until all of them have been received. In case of acceptance, it creates a new *negotiation*, setting the sender as negotiation's *counterpart*.
3. Then it sends a `CounterpartsFound` message, including the number of *professors* that responded positively to each of such *professors*. Thus starts the `handleMessage` part of the negotiation. 

* In case that all the *professors* asked responded with a `NegotiationRejection`, a group agent ask the *coordinator* for extra *scope* &mdash; the *part-time professors*. In case that even then an agent is unable to establish at least one negotiation, it notifies the *coordinator* of it's failure with a `NoCounterpartFound` message.

It interracts with *professors*' decision partial functions `handleNewNegotiations` and `handleNegotiationStart`.

#### Handle Message

This `PartialFunction` handles the negotiation over *class*(es) *time*.

1. An agent awaits a `DisciplinePriorityEstablished` message, that (as follows from the name) contains the *discipline priority*, established by the *professor*. It guards the priority.
2. Generates a *class proposal*.
3. Guards the (*counter-*)*proposal* in the `CurrentProposal` state.
3. Sends the (*counter-*)*proposal* to the *counterpart* agent. Sets the proposal in `AwaitingResponse` state. 
4. Awaits the corresponding response, expecting `ClassesAcceptance` or `ClassesCounterProposal` (or `ClassesRejection`) message. 

* (4A) **In case of an acceptance**, the agent tries to put the *class* into it's *timetable*. 
  * (+) In case of success, it reports termination (**TO DO**: not implemented yet). 
  * (-) In case of failure, it generates a `ClassesCounterProposal` and **goes to (3)**.
* (4R) **In case of a rejection**, the agent **goes to (2)** (currently not used).
* (4P) **In case of a counter-proposal**, the agent assesses the **utility** of proposal.
  * (+) In case the *utility* is high enough (`utilityAcceptanceThreshold`), **go to (4A) body**.
  * (-) Otherwise, **go to (4A-) body**.

It interracts with professors' decision partial functions `handleNegotiationStart` and `handleNegotiation`.

#### Handle Students

It handles orders to add/remove a *student* to/from a *group*.

The logic is pretty straight forward.


#### Group's Goal Achivement

The *goal achivement* is the **ratio of** the sum of assigned classes *lengths* **to** the sum of the required ones.
 * **If** the *ratio* > 1, **return** 0.
 * It is ensured that all the *classes* have the right *discipline*.
 * Recieves the information from a *goal holder*.

- the *goal holder* is implemented with `MutableTimetable`;
- uses [implicit](http://docs.scala-lang.org/tutorials/tour/implicit-parameters.html) `timeDescriptor`.

Professor Agent
---------------


Common Definitions
------------------

#### Utility
[[scaladoc](http://fehu.github.io/schedule-negotiation/docs/dev-api/index.html#feh.tec.agents.schedule.UtilityDriven@inheritance-diagram), [source](/src/main/scala/feh/tec/agents/schedule/UtilityDriven.scala)]

The *utility* is a mean to assess *proposals*. It is calculated based on 
- the *proposal*;
- the negotiation time;
- current *goal holder*; is implemented with `MutableTimetable`;

1. Ensure that the *proposal* satisfies *timetable* constraints. If not, return 0.
2. Calculate *&Delta;goal*:
   * [Assume](http://fehu.github.io/schedule-negotiation/docs/dev-api/index.html#feh.tec.agents.schedule.AgentUtility@assumeProposal(gh:AgentUtility.this.GoalHolder,proposal:AgentUtility.this.ProposalType):AgentUtility.this.GoalHolder) *proposal* and calculate the resulting *goal* value.
   *  Subtract the *goal* value for the current state from the assumed one.
3. If the *&Delta;goal* > 0, multiply it with a sum of the [weightedPriority](http://fehu.github.io/schedule-negotiation/docs/dev-api/index.html#feh.tec.agents.schedule.AgentUtility@weightedPriority(proposal:AgentUtility.this.ProposalType):Double) and [preference](http://fehu.github.io/schedule-negotiation/docs/dev-api/index.html#feh.tec.agents.schedule.AgentUtility@preference(time:AgentPreferences.this.NegotiationTime,gh:AgentPreferences.this.GoalHolder,proposal:AgentPreferences.this.ProposalType):feh.util.InUnitInterval). Else return 0.
