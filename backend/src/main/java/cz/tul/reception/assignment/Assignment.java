package cz.tul.reception.assignment;

import java.time.OffsetDateTime;

record Assignment(long id,
                  long shiftId,
                  long employeeId,
                  OffsetDateTime startsAt,
                  OffsetDateTime endsAt) {}
