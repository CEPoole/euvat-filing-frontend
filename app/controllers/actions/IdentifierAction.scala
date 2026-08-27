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

import config.FrontendAppConfig
import controllers.routes
import models.requests.IdentifierRequest
import play.api.mvc.*
import play.api.mvc.Results.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.AffinityGroup.{Agent, Organisation}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}
import javax.inject.Inject

trait IdentifierAction extends ActionBuilder[IdentifierRequest, AnyContent] with ActionFunction[Request, IdentifierRequest]

class AuthenticatedIdentifierAction @Inject() (
  override val authConnector: AuthConnector,
  config: FrontendAppConfig,
  val parser: BodyParsers.Default
)(implicit val executionContext: ExecutionContext)
    extends IdentifierAction
    with AuthorisedFunctions:

  private def checkForSupportedEnrolment(
    affinityGroup: AffinityGroup,
    enrolments: Enrolments
  ): Option[EnrolmentIdentifier] =
    enrolments.enrolments.collectFirst:
      case e @ Enrolment("HMRC-EU-REF-ORG", identifiers, _, _) if e.isActivated && affinityGroup != Agent =>
        e.identifiers.filter(_.key == "VATRegNo").head
      case e @ Enrolment("HMCE-VAT-AGNT", identifiers, _, _) if e.isActivated && affinityGroup == Agent =>
        e.identifiers.filter(_.key == "AgentRefNo").head
      case e @ Enrolment("HMRC-NOVRN-AGNT", identifiers, _, _) if e.isActivated && affinityGroup == Agent =>
        e.identifiers.filter(_.key == "VATAgentRefNo").head

  override def invokeBlock[A](request: Request[A], block: IdentifierRequest[A] => Future[Result]): Future[Result] =
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    authorised()
      .retrieve(Retrievals.affinityGroup and Retrievals.credentials and Retrievals.allEnrolments):
        case Some(affinityGroup) ~ Some(credentials) ~ enrolments =>
          checkForSupportedEnrolment(affinityGroup, enrolments) match
            case None => Future.successful(Redirect(routes.UnauthorisedController.onPageLoad()))
            case Some(supportedIdentifier) =>
              block(
                IdentifierRequest(request         = request,
                                  userId          = credentials.providerId,
                                  identifierKey   = supportedIdentifier.key,
                                  identifierValue = supportedIdentifier.value
                                 )
              )
        case _ =>
          Future.failed(new UnauthorizedException("Unable to retrieve affinity, enrolments or credentials"))
      .recover:
        case _: NoActiveSession        => Redirect(config.loginUrl, Map("continue" -> Seq(config.loginContinueUrl)))
        case _: AuthorisationException => Redirect(routes.UnauthorisedController.onPageLoad())
