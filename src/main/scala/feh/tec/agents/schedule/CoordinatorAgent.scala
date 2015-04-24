package feh.tec.agents.schedule

import java.util.UUID

import akka.actor.ActorLogging
import feh.tec.agents.comm.ControllerMessage.Begin
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
    case _: SystemMessage.Start => log.debug("START~!!!!!"); start()
    case _: SystemMessage.Stop  => stop()
  }

  def initializeNegotiator(ref: NegotiatingAgentRef): Unit = ref ! SystemMessage.Initialize(agentInit(ref.id.role): _*)(this.ref)

  def agentInit: NegotiationRole => Seq[SystemMessage] = ???

  def messageReceived: PartialFunction[Message, Unit] = {
    case CoordinatorAgent.ExtraScopeRequest(ProfessorAgent.Role.PartTime, _) => negotiatorsByRole(ProfessorAgent.Role.PartTime)
    case _: Begin => startNegotiation()
  }

  protected def unknownSystemMessage(sysMsg: SystemMessage): Unit = {}

  override def start(): Unit = {
    log.debug("START")
    super.start()
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