package com.github.klefstad_teaching.cs122b.billing.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.klefstad_teaching.cs122b.core.result.Result;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class orderPaymentResp {
    private Result result;
    private String paymentIntentId;
    private String clientSecret;

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public void setPaymentIntentId(String paymentIntentId) {
        this.paymentIntentId = paymentIntentId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
}
