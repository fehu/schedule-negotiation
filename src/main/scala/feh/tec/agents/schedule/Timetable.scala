package feh.tec.agents.schedule

import feh.util.RandomWrappers._

class Time protected (val discrete: Int) extends Ordered[Time]{
  def compare(that: Time): Int = this.discrete compare that.discrete

  override def toString: String = s"DiscreteTime($discrete)"
}

object Time{
  def apply(i: Int): Time = new Time(i.ensuring(_ >= 0))

  trait Descriptor extends TimeDescriptor[Time]{
    def n: Int
  }
  
  def descriptor(mBegin: Int, mEnd: Int, mStep: Int): Descriptor = new Descriptor{
    lazy val n = divEnsuringIntegerResult(mEnd - mBegin, mStep)

    def beginning = Time(0)
    def ending    = Time(n)

    lazy val domain: Stream[Time] = Stream.from(0).map(Time.apply).take(n)
    
    def fromMinutesOpt(t: Int): Option[Time] = divEnsuringIntegerResult(t - mBegin, mStep) match {
      case ok if ok <= n => Some(Time(ok))
      case _ => None
    }

    def toMinutes(t: Time): Int = t.discrete*mStep + mBegin

    def randomly: Stream[Time] = Stream((0 to n).randomOrder(): _*).map(Time.apply)
  }
  
  
  private def divEnsuringIntegerResult(i1: Int, i2: Int) = {
    assert(i1 % i2 == 0, "i1 % i2 = " + i1 % i2)
    i1 / i2
  }
}

trait Timetable[T]{
  def asMap: Map[DayOfWeek, Map[Time, T]]
}


case class ImmutableTimetable[T](asMap: Map[DayOfWeek, Map[Time, T]]) extends Timetable[T]

class MutableTimetable(implicit timeDescr: Time.Descriptor) 
  extends Timetable[Option[ClassId]]
  with TimetableAccess[Time]
{
  protected val timeTable: Map[DayOfWeek, Array[Option[ClassId]]] = DaysOfWeek.values.toSeq.map{
    day => day -> Array.fill[Option[ClassId]](timeDescr.n+1)(None)
  }.toMap


  def asMap: Map[DayOfWeek, Map[Time, Option[ClassId]]] = timeTable.mapValues{
    arr =>
      timeDescr.domain.zip(arr).toMap
    }

  def allClasses: Seq[ClassId] = timeTable.flatMap(_._2.withFilter(_.isDefined).map(_.get)).toSeq

  def classAt(day: DayOfWeek, time: Time): Option[ClassId] = timeTable(day)(time.discrete)
  def classesAt(day: DayOfWeek, from: Time, to: Time): Seq[ClassId] = timeTable(day)
                                                                        .drop(from.discrete)
                                                                        .take(to.discrete - from.discrete)
                                                                        .toSeq.flatten
  

  def putClass(day: DayOfWeek, from: Time, to: Time, clazz: ClassId): Either[IllegalArgumentException, Unit] = {
    if(!busyAt(day, from, to))  Right(for(i <- from.discrete to to.discrete) timeTable(day)(i) = Option(clazz))
    else Left(new IllegalArgumentException(s"busyAt($day, $from, $to)"))
  }
}
