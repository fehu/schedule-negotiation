package feh.tec.agents.schedule

import java.util.UUID

import akka.actor.{OneForOneStrategy, SupervisorStrategy, ActorLogging}
import feh.tec.agents.comm.NegotiationController.AgentsManipulation
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
  with CoordinatorAgentStudentsHandling
  with Reporting
  with ActorLogging
{

  def stopped = false

  val Reporting = new ReportingConfig()

  /** A SystemMessage was sent message by an agent with not a SystemAgentId */
//  protected def systemMessageFraud(fraud: SystemMessage) = assert(fraud.sender.id.isInstanceOf[UserAgentId], "fraud: " + fraud)

  def initialNegotiatorsCreators = initNegCreators.join

  def nameForAgent(role: NegotiationRole, index: Int): String = role.role.filterNot(_.isWhitespace) + "-" + index

  def systemMessageReceived: PartialFunction[SystemMessage, Unit] = {
    case _: SystemMessage.Start       => start()
    case _: SystemMessage.Stop        =>
      log.debug("========================== SystemMessage.Stop ==========================")
      log.debug("negotiatorsByRole sizes: " + negotiatorsByRole.mapValues(_.size))
      stop()
//      context.stop(self)
    case _: SystemMessage.Initialize  => initialize()
    case _: ControllerMessage.Begin   => startNegotiation()
  }

  def initializeNegotiator(ref: NegotiatingAgentRef): Unit = ref ! SystemMessage.Initialize(agentInit(ref.id.role): _*)(this.ref)

  def agentInit: NegotiationRole => Seq[SystemMessage] = {
    case role@GroupAgent.Role =>
      SystemMessage.SetScope(SharedNegotiation.id, negotiatorsByRole(ProfessorAgent.Role.FullTime).toSet) :: Nil
    case _ => Nil
  }

  def messageReceived = handleMessages orElse handleStudents

  protected var searching = false

  def handleMessages: PartialFunction[Message, Unit] = {
    case CoordinatorAgent.ExtraScopeRequest(ProfessorAgent.Role.PartTime, _) =>
      sender() ! negotiatorsByRole.getOrElse(ProfessorAgent.Role.PartTime, Nil)
    case _: Messages.NoCounterpartFound => // todo
    case GroupAgent.StartSearchingProfessors() =>
      searching = true
      groups.values.par.foreach(_ ! GroupAgent.StartSearchingProfessors())
  }

  protected def unknownSystemMessage(sysMsg: SystemMessage): Unit = {}

  override def supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 0){
                                                                           case ex: Exception =>
                                                                             val id = searchNegotiator(sender()).get.id
                                                                             log.debug(id + " failed: " + ex)
                                                                             reportTo ! Report.Error(id, ex)
                                                                             delAgent(id)
                                                                             SupervisorStrategy.Stop
                                                                         }
}


trait CoordinatorAgentStudentsHandling{
  agent: NegotiationController with Reporting with AgentsManipulation =>


  def timeouts: Timeouts
  def schedulePolicy: SchedulePolicy
  protected def searching: Boolean

  protected val groups = mutable.HashMap.empty[(Discipline, StudentAgent.Career, Int), NegotiatingAgentRef]

  protected def newGroup(discipline: Discipline, career: StudentAgent.Career, counter: Int) = {
    val grId = GroupId(UUID.randomUUID().toString)
    val group = newAgent(GroupAgent.creator(reportTo, grId, discipline, timeouts, schedulePolicy))
    groups += (discipline, career, counter) -> group
    group ! SystemMessage.Start()
    initializeNegotiator(group)
    if(searching) group ! GroupAgent.StartSearchingProfessors()
    group
  }

  def getGroups(discipline: Discipline, career: StudentAgent.Career) = groups.collect{
    case ((`discipline`, `career`, _), group) => group
  }.toList

  def getGroupsNonEmpty(discipline: Discipline, career: StudentAgent.Career) = getGroups(discipline, career) match {
    case Nil => newGroup(discipline, career, 0) :: Nil
    case list => list
  }

  def handleStudents: PartialFunction[Message, Unit] = {
    case msg@StudentAgent.AssignMeToGroups(studentId, disciplines) =>
      assert(msg.sender.id.role == StudentAgent.Role, "sent not by a student: " + msg)
      for{
        d <- disciplines
        groups = getGroupsNonEmpty(d, msg.studentId.career)
      } groups.head ! GroupAgent.AddStudent(d, studentId, msg.sender.asInstanceOf[NegotiatingAgentRef])
    case GroupAgent.GroupIsFull(discipline, studentId, studentRef, nTry) =>
      val groups = getGroupsNonEmpty(discipline, studentId.career)
      if(groups.size < nTry+1) groups(nTry+1)
      else newGroup(discipline, studentId.career, groups.size)
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