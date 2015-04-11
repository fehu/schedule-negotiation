package feh.tec.agents.schedule

import java.util.UUID

import feh.tec.agents.comm._

/*  TODO:  several ???  */
class CoordinatorAgent( val id              : SystemAgentId
                      , val reportTo        : SystemAgentRef
                      , val initNegCreators : CoordinatorAgent.InitialNegotiatorsCreators
                        )
  extends NegotiationController
  with NegotiationController.InitialAgents
{

  def initialNegotiatorsCreators = initNegCreators.join

  def nameForAgent(role: NegotiationRole, index: Int): String = role.role.filterNot(_.isWhitespace) + "-" + index


  def initializeNegotiator(ref: NegotiatingAgentRef): Unit = ref ! SystemMessage.Initialize(???)(this.ref)

  def systemMessageReceived: PartialFunction[SystemMessage, Unit] = {
    case _: SystemMessage.Start => ???
    case _: SystemMessage.Stop  => ???
  }

  def messageReceived: PartialFunction[Message, Unit] = {
    case msg: CoordinatorAgent.ExtraScopeRequest => negotiatorsByRole(msg.role)
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

  case class InitialNegotiatorsCreators( groups             : Seq[NegotiatingAgentCreator[GroupAgent]]
                                       , professorsFullTime : Seq[NegotiatingAgentCreator[ProfessorAgent]]
                                       , professorsPartTime : Seq[NegotiatingAgentCreator[ProfessorAgent]]
                                       // todo: ClassRoom
                                         )
  {
    def join = groups ++ professorsFullTime ++ professorsPartTime
  }
}