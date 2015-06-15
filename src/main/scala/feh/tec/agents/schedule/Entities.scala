package feh.tec.agents.schedule

import feh.tec.agents.util.{FromString, AsString}

case class Discipline(code: String, name: String)

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

case class ClassId(uniqueId: String) extends AsString{  def asString = uniqueId  }

case class GroupId    (uniqueId: String) extends AsString{  def asString = uniqueId  }
case class ProfessorId(uniqueId: String) extends AsString{  def asString = uniqueId  }
//case class ProfessorId[Name](name    : Name  ) extends AsString{  def asString = name.toString  }
case class ClassRoomId() // todo

//object Ids{
//  implicit def groupIdFromString                      : FromString[GroupId]           = FromString( GroupId )
//  implicit def professorIdFromString[Name: FromString]: FromString[ProfessorId[Name]] = FromString(
//    name =>
//      ProfessorId( implicitly[FromString[Name]].fromString(name) )
//  )
//}

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