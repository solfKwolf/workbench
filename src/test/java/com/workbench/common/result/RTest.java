package com.workbench.common.result;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RTest {

    @Test
    void ok_carriesData() {
        R<String> r = R.ok("hello");
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getMessage()).isEqualTo("success");
        assertThat(r.getData()).isEqualTo("hello");
    }

    @Test
    void fail_carriesCodeAndMessage() {
        R<Void> r = R.fail(401, "未登录");
        assertThat(r.getCode()).isEqualTo(401);
        assertThat(r.getMessage()).isEqualTo("未登录");
        assertThat(r.getData()).isNull();
    }
}
