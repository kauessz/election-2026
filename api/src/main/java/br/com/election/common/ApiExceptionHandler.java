package br.com.election.common;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.List;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    ProblemDetail missing(NotFoundException e) { return problem(HttpStatus.NOT_FOUND, e.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException e) {
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Revise os campos informados.");
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage()).toList();
        p.setProperty("errors", errors); return p;
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) { return problem(HttpStatus.BAD_REQUEST, e.getMessage()); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail unreadable(Exception e) { return problem(HttpStatus.BAD_REQUEST, "Request inválido. Confira os tipos, datas e valores informados."); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException e) { return problem(HttpStatus.CONFLICT, "Os dados violam uma restrição do banco: confira duplicidades e referências."); }
    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail route(NoResourceFoundException e) { return problem(HttpStatus.NOT_FOUND, "Recurso não encontrado."); }
    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(switch(status) { case BAD_REQUEST -> "Dados inválidos"; case NOT_FOUND -> "Recurso não encontrado"; default -> "Conflito de dados"; });
        return p;
    }
}
