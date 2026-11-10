package cz.tul.reception.assignment;

import java.sql.SQLException;
import java.time.OffsetDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class AssignmentService {

    private final AssignmentRepository repository;

    AssignmentService(AssignmentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Assignment create(String login, long shiftId, CreateAssignment request) {
        long adminBlock = repository.adminBlock(login)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Vyžaduje se správce bloku."));

        AssignmentRepository.Shift shift = repository.shift(shiftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Směna nebyla nalezena."));
        if (adminBlock != shift.blockId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tuto směnu spravuje jiný blok.");
        }

        OffsetDateTime start = request.startsAt();
        OffsetDateTime end = request.endsAt();
        if (request.employeeId() == null || request.employeeId() <= 0 || start == null || end == null
                || !start.isBefore(end) || start.getNano() % 1000 != 0 || end.getNano() % 1000 != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Neplatný zaměstnanec nebo časový interval.");
        }
        if (start.isBefore(shift.start()) || end.isAfter(shift.end())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Interval musí ležet uvnitř směny.");
        }

        if (!repository.employeeExists(request.employeeId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Zaměstnanec nebyl nalezen.");
        }

        try {
            long id = repository.insert(shiftId, request);
            return new Assignment(id, shiftId, request.employeeId(), start, end);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMostSpecificCause() instanceof SQLException sql && "23P01".equals(sql.getSQLState())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Směna nebo zaměstnanec je v tomto čase již obsazen.", ex);
            }
            throw ex;
        }
    }
}
