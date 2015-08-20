package feh.tec.agents.schedule

case class Discipline(code: String, name: String, classes: Discipline.MinutesPerWeek, labs: Discipline.MinutesPerWeek){
  override def equals(obj: scala.Any) = canEqual(obj) && (obj match {
    case that: Discipline => this.code == that.code
  })
}


object Discipline{
  type MinutesPerWeek = Int
}

object DaysOfWeek extends Enumeration{
  val Mon, Tue, Wed, Thu, Sat, Sun = Value
}

case class Class[Time]( discipline: Discipline
                      , dayOfWeek : DayOfWeek
                      , begins    : Time
                      , ends      : Time
                      , group     : GroupId
                      , professor : ProfessorId
                      , classroom : ClassRoomId
                        )

trait EntityId{
  def uniqueId: String
}

case class ClassId(uniqueId: String) extends EntityId

case class StudentId  (tag: String, career: StudentAgent.Career) extends EntityId{
  def uniqueId = tag
}
case class GroupId    (uniqueId: String) extends EntityId
case class ProfessorId(uniqueId: String) extends EntityId
case class ClassRoomId(uniqueId: String) extends EntityId

object ClassRoomId{
  object Unassigned extends ClassRoomId("--")
} 

trait TimeDescriptor[Time]{
  def domain: Stream[Time]

  def randomly: Stream[Time]

  def step: Int

  def toMinutes(t: Time): Int
  def fromMinutesOpt(t: Int): Option[Time]
  def fromMinutes(t: Int): Time = fromMinutesOpt(t).getOrElse(sys.error(s"$t is out of range"))

  def beginning: Time
  def ending: Time

  def plus(t1: Time, t2: Int): Time = fromMinutes(toMinutes(t1) + t2)

  def humanReadable(t: Time): String
  def hr(t: Time): String = humanReadable(t)
}

// todo: not every week ?
trait TimetableAccess[Time, T] extends TimeTableRead[Time, T] with TimeTableWrite[Time, T]

trait TimeTableRead[Time, T]{
  def at(day: DayOfWeek, time: Time): Option[T]
  def at(day: DayOfWeek, from: Time, to: Time): Seq[T]

  def all: Seq[T]

  def busyAt(day: DayOfWeek, time: Time): Boolean = at(day, time).nonEmpty
  def busyAt(day: DayOfWeek, from: Time, to: Time): Boolean = at(day, from, to).nonEmpty
}

trait TimeTableWrite[Time, T]{
  def put(day: DayOfWeek, from: Time, to: Time, clazz: T): Either[IllegalArgumentException, Unit]

}