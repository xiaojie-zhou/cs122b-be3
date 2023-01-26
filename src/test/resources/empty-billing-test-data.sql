DELETE
FROM billing.cart
WHERE user_id > 0;
DELETE
FROM billing.sale
WHERE id > 0;

ALTER TABLE billing.cart
    AUTO_INCREMENT = 1;
ALTER TABLE billing.sale
    AUTO_INCREMENT = 1;
