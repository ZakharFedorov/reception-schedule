package cz.tul.reception.assignment;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

import cz.tul.reception.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@Import({SecurityConfig.class, AssignmentService.class})
class AssignmentApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @MockitoBean AssignmentRepository repository;
    @MockitoBean(name = "accounts") UserDetailsService accounts;

    private static final String LOGIN = "test@example.test";
    private static final String PASSWORD = "unit-test";
    private static final String URL = "/api/shifts/12/assignments";
    private final String basic = "Basic " + HttpHeaders.encodeBasicAuth(LOGIN, PASSWORD, null);

    @BeforeEach
    void setUp() {
        String hash = passwords.encode(PASSWORD);
        when(accounts.loadUserByUsername(LOGIN)).thenAnswer(call -> User.withUsername(LOGIN)
                .password(hash).roles("EMPLOYEE").build());
        when(repository.adminBlock(LOGIN)).thenReturn(Optional.of(2L));
        when(repository.shift(12)).thenReturn(Optional.of(new AssignmentRepository.Shift(
                2, time("14:00"), time("22:00"))));
        when(repository.employeeExists(7)).thenReturn(true);
    }

    private static OffsetDateTime time(String hour) {
        return OffsetDateTime.parse("2026-11-11T" + hour + ":00+01:00");
    }

    private ResultActions assign(String start, String end) throws Exception {
        var csrf = mvc.perform(get("/api/csrf").header(HttpHeaders.AUTHORIZATION, basic))
                .andExpect(status().isOk()).andReturn();
        var token = json.readTree(csrf.getResponse().getContentAsByteArray());
        return mvc.perform(post(URL)
                .session((MockHttpSession) csrf.getRequest().getSession(false))
                .header(HttpHeaders.AUTHORIZATION, basic)
                .header(token.get("headerName").asString(), token.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(new CreateAssignment(7L, time(start), time(end)))));
    }

    @Test
    void createsPartialAssignment() throws Exception {
        when(repository.insert(eq(12L), any())).thenReturn(25L);
        assign("19:00", "22:00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(25)).andExpect(jsonPath("$.employeeId").value(7))
                .andExpect(jsonPath("$.shiftId").value(12));
    }

    @ParameterizedTest
    @CsvSource({"13:59,19:00", "19:00,22:01", "19:00,19:00", "20:00,19:00"})
    void rejectsInvalidIntervals(String start, String end) throws Exception {
        assign(start, end).andExpect(status().isBadRequest());
        verify(repository, never()).insert(anyLong(), any());
    }

    @Test
    void rejectsAnotherBlocksAdmin() throws Exception {
        when(repository.adminBlock(LOGIN)).thenReturn(Optional.of(1L));
        assign("19:00", "22:00").andExpect(status().isForbidden());
        verify(repository, never()).insert(anyLong(), any());
    }

    @Test
    void rejectsOrdinaryEmployee() throws Exception {
        when(repository.adminBlock(LOGIN)).thenReturn(Optional.empty());
        assign("19:00", "22:00").andExpect(status().isForbidden());
        verify(repository, never()).shift(anyLong());
    }

    @Test
    void rejectsUnknownEmployee() throws Exception {
        when(repository.employeeExists(7)).thenReturn(false);
        assign("19:00", "22:00").andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnknownShift() throws Exception {
        when(repository.shift(12)).thenReturn(Optional.empty());
        assign("19:00", "22:00").andExpect(status().isNotFound());
    }

    @Test
    void returnsConflictForPostgresExclusionViolation() throws Exception {
        when(repository.insert(eq(12L), any())).thenThrow(new DataIntegrityViolationException(
                "conflict", new SQLException("exclusion constraint", "23P01")));
        assign("18:59", "22:00").andExpect(status().isConflict());
    }

    @Test
    void opensBackendHomeWithoutLoginPrompt() throws Exception {
        mvc.perform(get("/").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(get("/api/csrf")).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, basic))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }
}
