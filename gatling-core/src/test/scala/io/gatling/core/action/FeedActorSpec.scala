/*
 * Copyright 2011-2019 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gatling.core.action

import io.gatling.AkkaSpec
import io.gatling.core.controller.ControllerCommand.Crash
import io.gatling.core.feeder.Feeder
import io.gatling.core.session._

import akka.testkit._

class FeedActorSpec extends AkkaSpec {

  private def createFeedActor[T](feeder: Feeder[T], controller: TestProbe) =
    TestActorRef(FeedActor.props(feeder, controller.ref))

  private val session = Session("scenario", 0, System.currentTimeMillis())

  "FeedActor" should "force the simulation termination if the nb of records to pop can't be fetched from the session" in {
    val controller = TestProbe()
    val failingExpr: Expression[Int] = session => session("failed").validate[Int]
    val feedActor = createFeedActor(Iterator.continually(Map("foo" -> "bar")), controller)

    feedActor ! FeedMessage(session, failingExpr, new ActorDelegatingAction("next", self))
    controller.expectMsgType[Crash]
  }

  it should "force the simulation termination if the nb of records to pop is not strictly positive" in {
    val controller = TestProbe()
    val feedActor = createFeedActor(Iterator.continually(Map("foo" -> "bar")), controller)

    feedActor ! FeedMessage(session, 0.expressionSuccess, new ActorDelegatingAction("next", self))
    controller.expectMsgType[Crash]

    feedActor ! FeedMessage(session, (-1).expressionSuccess, new ActorDelegatingAction("next", self))
    controller.expectMsgType[Crash]
  }

  it should "force the simulation termination if the feeder is empty" in {
    val controller = TestProbe()
    val feedActor = createFeedActor(Iterator.empty, controller)

    feedActor ! FeedMessage(session, 1.expressionSuccess, new ActorDelegatingAction("next", self))
    controller.expectMsgType[Crash]
  }

  it should "simply put an entry from the feeder in the session when polling 1 record at a time" in {
    val controller = TestProbe()
    val feedActor = createFeedActor(Iterator.continually(Map("foo" -> "bar")), controller)

    feedActor ! FeedMessage(session, 1.expressionSuccess, new ActorDelegatingAction("next", self))

    val newSession = expectMsgType[Session]
    newSession.contains("foo") shouldBe true
    newSession("foo").as[String] shouldBe "bar"
  }

  it should "put entries from the feeder suffixed with an index in the session when polling multiple record at a time" in {
    val controller = TestProbe()
    val feedActor = createFeedActor(Iterator.continually(Map("foo" -> "bar")), controller)

    feedActor ! FeedMessage(session, 2.expressionSuccess, new ActorDelegatingAction("next", self))

    val newSession = expectMsgType[Session]
    newSession.contains("foo1") shouldBe true
    newSession("foo1").as[String] shouldBe "bar"
    newSession.contains("foo2") shouldBe true
    newSession("foo2").as[String] shouldBe "bar"
  }
}