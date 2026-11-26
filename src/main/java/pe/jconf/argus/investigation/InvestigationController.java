package pe.jconf.argus.investigation;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@Profile({"demo-local & !demo-azure", "demo-azure & !demo-local"})
@RequestMapping(path = InvestigationController.PATH, produces = APPLICATION_JSON_VALUE)
class InvestigationController {
    static final String PATH = "/api/v1/investigations";
    private final InvestigationOrchestrator orchestrator;

    InvestigationController(InvestigationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    ResponseEntity<InvestigationSnapshot> create(@RequestBody JsonNode request) {
        var value = request.path("assessmentId");
        if (!request.isObject() || request.size() != 1 || !value.isString()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supply a single assessmentId UUID.");
        }
        var assessmentId = parseUuid(value.stringValue(), "Assessment identifier must be a UUID.");
        try {
            var submission = orchestrator.start(assessmentId);
            var current = submission.snapshot();
            var status = submission.created() || !current.status().isTerminal() ? HttpStatus.ACCEPTED : HttpStatus.OK;
            return ResponseEntity.status(status).location(URI.create(PATH + "/" + current.investigationId()))
                    .body(current);
        } catch (InvestigationOrchestrator.Rejected exception) {
            throw switch (exception.reason()) {
                case UNKNOWN_ASSESSMENT -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assessment not found.");
                case ASSESSMENT_NOT_REVIEW -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Only REVIEW assessments can be investigated.");
                case SHUTTING_DOWN -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Investigation processing is shutting down.");
                case CAPACITY -> new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Investigation capacity is full. Retry later.");
            };
        }
    }

    @GetMapping("/{investigationId}")
    InvestigationSnapshot get(@PathVariable String investigationId) {
        var id = parseUuid(investigationId, "Investigation identifier must be a UUID.");
        return orchestrator.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investigation not found."));
    }

    private static UUID parseUuid(String value, String detail) {
        try {
            var parsed = UUID.fromString(value);
            if (parsed.toString().equalsIgnoreCase(value)) {
                return parsed;
            }
        } catch (IllegalArgumentException ignored) {
            // Return a consistent error without including the submitted value.
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}
