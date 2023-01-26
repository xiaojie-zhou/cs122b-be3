package com.github.klefstad_teaching.cs122b.billing.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.klefstad_teaching.cs122b.billing.model.Item;
import com.github.klefstad_teaching.cs122b.core.result.Result;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CRetrieveResp {
    private Result result;
    private BigDecimal total;
    private List<Item> Items;

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public List<Item> getItems() {
        return Items;
    }

    public void setItems(List<Item> items) {
        Items = items;
    }
}
