package feh.tec.agents.schedule

import java.util.UUID

import feh.tec.agents.comm._
import feh.tec.agents.comm.agent.SystemSupport

/*  TODO:  several ???  */
class CoordinatorAgent( val id              : SystemAgentId
                      , val reportTo        : SystemAgentRef
                      , val initNegCreators : CoordinatorAgent.InitialNegotiatorsCreators
                        )
  extends NegotiationController
  with NegotiationController.InitialAgents
  with SystemSupport
{

  /** A SystemMessage was sent message by an agent with not a SystemAgentId */
  protected def systemMessageFraud(fraud: SystemMessage): Unit = sys.error("fraud: " + fraud)

  def initialNegotiatorsCreators = initNegCreators.join

  def nameForAgent(role: NegotiationRole, index: Int): String = role.role.filterNot(_.isWhitespace) + "-" + index


  def initializeNegotiator(ref: NegotiatingAgentRef): Unit = ref ! SystemMessage.Initialize(agentInit(ref.id.role): _*)(this.ref)

  def agentInit: NegotiationRole => Seq[SystemMessage] = ???

//  def systemMessageReceived: PartialFunction[SystemMessage, Unit] = {
//    case _: SystemMessage.Start => startNegotiation()
//    case _: SystemMessage.Stop  => stop()
//    case SystemMessage.Initialize(init) =>
//  }

  def messageReceived: PartialFunction[Message, Unit] = {
    case CoordinatorAgent.ExtraScopeRequest(ProfessorAgent.Role.PartTime, _) => negotiatorsByRole(ProfessorAgent.Role.PartTime)
    case CoordinatorAgent.Begin(_) => startNegotiation()
  }

  protected def onMessageReceived(msg: Message, unhandled: Boolean): Unit = {}
  protected def onMessageSent(msg: Message, to: AgentRef): Unit = {}
  protected def unknownSystemMessage(sysMsg: SystemMessage): Unit = {}
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

  case class Begin(uuid: UUID = UUID.randomUUID())
                  (implicit val sender: AgentRef) extends Message{
    val tpe = "Begin Negotiations"
    val asString = ""
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