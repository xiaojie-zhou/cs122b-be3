package com.github.klefstad_teaching.cs122b.billing;

import com.github.klefstad_teaching.cs122b.billing.config.BillingServiceConfig;
import com.github.klefstad_teaching.cs122b.core.spring.SecuredStackService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@SecuredStackService
@EnableConfigurationProperties({
    BillingServiceConfig.class
})
public class BillingService
{
    public static void main(String[] args)
    {
        SpringApplication.run(BillingService.class, args);
    }
}
