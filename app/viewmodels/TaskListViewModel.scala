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

package viewmodels

import com.google.inject.Inject
import models.UserAnswers
import pages.ClaimDetailsCompletedPage
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.tag.Tag
import uk.gov.hmrc.govukfrontend.views.viewmodels.tasklist.{TaskList, TaskListItem, TaskListItemStatus, TaskListItemTitle}

class TaskListViewModel @Inject() () {

  def showDeleteLink(answers: UserAnswers): Boolean =
    answers.get(ClaimDetailsCompletedPage).contains(true)

  def buildTaskList(answers: UserAnswers)(implicit messages: Messages): TaskList = {
    val claimDetailsDone = answers.get(ClaimDetailsCompletedPage).contains(true)

    def notStartedStatus = TaskListItemStatus(tag = Some(Tag(content = Text(messages("taskListDashboard.status1")))))
    def cannotStartStatus = TaskListItemStatus(tag = Some(Tag(content = Text(messages("taskListDashboard.status2")), classes = "govuk-tag--grey")))

    val claimDetailsItem = TaskListItem(
      title = TaskListItemTitle(content =
        Text(
          if (claimDetailsDone) messages("taskListDashboard.listItem1.completed")
          else messages("taskListDashboard.listItem1")
        )
      ),
      status =
        if (claimDetailsDone) TaskListItemStatus(content = Text(messages("taskListDashboard.status3")))
        else notStartedStatus,
      href = Some(
        if (claimDetailsDone) "/file-eu-vat/claim-details"
        else controllers.routes.RefundingCountryController.onPageLoad(models.NormalMode).url
      )
    )

    val addPurchaseItem = TaskListItem(
      title  = TaskListItemTitle(content = Text(messages("taskListDashboard.listItem2"))),
      status = if (claimDetailsDone) notStartedStatus else cannotStartStatus,
      href   = if (claimDetailsDone) Some(controllers.routes.BeforeYouStartPurchaseController.onPageLoad().url) else None
    )

    val addImportItem = TaskListItem(
      title  = TaskListItemTitle(content = Text(messages("taskListDashboard.listItem3"))),
      status = if (claimDetailsDone) notStartedStatus else cannotStartStatus,
      href   = if (claimDetailsDone) Some("#") else None
    )

    val addSupportingDocsItem = TaskListItem(
      title  = TaskListItemTitle(content = Text(messages("taskListDashboard.listItem4"))),
      status = if (claimDetailsDone) notStartedStatus else cannotStartStatus,
      href   = if (claimDetailsDone) Some("#") else None
    )

    val addBankDetailsItem = TaskListItem(
      title  = TaskListItemTitle(content = Text(messages("taskListDashboard.listItem5"))),
      status = if (claimDetailsDone) notStartedStatus else cannotStartStatus,
      href   = if (claimDetailsDone) Some("#") else None
    )

    val submitClaimItem = TaskListItem(
      title  = TaskListItemTitle(content = Text(messages("taskListDashboard.listItem6"))),
      status = cannotStartStatus
    )

    TaskList(
      items    = Seq(claimDetailsItem, addPurchaseItem, addImportItem, addSupportingDocsItem, addBankDetailsItem, submitClaimItem),
      idPrefix = "make-a-claim-eu-vat-refund"
    )
  }
}
