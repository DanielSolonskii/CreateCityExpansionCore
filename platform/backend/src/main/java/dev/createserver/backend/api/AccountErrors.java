package dev.createserver.backend.api;

import dev.createserver.backend.account.AccountFailure;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = {AccountController.class, DeviceController.class, GameController.class})
public class AccountErrors {
    public record Error(String code) {}
    @ExceptionHandler(AccountFailure.class)
    ResponseEntity<Error> accountFailure(AccountFailure failure) {
        return ResponseEntity.status(failure.status()).cacheControl(CacheControl.noStore())
                .body(new Error(failure.getMessage()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Error> conflict() {
        return ResponseEntity.status(409).cacheControl(CacheControl.noStore()).body(new Error("IDENTITY_CONFLICT"));
    }
}
