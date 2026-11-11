package dev.createserver.backend.miniapp;
import dev.createserver.backend.account.AccountFailure;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes={CompanionController.class,CompanionNoticeController.class})
public class CompanionErrors {
    @ExceptionHandler(AccountFailure.class) public ResponseEntity<java.util.Map<String,String>> failure(AccountFailure e){return ResponseEntity.status(e.status()).cacheControl(CacheControl.noStore()).body(java.util.Map.of("code",e.getMessage()));}
}
