package vn.edu.cnpm.content.api;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.LinkedHashMap;
import java.util.Map;
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException ex,HttpServletRequest request) {
        return ResponseEntity.status(ex.status()).body(error(ex.status(),ex.code(),ex.getMessage(),ex.details(),request));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex,HttpServletRequest request) {
        Map<String,String> fields=new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e->fields.putIfAbsent(e.getField(),e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Dữ liệu không hợp lệ",fields,request));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex,HttpServletRequest request) {
        log.error("Unhandled Content Service failure; correlationId={}",request.getHeader("X-Correlation-ID"),ex);
        return ResponseEntity.internalServerError().body(error(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR","Đã xảy ra lỗi nội bộ",null,request));
    }
    private ApiError error(HttpStatus status,String code,String message,Object details,HttpServletRequest request) {
        String id=request.getHeader("X-Correlation-ID");
        return new ApiError("error",code,message,details,id==null?"":id);
    }
}
