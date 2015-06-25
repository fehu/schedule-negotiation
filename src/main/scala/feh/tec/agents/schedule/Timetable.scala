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

    def humanReadable(t: Time) = {
      val m = toMinutes(t)
      (m / 60) + (":%02d" format m % 60)
    }
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

class MutableTimetable[T](implicit timeDescr: Time.Descriptor) 
  extends Timetable[Option[T]]
  with TimetableAccess[Time, T]
{
  protected val timeTable: Map[DayOfWeek, Array[Option[T]]] = DaysOfWeek.values.toSeq.map{
    day => day -> Array.fill[Option[T]](timeDescr.n+1)(None)
  }.toMap


  def asMap: Map[DayOfWeek, Map[Time, Option[T]]] = timeTable.mapValues{
    arr =>
      timeDescr.domain.zip(arr).toMap
    }

  def all: Seq[T] = timeTable.flatMap(_._2.withFilter(_.isDefined).map(_.get)).toSeq

  def at(day: DayOfWeek, time: Time): Option[T] = timeTable(day)(time.discrete)
  def at(day: DayOfWeek, from: Time, to: Time): Seq[T] = timeTable(day)
                                                          .drop(from.discrete)
                                                          .take(to.discrete - from.discrete)
                                                          .toSeq.flatten
  

  def put(day: DayOfWeek, from: Time, to: Time, t: T): Either[IllegalArgumentException, Unit] = {
    if(!busyAt(day, from, to))  Right(for(i <- from.discrete to to.discrete) timeTable(day)(i) = Option(t))
    else Left(new IllegalArgumentException(s"busyAt($day, $from, $to)"))
  }
}
