package com.workbench.common.exception;

import com.workbench.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：状态码与 code 用异常携带值 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getCode()).body(R.fail(e.getCode(), e.getMessage()));
    }

    /** @Valid 参数校验失败：取第一条字段错误 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("参数校验失败");
        return ResponseEntity.badRequest().body(R.fail(400, message));
    }

    /** 请求体 JSON 格式错误（如漏写字段类型不对） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> handleMessageNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(R.fail(400, "请求体缺失或格式错误"));
    }

    /** 兜底：堆栈只进日志，对外不泄露细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleOther(Exception e) {
        log.error("未预期异常", e);
        return ResponseEntity.internalServerError().body(R.fail(500, "系统繁忙，请稍后重试"));
    }
}
