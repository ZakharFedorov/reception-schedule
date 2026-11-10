package cz.tul.reception.security;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CsrfController {

    record Token(String headerName, String token) {}

    @GetMapping("/api/csrf")
    Token token(CsrfToken csrf) {
        return new Token(csrf.getHeaderName(), csrf.getToken());
    }
}
