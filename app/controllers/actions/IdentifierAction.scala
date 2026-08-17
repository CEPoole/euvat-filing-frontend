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

import com.google.inject.Inject
import config.FrontendAppConfig
import controllers.routes
import models.requests.IdentifierRequest
import play.api.mvc.*
import play.api.mvc.Results.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

trait IdentifierAction extends ActionBuilder[IdentifierRequest, AnyContent] with ActionFunction[Request, IdentifierRequest]

final case class EnrolmentIdentifier(
  key: String,
  value: String
)
class AuthenticatedIdentifierAction @Inject() (
  override val authConnector: AuthConnector,
  config: FrontendAppConfig,
  val parser: BodyParsers.Default
)(implicit val executionContext: ExecutionContext)
    extends IdentifierAction
    with AuthorisedFunctions {

  private val requiredIdentifiers: Map[String, String] = Map(
    "HMRC-EU-REF-ORG" -> "VATRegNo",
    "HMCE-VAT-AGNT"   -> "AgentRefNo",
    "HMRC-NOVRN-AGNT" -> "VATAgentRefNo"
  )

  // allowed enrolments per affinity group
  private def allowedEnrolments(affinityGroup: AffinityGroup): Set[String] = affinityGroup match {
    case AffinityGroup.Organisation | AffinityGroup.Individual => Set("HMRC-EU-REF-ORG")
    case AffinityGroup.Agent                                   => Set("HMCE-VAT-AGNT", "HMRC-NOVRN-AGNT")
    case _                                                     => Set.empty
  }

  private def supportedEnrolmentIdentifier(
    affinityGroup: AffinityGroup,
    enrolments: Enrolments
  ): Option[EnrolmentIdentifier] = {
    val allowedKeys = allowedEnrolments(affinityGroup)
    // find matching enrolment + identifier
    enrolments.enrolments
      .find(enrol =>
        enrol.isActivated &&
          allowedKeys.contains(enrol.key) &&
          requiredIdentifiers.contains(enrol.key)
      )
      .flatMap { enrol =>
        requiredIdentifiers.get(enrol.key).flatMap { identifierKey =>
          enrol.identifiers
            .find(id => id.key == identifierKey && id.value.trim.nonEmpty)
            .map(id => EnrolmentIdentifier(identifierKey, id.value))
        }
      }
  }

  override def invokeBlock[A](request: Request[A], block: IdentifierRequest[A] => Future[Result]): Future[Result] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    authorised().retrieve(Retrievals.affinityGroup and Retrievals.credentials and Retrievals.allEnrolments) {
      case Some(affinityGroup) ~ Some(credentials) ~ enrolments =>
        supportedEnrolmentIdentifier(affinityGroup, enrolments) match {
          case Some(identifier) =>
            block(
              IdentifierRequest(request         = request,
                                userId          = credentials.providerId,
                                identifierKey   = Some(identifier.key),
                                identifierValue = Some(identifier.value)
                               )
            )
          case None => Future.successful(Redirect(routes.UnauthorisedController.onPageLoad()))
        }
      case _ => Future.failed(new UnauthorizedException("Unable to retrieve affinity, enrolments or credentials"))
    } recover {
      case _: NoActiveSession        => Redirect(config.loginUrl, Map("continue" -> Seq(config.loginContinueUrl)))
      case _: AuthorisationException => Redirect(routes.UnauthorisedController.onPageLoad())
    }
  }
}

class SessionIdentifierAction @Inject() (
  val parser: BodyParsers.Default
)(implicit val executionContext: ExecutionContext)
    extends IdentifierAction {

  override def invokeBlock[A](request: Request[A], block: IdentifierRequest[A] => Future[Result]): Future[Result] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    hc.sessionId match {
      case Some(session) => block(IdentifierRequest(request, session.value))
      case None          => Future.successful(Redirect(routes.JourneyRecoveryController.onPageLoad()))
    }
  }
}
