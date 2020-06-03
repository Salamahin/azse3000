package io.github.salamahin.azse3000.blobstorage
import org.scalatest.matchers.{MatchResult, Matcher}

sealed trait MessageMatcher {
  def apply(remainedMessages: Seq[String]): Either[String, Seq[String]]
}

trait LogMatchers {
  class CompositeMessageMatcher(matchers: Seq[MessageMatcher]) extends Matcher[Seq[String]] {
    override def apply(messages: Seq[String]): MatchResult = {
      matchers
        .foldLeft(Right[String, Seq[String]](messages): Either[String, Seq[String]]) {
          case (remainedMessages, m) => remainedMessages.flatMap(x => m(x))
        }
        .fold(
          err => MatchResult(false, err, "Actual and expected messages are the same"),
          seq => MatchResult(seq.isEmpty, "Too many messages in actual log", "Too less messages in actual log")
        )
    }
  }

  def containMessages(matchers: MessageMatcher*) = new CompositeMessageMatcher(matchers)

  def inOrder(expectedMessages: String*) =
    new MessageMatcher {
      override def apply(remainedMessages: Seq[String]): Either[String, Seq[String]] = {
        val (actualMessages, newRemainedMessages) = remainedMessages.splitAt(expectedMessages.length)

        if (actualMessages == expectedMessages) Right(newRemainedMessages)
        else Left(s"Expected messages in order [${expectedMessages.mkString(";")}], but got [${actualMessages.mkString(",")}]")
      }
    }

  def inAnyOrder(expectedMessages: String*) =
    new MessageMatcher {
      override def apply(remainedMessages: Seq[String]): Either[String, Seq[String]] = {
        val (actualMessages, newRemainedMessages) = remainedMessages.splitAt(expectedMessages.length)

        val expectedGroupped = expectedMessages.groupBy(identity)
        val actualGroupped   = actualMessages.groupBy(identity)

        if (actualGroupped == expectedGroupped) Right(newRemainedMessages)
        else Left(s"Expected messages in any order [${expectedMessages.mkString(";")}], but got [${actualMessages.mkString(",")}]")
      }
    }
}
