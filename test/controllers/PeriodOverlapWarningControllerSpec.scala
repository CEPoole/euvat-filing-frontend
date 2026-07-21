package controllers

import base.SpecBase
import models.NormalMode
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import views.html.PeriodOverlapWarningView

class PeriodOverlapWarningControllerSpec extends SpecBase {

  "PeriodOverlapWarning Controller" - {

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.PeriodOverlapWarningController.onPageLoad().url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[PeriodOverlapWarningView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(routes.RefundPeriodController.onPageLoad(NormalMode))(request, messages(application)).toString
      }
    }

    "must redirect to ContactDetailsController on submit" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.PeriodOverlapWarningController.onSubmit().url)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.ContactDetailsController.onPageLoad(NormalMode).url
      }
    }
  }
}
