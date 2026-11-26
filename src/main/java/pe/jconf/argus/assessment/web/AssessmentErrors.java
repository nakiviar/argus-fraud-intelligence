package pe.jconf.argus.assessment.web;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class AssessmentErrors extends ResponseEntityExceptionHandler {
}
