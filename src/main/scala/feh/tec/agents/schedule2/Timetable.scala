package feh.tec.agents.schedule2

import feh.util._

class DTime protected(val discrete: Int) extends Ordered[DTime]{
  def compare(that: DTime): Int = this.discrete compare that.discrete

  override def toString: String = s"DiscreteTime($discrete)"
}

object DTime{
  def apply(i: Int): DTime = new DTime(i.ensuring(_ >= 0, "time must be positive, got " + i))

  trait Descriptor extends TimeDescriptor[DTime]{
    def n: Int
  }

  def descriptor(mBegin: Int, mEnd: Int, mStep: Int): Descriptor = new Descriptor{
    lazy val n = divEnsuringIntegerResult(mEnd - mBegin, mStep)

    def step = mStep

    def beginning = DTime(0)
    def ending    = DTime(n)

    lazy val domain: Stream[DTime] = Stream.from(0).map(DTime.apply).take(n)

    def minutesToDiscrete(t: Int) = divEnsuringIntegerResult(t, mStep)

    def fromMinutesOpt(t: Int): Option[DTime] = divEnsuringIntegerResult(t - mBegin, mStep) match {
      case ok if ok <= n && ok >= 0 => Some(DTime(ok))
      case _ => None
    }

    def toMinutes(t: DTime): Int = t.discrete*mStep + mBegin

    def randomly: Stream[DTime] = Stream((0 to n).randomOrder: _*).map(DTime.apply)

    def humanReadable(t: DTime) = {
      val m = toMinutes(t)
      (m / 60) + (":%02d" format m % 60)
    }

    def compare(x: DTime, y: DTime) = x.discrete compare y.discrete
  }


  private def divEnsuringIntegerResult(i1: Int, i2: Int) = {
    assert(i1 % i2 == 0, "i1 % i2 = " + i1 % i2)
    i1 / i2
  }
}

trait Timetable[T]{
  def asMap: Map[DayOfWeek, Map[DTime, T]]
}

object ImmutableTimetable{
  def opts[T](asMap: Map[DayOfWeek, Map[DTime, Option[T]]]): ImmutableTimetable[T] =
    ImmutableTimetable(filterEmpty(asMap))

  def filterEmpty[T](opts: Map[DayOfWeek, Map[DTime, Option[T]]]): Map[DayOfWeek, Map[DTime, T]] =
    opts.mapValues(_.filter(_._2.isDefined).mapValues(_.get)).filter(_._2.nonEmpty)
}

case class ImmutableTimetable[T](asMap: Map[DayOfWeek, Map[DTime, T]]) extends Timetable[T]

class MutableTimetable[T](implicit timeDescr: DTime.Descriptor)
  extends Timetable[Option[T]]
  with TimetableAccess[DTime, T]
{
  protected val timeTable: Map[DayOfWeek, Array[Option[T]]] =
    DaysOfWeek.values.toSeq
      .map{ _ -> Array.fill[Option[T]](timeDescr.n+1)(None) }
      .toMap


  def asMap: Map[DayOfWeek, Map[DTime, Option[T]]] = timeTable.mapValues{
    arr =>
      timeDescr.domain.zip(arr).toMap
    }

  def all: Seq[T] = timeTable.toStream.flatMap(_._2.withFilter(_.isDefined).map(_.get))

  def at(day: DayOfWeek, time: DTime): Option[T] = timeTable(day)(time.discrete)
  def at(day: DayOfWeek, from: DTime, to: DTime): Seq[T] = timeTable(day)
                                                          .drop(from.discrete)
                                                          .take(to.discrete - from.discrete)
                                                          .toSeq.flatten


  def put(day: DayOfWeek, from: DTime, to: DTime, t: T): Either[IllegalArgumentException, Unit] = {
    if(!busyAt(day, from, to))  Right(for(i <- from.discrete to to.discrete) timeTable(day)(i) = Option(t))
    else Left(new IllegalArgumentException(s"busyAt($day, $from, $to)"))
  }

  trait CopyExt extends MutableTimetable[T]{
    ext =>

    def _put(day: DayOfWeek, time: DTime, t: T) = timeTable(day)(time.discrete) = Option(t)
  }

  def copy: MutableTimetable[T] = new MutableTimetable[T] with CopyExt $$ {
    mtt =>
      val asMap = ImmutableTimetable.filterEmpty(this.asMap)
      for {
        (day, m) <- asMap
        (time, v) <- m
      } mtt._put(day, time, v)
  }
}
