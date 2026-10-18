package vn.edu.cnpm.narration.api;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException ex,HttpServletRequest req){
        String id=req.getHeader("X-Correlation-ID");
        return ResponseEntity.status(ex.status()).body(new ApiError("error",ex.code(),ex.getMessage(),ex.details(),id==null?"":id));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex,HttpServletRequest req){
        String id=req.getHeader("X-Correlation-ID");
        log.error("Unhandled exception correlationId={}", id, ex);
        return ResponseEntity.internalServerError().body(new ApiError("error","INTERNAL_ERROR","Đã xảy ra lỗi nội bộ",null,id==null?"":id));
    }
}
