package feh.tec.agents.schedule

case class Discipline(code: String, name: String, classes: Discipline.MinutesPerWeek, labs: Discipline.MinutesPerWeek)


object Discipline{
  type MinutesPerWeek = Int
}

object DaysOfWeek extends Enumeration{
  val Mon, Tue, Wed, Thu, Sat, Sun = Value
}

case class Class[Time]( id        : ClassId,
                        discipline: Discipline
                      , dayOfWeek : DayOfWeek
                      , begins    : Time
                      , ends      : Time
                      , group     : GroupId
                      , professor : ProfessorId
                      , classroom : ClassRoomId
                        )

//trait EntityId{
//  def uniqueId: String
//}

case class ClassId(uniqueId: String) //extends EntityId

case class StudentId  (tag: String, career: StudentAgent.Career)
case class GroupId    (uniqueId: String) //extends EntityId
case class ProfessorId(uniqueId: String) //extends EntityId
case class ClassRoomId(uniqueId: String) //extends EntityId

trait TimeDescriptor[Time]{
  def domain: Stream[Time]

  def randomly: Stream[Time]

  def toMinutes(t: Time): Int
  def fromMinutesOpt(t: Int): Option[Time]
  def fromMinutes(t: Int): Time = fromMinutesOpt(t).getOrElse(sys.error(s"$t is out of range"))

  def beginning: Time
  def ending: Time

  def plus(t1: Time, t2: Int): Time = fromMinutes(toMinutes(t1) + t2)
}

// todo: not every week ?
trait TimetableAccess[Time] extends TimeTableRead[Time] with TimeTableWrite[Time]

trait TimeTableRead[Time]{
  def classAt(day: DayOfWeek, time: Time): Option[ClassId]
  def classesAt(day: DayOfWeek, from: Time, to: Time): Seq[ClassId]

  def allClasses: Seq[ClassId]

  def busyAt(day: DayOfWeek, time: Time): Boolean = classAt(day, time).nonEmpty
  def busyAt(day: DayOfWeek, from: Time, to: Time): Boolean = classesAt(day, from, to).nonEmpty
}

trait TimeTableWrite[Time]{
  def putClass(day: DayOfWeek, from: Time, to: Time, clazz: ClassId)

}