package com.github.klefstad_teaching.cs122b.billing.repo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class BillingRepo
{
    private final NamedParameterJdbcTemplate template;
    @Autowired
    public BillingRepo(NamedParameterJdbcTemplate template)
    {
        this.template = template;
    }
    public NamedParameterJdbcTemplate getTemplate() {
        return template;
    }
}
