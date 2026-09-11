package com.workbench.common.exception;

import com.workbench.common.result.R;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBusiness_returnsMatchingStatusAndBody() {
        ResponseEntity<R<Void>> resp = handler.handleBusiness(new BusinessException(400, "用户名已存在"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(400);
        assertThat(resp.getBody().getMessage()).isEqualTo("用户名已存在");
        assertThat(resp.getBody().getData()).isNull();
    }

    @Test
    void handleBusiness_401MapsTo401() {
        ResponseEntity<R<Void>> resp =
                handler.handleBusiness(new BusinessException(401, "用户名或密码错误"));
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void handleMessageNotReadable_returns400() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("bad", (org.springframework.http.HttpInputMessage) null);
        ResponseEntity<R<Void>> resp = handler.handleMessageNotReadable(ex);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody().getMessage()).isEqualTo("请求体缺失或格式错误");
    }

    @Test
    void handleOther_returns500WithGenericMessage() {
        ResponseEntity<R<Void>> resp = handler.handleOther(new RuntimeException("boom"));
        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        assertThat(resp.getBody().getMessage()).isEqualTo("系统繁忙，请稍后重试");
    }
}
