package feh.tec.agents.schedule

import java.util.UUID

import akka.actor.{SupervisorStrategy, ActorLogging}
import feh.tec.agents.comm._
import feh.tec.agents.comm.agent.{Reporting, SystemSupport}

/*  TODO:  several ???  */
class CoordinatorAgent( val id              : SystemAgentId
                      , val reportTo        : SystemAgentRef
                      , val initNegCreators : CoordinatorAgent.InitialNegotiatorsCreators
                        )
  extends NegotiationController
  with NegotiationController.InitialAgents
  with Reporting
  with ActorLogging
{

  val Reporting = new ReportingConfig(messageSent = true, messageReceived = true)

  /** A SystemMessage was sent message by an agent with not a SystemAgentId */
  protected def systemMessageFraud(fraud: SystemMessage) = assert(fraud.sender.id.isInstanceOf[UserAgentId], "fraud: " + fraud)

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

  def messageReceived: PartialFunction[Message, Unit] = {
    case CoordinatorAgent.ExtraScopeRequest(ProfessorAgent.Role.PartTime, _) =>
      sender() ! negotiatorsByRole.getOrElse(ProfessorAgent.Role.PartTime, Nil).toSet
    case _: Messages.NoCounterpartFound => // todo
  }

  protected def unknownSystemMessage(sysMsg: SystemMessage): Unit = {}

  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy
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

  case class InitialNegotiatorsCreators( groups             : Seq[NegotiatingAgentCreator[GroupAgent]]
                                       , professorsFullTime : Seq[NegotiatingAgentCreator[ProfessorAgent]]
                                       , professorsPartTime : Seq[NegotiatingAgentCreator[ProfessorAgent]]
                                       // todo: ClassRoom
                                         )
  {
    def join = groups ++ professorsFullTime ++ professorsPartTime
  }

  def role = SystemAgentRole("Coordinator")

  def creator(reportTo: SystemAgentRef,
              initNegCreators: CoordinatorAgent.InitialNegotiatorsCreators) =
    new SystemAgentCreator[CoordinatorAgent](role,
                                             scala.reflect.classTag[CoordinatorAgent],
                                             id => _ => new CoordinatorAgent(id, reportTo, initNegCreators)
    )
}