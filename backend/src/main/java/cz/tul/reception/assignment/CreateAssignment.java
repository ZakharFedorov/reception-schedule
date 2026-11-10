package cz.tul.reception.assignment;

import java.time.OffsetDateTime;

record CreateAssignment(Long employeeId, OffsetDateTime startsAt, OffsetDateTime endsAt) {}
