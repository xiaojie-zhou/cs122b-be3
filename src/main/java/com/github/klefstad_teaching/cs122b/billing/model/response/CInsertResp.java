package com.github.klefstad_teaching.cs122b.billing.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.klefstad_teaching.cs122b.core.result.Result;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CInsertResp {
    private Result result;

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }
}
