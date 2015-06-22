package feh.tec.agents.schedule

import java.util.UUID

import akka.actor.{SupervisorStrategy, ActorLogging}
import feh.tec.agents.comm._
import feh.tec.agents.comm.agent.{Reporting, SystemSupport}
import feh.tec.agents.schedule.CommonAgentDefs.Timeouts
import feh.util._

import scala.collection.mutable

/*  TODO:  several ???  */
class CoordinatorAgent( val id              : SystemAgentId
                      , val reportTo        : SystemAgentRef
                      , val timeouts        : Timeouts
                      , val schedulePolicy  : SchedulePolicy
                      , val initNegCreators : CoordinatorAgent.InitialNegotiatorsCreators
                        )
  extends NegotiationController
  with NegotiationController.InitialAgents
  with NegotiationController.AgentsManipulation
  with Reporting
  with ActorLogging
{

  val Reporting = new ReportingConfig(messageSent = true, messageReceived = true)

  /** A SystemMessage was sent message by an agent with not a SystemAgentId */
//  protected def systemMessageFraud(fraud: SystemMessage) = assert(fraud.sender.id.isInstanceOf[UserAgentId], "fraud: " + fraud)

  def initialNegotiatorsCreators = initNegCreators.join

  def nameForAgent(role: NegotiationRole, index: Int): String = role.role.filterNot(_.isWhitespace) + "-" + index

  def systemMessageReceived: PartialFunction[SystemMessage, Unit] = {
    case _: SystemMessage.Start       => start()
    case _: SystemMessage.Stop        => stop()
    case _: SystemMessage.Initialize  => initialize()
    case _: ControllerMessage.Begin   => startNegotiation()
  }

  def initializeNegotiator(ref: NegotiatingAgentRef): Unit = ref ! SystemMessage.Initialize(agentInit(ref.id.role): _*)(this.ref)

  def agentInit: NegotiationRole => Seq[SystemMessage] = {
    case role@GroupAgent.Role =>
      SystemMessage.SetScope(SharedNegotiation.id, negotiatorsByRole(ProfessorAgent.Role.FullTime).toSet) :: Nil
    case _ => Nil
  }

  def messageReceived = handleMessages

  def handleMessages: PartialFunction[Message, Unit] = {
    case CoordinatorAgent.ExtraScopeRequest(ProfessorAgent.Role.PartTime, _) =>
      sender() ! negotiatorsByRole.getOrElse(ProfessorAgent.Role.PartTime, Nil).toSet
    case _: Messages.NoCounterpartFound => // todo
  }

  protected def unknownSystemMessage(sysMsg: SystemMessage): Unit = {}

  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy
}


trait CoordinatorAgentStudentsHandling{
  agent: NegotiationController with Reporting =>


  def timeouts: Timeouts
  def schedulePolicy: SchedulePolicy

  protected val groups = mutable.HashMap.empty[(Discipline, StudentAgent.Career, Int), NegotiatingAgentRef]

  protected def newGroup(discipline: Discipline)=
    mkNegotiators(GroupAgent.creator(reportTo, discipline, timeouts, schedulePolicy))

  def getGroups(discipline: Discipline, career: StudentAgent.Career) = groups.collect{
    case ((`discipline`, `career`, _), group) => group
  }.toList

  def getGroupsNonEmpty(discipline: Discipline, career: StudentAgent.Career) = getGroups(discipline, career) match {
    case Nil => newGroup(discipline)
    case list => list
  }

  def handleStudents: PartialFunction[Message, Unit] = {
    case msg@StudentAgent.AssignMeToAGroup() =>
      assert(msg.sender.id.role == StudentAgent.Role, "sent not by a student: " + msg)
      ???
  }
}

object CoordinatorAgent{
  case class ExtraScopeRequest( role: NegotiationRole
                              , uuid: UUID              = UUID.randomUUID() )
                              ( implicit val sender: AgentRef)
    extends Message
  {
    val tpe = "ExtraScopeRequest"
    val asString = s"for role $role"
  }

  type Creators[Ag <: NegotiatingAgent] = Iterable[NegotiatingAgentCreator[Ag]]

  case class InitialNegotiatorsCreators( students           : Creators[StudentAgent]
                                       , groups             : Creators[GroupAgent]
                                       , professorsFullTime : Creators[ProfessorAgent]
                                       , professorsPartTime : Creators[ProfessorAgent]
                                       // todo: ClassRoom
                                         )
  {
    def join = (students ++ groups ++ professorsFullTime ++ professorsPartTime).toSeq
  }

  def role = SystemAgentRole("Coordinator")

  def creator( reportTo       : SystemAgentRef
             , timeouts       : Timeouts
             , schedulePolicy : SchedulePolicy
             , initNegCreators: CoordinatorAgent.InitialNegotiatorsCreators) =
    new SystemAgentCreator[CoordinatorAgent](role,
                                             scala.reflect.classTag[CoordinatorAgent],
                                             id => _ => new CoordinatorAgent(id, reportTo, timeouts, schedulePolicy, initNegCreators)
    )
}