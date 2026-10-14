package dev.createserver.backend.api;
import dev.createserver.backend.account.AccountFailure;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=BroadcastController.class)
public class BroadcastErrors {
    public record Error(String code){}
    @ExceptionHandler(AccountFailure.class) ResponseEntity<Error> failure(AccountFailure e){return ResponseEntity.status(e.status()).cacheControl(CacheControl.noStore()).body(new Error(e.getMessage()));}
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<Error> conflict(){return ResponseEntity.status(409).body(new Error("POST_CONFLICT"));}
}
