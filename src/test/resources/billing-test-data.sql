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

SET @admin = 1;
SET @employee = 2;
SET @premium = 3;

SET @employee_sale_one = 1;
SET @employee_sale_two = 2;
SET @premium_sale_one = 3;

INSERT INTO billing.cart (user_id, movie_id, quantity)
VALUES (@admin, 1843866, 2),
       (@admin, 4154756, 7),
       (@admin, 4154796, 8),
       (@employee, 1013743, 1),
       (@employee, 1117563, 2),
       (@employee, 113071, 3),
       (@employee, 1545660, 1),
       (@employee, 1683043, 2),
       (@employee, 183790, 1),
       (@employee, 1843866, 10),
       (@employee, 2101383, 2),
       (@employee, 2166834, 3),
       (@employee, 2313197, 1),
       (@employee, 2493486, 2),
       (@employee, 4154756, 5),
       (@employee, 4154796, 1),
       (@premium, 1843866, 3),
       (@premium, 2101383, 2),
       (@premium, 4154796, 8);

INSERT INTO billing.sale (id, user_id, total, order_date)
VALUES (@employee_sale_one, @employee, 234.45, TIMESTAMP '2022-01-01 12:00:00'),
       (@employee_sale_two, @employee, 299.30, TIMESTAMP '2022-01-02 12:00:00'),
       (@premium_sale_one, @premium, 345.65, TIMESTAMP '2022-01-03 12:00:00'),
       (4, @admin, 45.95, TIMESTAMP '2022-01-04 12:00:00'),
       (5, @admin, 152.95, TIMESTAMP '2022-01-05 12:00:00'),
       (6, @admin, 23.95, TIMESTAMP '2022-01-06 12:00:00'),
       (7, @admin, 9.95, TIMESTAMP '2022-01-07 12:00:00'),
       (8, @admin, 105.45, TIMESTAMP '2022-01-08 12:00:00'),
       (9, @admin, 59.95, TIMESTAMP '2022-01-09 12:00:00');

INSERT INTO billing.sale_item (sale_id, movie_id, quantity)
VALUES (@employee_sale_one, 1843866, 1),
       (@employee_sale_one, 2101383, 3),
       (@employee_sale_one, 4154796, 7),
       (@employee_sale_two, 183790, 2),
       (@employee_sale_two, 2101383, 4),
       (@employee_sale_two, 2313197, 8),
       (@premium_sale_one, 183790, 3),
       (@premium_sale_one, 2101383, 5),
       (@premium_sale_one, 2166834, 9);
