package pe.jconf.argus.assessment.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.jconf.argus.assessment.RiskAssessmentService;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(path = "/api/v1", produces = APPLICATION_JSON_VALUE)
public class RiskAssessmentController {
    private static final Pattern SCENARIO_ID = Pattern.compile("[a-z][a-z0-9-]{0,63}");
    private static final String ASSESSMENTS = "/assessments";

    private final RiskAssessmentService service;

    public RiskAssessmentController(RiskAssessmentService service) {
        this.service = service;
    }

    @GetMapping("/scenarios")
    public List<RiskAssessmentService.ScenarioSummary> scenarios() {
        return service.listScenarios();
    }

    @PostMapping(path = ASSESSMENTS, consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<AssessmentResponse> assess(@RequestBody JsonNode request) {
        var scenarioId = request.path("scenarioId");
        if (!request.isObject() || request.size() != 1 || !scenarioId.isString()
                || !SCENARIO_ID.matcher(scenarioId.stringValue()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supply a single valid scenarioId string.");
        }

        var assessment = service.assess(scenarioId.stringValue())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Synthetic scenario not found."));
        return ResponseEntity.created(URI.create("/api/v1" + ASSESSMENTS + "/" + assessment.assessmentId()))
                .body(AssessmentResponse.from(assessment));
    }

    @GetMapping(ASSESSMENTS + "/{assessmentId}")
    public AssessmentResponse get(@PathVariable String assessmentId) {
        UUID id;
        try {
            id = UUID.fromString(assessmentId);
            if (!id.toString().equalsIgnoreCase(assessmentId)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assessment identifier must be a UUID.");
        }
        return service.find(id).map(AssessmentResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assessment not found."));
    }
}
