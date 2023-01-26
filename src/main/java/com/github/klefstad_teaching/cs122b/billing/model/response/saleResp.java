package com.github.klefstad_teaching.cs122b.billing.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.klefstad_teaching.cs122b.billing.model.Sale;
import com.github.klefstad_teaching.cs122b.core.result.Result;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class saleResp {
    private Result result;
    private List<Sale> sales;

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public List<Sale> getSales() {
        return sales;
    }

    public void setSales(List<Sale> sales) {
        this.sales = sales;
    }
}
