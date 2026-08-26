/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers.actions

import base.SpecBase
import config.FrontendAppConfig
import controllers.routes
import play.api.mvc.{Action, AnyContent, BodyParsers, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AuthIdentifierActionSpec extends SpecBase {

  class Harness(authAction: IdentifierAction) {
    def onPageLoad(): Action[AnyContent] = authAction(_ => Results.Ok)
  }

  "Auth Identifier Action" - {

    "when the user hasn't logged in" - {
      "must redirect the user to log in " in {
        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig = application.injector.instanceOf[FrontendAppConfig]

          val authAction = new AuthenticatedIdentifierAction(new FakeFailingAuthConnector(new MissingBearerToken), appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value must startWith(appConfig.loginUrl)
        }
      }
    }

    "the user's session has expired" - {
      "must redirect the user to log in " in {
        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig = application.injector.instanceOf[FrontendAppConfig]

          val authAction = new AuthenticatedIdentifierAction(new FakeFailingAuthConnector(new BearerTokenExpired), appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value must startWith(appConfig.loginUrl)
        }
      }
    }

    "the user doesn't have sufficient enrolments" - {
      "must redirect the user to the unauthorised page" in {
        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig = application.injector.instanceOf[FrontendAppConfig]

          val authAction = new AuthenticatedIdentifierAction(new FakeFailingAuthConnector(new InsufficientEnrolments), appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
        }
      }
    }

    "the user doesn't have sufficient confidence level" - {
      "must redirect the user to the unauthorised page" in {
        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig = application.injector.instanceOf[FrontendAppConfig]

          val authAction = new AuthenticatedIdentifierAction(new FakeFailingAuthConnector(new InsufficientConfidenceLevel), appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
        }
      }
    }

    "the user used an unaccepted auth provider" - {
      "must redirect the user to the unauthorised page" in {
        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig = application.injector.instanceOf[FrontendAppConfig]

          val authAction = new AuthenticatedIdentifierAction(new FakeFailingAuthConnector(new UnsupportedAuthProvider), appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
        }
      }
    }

    "the user has an unsupported affinity group" - {
      "must redirect the user to the unauthorised page" in {
        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig = application.injector.instanceOf[FrontendAppConfig]

          val authAction = new AuthenticatedIdentifierAction(new FakeFailingAuthConnector(new UnsupportedAffinityGroup), appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(routes.UnauthorisedController.onPageLoad().url)
        }
      }
    }

    "the user has an unsupported credential role" - {
      "must redirect the user to the unauthorised page" in {
        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig = application.injector.instanceOf[FrontendAppConfig]

          val authAction = new AuthenticatedIdentifierAction(new FakeFailingAuthConnector(new UnsupportedCredentialRole), appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(routes.UnauthorisedController.onPageLoad().url)
        }
      }
    }

    "when the user is authenticated with supported affinity and enrolments" - {
      "must allow the request to proceed for Organisation users with HMRC-EU-REF-ORG enrolment" in {
        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig = application.injector.instanceOf[FrontendAppConfig]
          val enrolments = Enrolments(Set(Enrolment("HMRC-EU-REF-ORG", Seq(EnrolmentIdentifier(key = "VATRegNo", value = "123456789")), "Activated")))

          val authConnector = new FakeSuccessfulAuthConnector(
            affinityGroup = Some(AffinityGroup.Organisation),
            credentials   = Some(Credentials("credId", "provider")),
            enrolments    = enrolments
          )

          val authAction = new AuthenticatedIdentifierAction(authConnector, appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe OK
        }
      }

      "must allow the request to proceed for Individual users with HMRC-EU-REF-ORG enrolment" in {
        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig = application.injector.instanceOf[FrontendAppConfig]
          val enrolments = Enrolments(Set(Enrolment("HMRC-EU-REF-ORG", Seq(EnrolmentIdentifier(key = "VATRegNo", value = "123456789")), "Activated")))

          val authConnector = new FakeSuccessfulAuthConnector(
            affinityGroup = Some(AffinityGroup.Individual),
            credentials   = Some(Credentials("credId", "provider")),
            enrolments    = enrolments
          )

          val authAction = new AuthenticatedIdentifierAction(authConnector, appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe OK
        }
      }

      "must allow the request to proceed for Agent users with HMCE-VAT-AGNT enrolment" in {
        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig = application.injector.instanceOf[FrontendAppConfig]
          val enrolments = Enrolments(Set(Enrolment("HMCE-VAT-AGNT", Seq(EnrolmentIdentifier(key = "AgentRefNo", value = "123456789")), "Activated")))

          val authConnector = new FakeSuccessfulAuthConnector(
            affinityGroup = Some(AffinityGroup.Agent),
            credentials   = Some(Credentials("credId", "provider")),
            enrolments    = enrolments
          )

          val authAction = new AuthenticatedIdentifierAction(authConnector, appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe OK
        }
      }

      "must allow the request to proceed for Agent users with HMRC-NOVRN-AGNT enrolment" in {
        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig = application.injector.instanceOf[FrontendAppConfig]
          val enrolments =
            Enrolments(Set(Enrolment("HMRC-NOVRN-AGNT", Seq(EnrolmentIdentifier(key = "VATAgentRefNo", value = "123456789")), "Activated")))

          val authConnector = new FakeSuccessfulAuthConnector(
            affinityGroup = Some(AffinityGroup.Agent),
            credentials   = Some(Credentials("credId", "provider")),
            enrolments    = enrolments
          )

          val authAction = new AuthenticatedIdentifierAction(authConnector, appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe OK
        }
      }
    }

    "when the user is authenticated but missing credentials" - {
      "must throw UnauthorizedException" in {
        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig = application.injector.instanceOf[FrontendAppConfig]
          val enrolments = Enrolments(Set(Enrolment("HMRC-EU-REF-ORG", Seq(), "Activated")))

          val authConnector = new FakeSuccessfulAuthConnector(
            affinityGroup = Some(AffinityGroup.Organisation),
            credentials   = None,
            enrolments    = enrolments
          )

          val authAction = new AuthenticatedIdentifierAction(authConnector, appConfig, bodyParsers)
          val controller = new Harness(authAction)

          assertThrows[UnauthorizedException] {
            await(controller.onPageLoad()(FakeRequest()))
          }
        }
      }
    }

    "when the user is authenticated but has unsupported enrolments" - {
      "must redirect to the unauthorised page" in {
        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig = application.injector.instanceOf[FrontendAppConfig]
          val unsupportedEnrolments =
            Enrolments(
              Set(Enrolment(key = "SOME-OTHER-ENROLMENT", identifiers = Seq.empty, state = "Activated"))
            )

          val authConnector = new FakeSuccessfulAuthConnector(
            affinityGroup = Some(AffinityGroup.Organisation),
            credentials   = Some(Credentials("credId", "provider")),
            enrolments    = unsupportedEnrolments
          )

          val authAction = new AuthenticatedIdentifierAction(authConnector, appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
        }
      }
    }

    "when the user is authenticated but missing affinity group" - {
      "must redirect to the unauthorised page" in {
        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig = application.injector.instanceOf[FrontendAppConfig]
          val enrolments = Enrolments(Set.empty)

          val authConnector = new FakeSuccessfulAuthConnector(
            affinityGroup = None,
            credentials   = Some(Credentials("credId", "provider")),
            enrolments    = enrolments
          )

          val authAction = new AuthenticatedIdentifierAction(authConnector, appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          assertThrows[UnauthorizedException] {
            await(controller.onPageLoad()(FakeRequest()))
          }
        }
      }
    }

  }
}

class FakeFailingAuthConnector @Inject() (exceptionToReturn: Throwable) extends AuthConnector {
  val serviceUrl: String = ""

  override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
    Future.failed(exceptionToReturn)
}

class FakeSuccessfulAuthConnector(
  affinityGroup: Option[AffinityGroup],
  credentials: Option[Credentials],
  enrolments: Enrolments
) extends AuthConnector {
  val serviceUrl: String = ""

  override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] = {
    val result = new ~(new ~(affinityGroup, credentials), enrolments)
    Future.successful(result.asInstanceOf[A])
  }
}
