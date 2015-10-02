Message Flow 
============ 


* 
    * [PartialFunction](#partialfunction)
* [Terminology](#terminology)
* [Group Agent](#group-agent)
    * 
      * [Handle New Negotiations](#handle-new-negotiations)
      * [Handle Message](#handle-message)
      * [Handle Students](#handle-students)
    * [Goal Achivement](#goal-achivement)
* [Professor Agent](#professor-agent)
    * 
      * [Handle New Negotiation / Handle Negotiation Propositions](#handle-new-negotiation--handle-negotiation-propositions)
      * [Handle Negotiation Start](#handle-negotiation-start)
      * [Handle Negotiation](#handle-negotiation)
    * [Goal Achivement](#goal-achivement-1)
* [Common Definitions](#common-definitions)
    * [Utility](#utility)
* [Coordinator Agent](#coordinator-agent)

---

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
— <cite><a href="http://www.scala-lang.org/api/2.11.7/#scala.PartialFunction">Scala API</a></cite>
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
- ![discipline-priority](https://latex.codecogs.com/png.latex?{\\mathrm{discipline~priority}~ =~\\frac{\\sum \\mathrm{groups}~|~\\mathrm{chose}~\\mathbf{discipline}}{\\sum \\mathrm{professors}~|~\\mathrm{can~teach}~\\mathbf{discipline}}})

  **TO DO:** the *discipline priority* should be dynamic.

- **timetable** TBD


Group Agent
-----------
A *group* agent represents a group of *students*, united for the purpose of studying a *discipline*, **one per group**. It's goal is to encounter and come to an agreement over a *class* with the best suited *professor*, able to teach the *discipline*.

[[scaladoc](http://fehu.github.io/schedule-negotiation/docs/dev-api/index.html#feh.tec.agents.schedule.GroupAgent), [source](/src/main/scala/feh/tec/agents/schedule/GroupAgent.scala)]

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
4. Sends the (*counter-*)*proposal* to the *counterpart* agent. Sets the proposal in `AwaitingResponse` state. 
5. Awaits the corresponding response, expecting `ClassesAcceptance` or `ClassesCounterProposal` (or `ClassesRejection`) message. 

* (5A) **In case of an acceptance**, the agent tries to put the *class* into it's *timetable*. 
  * (+) In case of success, it reports termination (**TO DO:** not implemented yet). 
  * (-) In case of failure, it generates a `ClassesCounterProposal` and **goes to (3)**.
* (5R) **In case of a rejection**, the agent **goes to (2)** (currently not used).
* (5P) **In case of a counter-proposal**, the agent assesses the **utility** of proposal.
  * (+) In case the *utility* is high enough (`utilityAcceptanceThreshold`), **go to (5A) body**.
  * (-) Otherwise, **go to (5A-) body**.

It interracts with professors' decision partial functions `handleNegotiationStart` and `handleNegotiation`.

#### Handle Students

It handles orders to add/remove a *student* to/from a *group*.

The logic is pretty straight forward.


#### Goal Achivement

The *goal achivement* is the **ratio of** the sum of assigned classes *lengths* **to** the sum of the required ones.
 * **If** the *ratio* > 1, **return** 0.
 * It is ensured that all the *classes* have the right *discipline*.
 * Recieves the information from a *goal holder*.

- the *goal holder* is implemented with `MutableTimetable`;
- uses [implicit](http://docs.scala-lang.org/tutorials/tour/implicit-parameters.html) `timeDescriptor`.

Professor Agent
---------------
A *professor* agent represent a university professor. In the current implementation there are two types of professors: **full-time** and **part-time**. They are distinguished by agent's *role*. Each professor knows the disciplines he can teach.

[[scaladoc](http://fehu.github.io/schedule-negotiation/docs/dev-api/index.html#feh.tec.agents.schedule.ProfessorAgent), [source](/src/main/scala/feh/tec/agents/schedule/ProfessorAgent.scala)]

---

Currently a *professor*'s behaviour is described by three partial functions:

 - `handleNewNegotiation` (`handleNegotiationPropositions` in the code)
 - `handleNegotiationStart`
 - `handleNegotiation`
  
#### Handle New Negotiation / Handle Negotiation Propositions

The function handles `NegotiationProposition`s, sent by *groups*.

On receive of such message, the agent's response is based on whether he can or cannot teach the *discipline*, included in the message. Possible responses are `NegotiationAcceptance` and `NegotiationRejection`. In case of acceptance, the agent creates and guards the *negotiation*, guarding the proposition's sender as negotiation's *counterpart*.

Interracts with groups' decision partial functions `handleNewNegotiations`.

#### Handle Negotiation Start

This function is responsible only for calculating *discipline priority* and passing it to the *groups*, thus begining the negotiation for *classes* *time*.

1. Every time a `CounterpartsFound` message is received, it's guarded by the agent. 
2. This repeats until the message has been received from all the negotiations' counterparts. 
3. The agent ensures that a number of found counterparts is the same for each group with the same *discipline*.
4. It calculates the *discipline priority* by dividing the number of it's own negotiations with groups by the number of counterparts, reported by *groups*.
5. Notifies the counterpart of each negotiation with the corresponding *priority discipline* by sending `DisciplinePriorityEstablished` messages.

Interracts with groups' decision partial functions `handleNewNegotiations` and `handleMessage`.

#### Handle Negotiation

This `PartialFunction` handles the negotiation over *class*(es) *time*.

- On receive of a `ClassesProposalMessage` message, the agent assesses it's utility and responds with
  * `ClassesAcceptance`, if the utility is higher than `utilityAcceptanceThreshold`;
  * `ClassesRejection`, otherwise.
- On receive of a `ClassesAcceptance` message, the agent tries to guard it into it's `timetable`. If it fails, responds with a `ClassesCounterProposal`. (**TO DO:** not implemented yet)

#### Goal Achivement
**TO DO:** not implemented yet

Common Definitions
------------------

#### Utility
UtilityDriven: [[scaladoc](http://fehu.github.io/schedule-negotiation/docs/dev-api/index.html#feh.tec.agents.schedule.UtilityDriven@inheritance-diagram), [source](/src/main/scala/feh/tec/agents/schedule/UtilityDriven.scala)]

AgentExt: [[source](/src/main/scala/feh/tec/agents/schedule/AgentExt.scala)]

The *utility* is a mean to assess *proposals*. It is calculated based on 
- the *proposal*;
- the negotiation time;
- current *goal holder*; is implemented with `MutableTimetable`;

1. Ensure that the *proposal* satisfies *timetable* constraints. If not, return 0.
2. Calculate *&Delta;goal*:
   * [Assume](http://fehu.github.io/schedule-negotiation/docs/dev-api/index.html#feh.tec.agents.schedule.AgentUtility@assumeProposal(gh:AgentUtility.this.GoalHolder,proposal:AgentUtility.this.ProposalType):AgentUtility.this.GoalHolder) *proposal* and calculate the resulting *goal* value.
   *  Subtract the *goal* value for the current state from the assumed one.
3. If the *&Delta;goal* > 0, multiply it with a sum of the [weightedPriority](http://fehu.github.io/schedule-negotiation/docs/dev-api/index.html#feh.tec.agents.schedule.AgentUtility@weightedPriority(proposal:AgentUtility.this.ProposalType):Double) and [preference](http://fehu.github.io/schedule-negotiation/docs/dev-api/index.html#feh.tec.agents.schedule.AgentUtility@preference(time:AgentPreferences.this.NegotiationTime,gh:AgentPreferences.this.GoalHolder,proposal:AgentPreferences.this.ProposalType):feh.util.InUnitInterval). Else return 0.

Coordinator Agent
-----------------

TBD

References:
----------
#### Agents: CSP (DSCP)
- Makoto Yokoo, Toru Ishida, Edmund H. Durfee, Kazuhiro Kuwabara. "The Distributed Constraint
Satisfaction Problem: Formalization and Algorithms". IEEE TRANSACTIONS ON KNOWLEDGE AND DATA ENGINEERING, VOL. 10, NO. 5, SEPTEMBERiOCTOBEiR 1998. p. 673-685.
- Makoto Yokoo, Katsutoshi Hirayama. "Distributed Constraint Satisfaction Algorithm for Complex Local Problems". 1998 IEEE.
- Najla Sassi, Khaled Ghédira, Kais Ben Salah. "Using Multi-Agent System to solve Distributed Constraint Optimization Problems". 2014 IEEE.
- Raziyeh Moghaddas, Eghbal Mansoori, Ali Hamzeh. "A New Multi-Agent Algorithm for Solving Constraint Satisfaction Problems". 2013 IEEE.
- Jing Liu, Weicai Zhong, and Licheng Jiao. "A Multiagent Evolutionary Algorithm for Constraint Satisfaction Problems". 2006 IEEE.

#### (University) Scheduling
- Jong Yih Kuo, Hsuan-Kuei Cheng, Yong-Yi FanJiang, Shang-Pin Ma. "Multi-Agent Automatic Negotiation and Argumentation for Courses Scheduling". 2011 IEEE.
- A. Gîrbea, C. Suciu, F. Sisak. "Design and implementation of a fully automated planner-scheduler constraint satisfaction problem". 2011 IEEE.
- Makoto Ohki, Akio Morimoto, Kosuke Miyake. "Nurse Scheduling by Using Cooperative GA with Efficient Mutation and Mountain-Climbing Operators". 2006 IEEE.
- Qi Kang, Hui Xiao, Lei Wang, Qidi Wu. "A Swarm-Dynamic Scheduling Method for Semiconductor Assembly Production Line". 2008 IEEE.
- Antariksha Bhaduri. "University Time Table Scheduling using Genetic Artificial Immune Network". 2009 IEEE.
- Sehraneh Ghaemi, Mohammad Taghi Vakili, Ali Aghagolzadeh. "USING A GENETIC ALGORITHM OPTIMIZER TOOL TO SOLVE
UNIVERSITY TIMETABLE SCHEDULING PROBLEM". 2007 IEEE.
- Adalet Oner, Sel Ozcan, Derya Dengi. "Optimization Of University Course Scheduling Problem With A Hybrid Artificial Bee Colony Algorithm". 2011 IEEE.
- YuZheng, lingfa Liu. "A Novel Quantum-inspired Genetic Algorithm For a Weekly University Scheduling Optimization". 2011 IEEE.
- Mohammed Aldasht, Mahmoud Alsaheb, Safa Adi, Mohammad Abu Qopita. "University Course Scheduling Using Evolutionary Algorithms". 2009 IEEE.
- Ayed. A. Salman, Suha A. Hamdan. "Solving Department's Course-scheduling Problem using Differential Evolution". International Conference on Methodsand Models in ComputerScience, 2009.

#### Other CSP (DSCP) solving methods
- Mohamed ETTAOUIL, Khalid HADDOUCH, Youssef HAMI, LOQMAN Chakir. "Neural networks approach for solving the Maximal
Constraint Satisfaction Problems". 2013 IEEE.
- Kazunori Mizuno, Daiki Hayakawa, Hitoshi Sasaki, Seiichi Nishihara. "Solving Constraint Satisfaction Problems by ACO with Cunning Ants". 2011 IEEE.
- Hrishikesh J. Goradia. "Ants with Limited Memory for Solving Constraint Satisfaction Problems". 2013 IEEE.
- José C. Ortiz-Bayliss, Hugo Terashima-Marín, Santiago E. Conant-Pablos. "Using Learning Classifier Systems to Design
Selective Hyper-Heuristics for Constraint Satisfaction Problems". 2013 IEEE.
