package dev.createserver.backend.api;
import dev.createserver.backend.account.AccountFailure;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
@RestControllerAdvice(assignableTypes=BridgeController.class)
public class BridgeErrors {
    public record Error(String code){}
    @ExceptionHandler(AccountFailure.class) ResponseEntity<Error> failure(AccountFailure e){return ResponseEntity.status(e.status()).cacheControl(CacheControl.noStore()).body(new Error(e.getMessage()));}
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<Error> conflict(){return ResponseEntity.status(409).cacheControl(CacheControl.noStore()).body(new Error("TELEMETRY_CONFLICT"));}
}
