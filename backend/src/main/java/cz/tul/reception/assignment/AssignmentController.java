package cz.tul.reception.assignment;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AssignmentController {

    private final AssignmentService service;

    AssignmentController(AssignmentService service) {
        this.service = service;
    }

    @PostMapping("/api/shifts/{shiftId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    Assignment create(@PathVariable long shiftId, @RequestBody CreateAssignment request, Principal user) {
        return service.create(user.getName(), shiftId, request);
    }
}
