package com.github.klefstad_teaching.cs122b.billing.config;

import com.stripe.Stripe;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConstructorBinding
@ConfigurationProperties(prefix = "billing")
public class BillingServiceConfig
{
    public BillingServiceConfig(String stripeApiKey)
    {
        Stripe.apiKey = stripeApiKey;
    }
}
