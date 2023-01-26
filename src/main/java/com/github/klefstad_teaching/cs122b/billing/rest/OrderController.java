package com.github.klefstad_teaching.cs122b.billing.rest;

import com.github.klefstad_teaching.cs122b.billing.model.Item;
import com.github.klefstad_teaching.cs122b.billing.model.Sale;
import com.github.klefstad_teaching.cs122b.billing.model.request.OCompleteReq;
import com.github.klefstad_teaching.cs122b.billing.model.response.CInsertResp;
import com.github.klefstad_teaching.cs122b.billing.model.response.CRetrieveResp;
import com.github.klefstad_teaching.cs122b.billing.model.response.orderPaymentResp;
import com.github.klefstad_teaching.cs122b.billing.model.response.saleResp;
import com.github.klefstad_teaching.cs122b.billing.repo.BillingRepo;
import com.github.klefstad_teaching.cs122b.billing.util.Validate;
import com.github.klefstad_teaching.cs122b.core.result.BillingResults;
import com.github.klefstad_teaching.cs122b.core.security.JWTManager;
import com.nimbusds.jwt.SignedJWT;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.ParseException;
import java.time.Instant;
import java.util.List;

@RestController
public class OrderController
{
    private final BillingRepo repo;
    private final Validate    validate;

    @Autowired
    public OrderController(BillingRepo repo,Validate validate)
    {
        this.repo = repo;
        this.validate = validate;
    }

    @GetMapping("order/payment")
    public ResponseEntity<orderPaymentResp> orderPayment(@AuthenticationPrincipal SignedJWT user){
        orderPaymentResp resp = new orderPaymentResp();
        boolean showAll = false;
        Long user_id;
        boolean premium = false;
        try {
            user_id = user.getJWTClaimsSet().getLongClaim(JWTManager.CLAIM_ID);
            List<String> role = user.getJWTClaimsSet().getStringListClaim((JWTManager.CLAIM_ROLES));
            if(!role.isEmpty()){
                showAll = (role.get(0).equalsIgnoreCase("Admin") ||
                        role.get(0).equalsIgnoreCase("Employee"));

                premium = role.get(0).equalsIgnoreCase("Premium");
            }
        } catch (ParseException | ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(resp);
        }

        String select  = "SELECT cart.movie_id, quantity, unit_price, title " +
                "FROM billing.cart " +
                "JOIN movies.movie ON movie_id = movie.id " +
                "JOIN billing.movie_price ON cart.movie_id = movie_price.movie_id " +
                "WHERE user_id = :user_id;";

        MapSqlParameterSource select_source = new MapSqlParameterSource()
                .addValue("user_id", user_id, Types.INTEGER);

        List<Item> movies = repo.getTemplate().query(
                select, select_source,
                (rs, rowNum) -> new Item()
                        .setMovieId(rs.getLong("movie_id"))
                        .setQuantity(rs.getInt("quantity"))
                        .setMovieTitle(rs.getString("title"))
                        .setUnitPrice(rs.getBigDecimal("unit_price").setScale(2, RoundingMode.DOWN))
        );

        if (movies.isEmpty()){
            resp.setResult(BillingResults.CART_EMPTY);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(resp);

        }
        else {
            String description = "";
            String userId = Long.toString(user_id);

            BigDecimal total = BigDecimal.valueOf(0.00).setScale(2, RoundingMode.DOWN);
            if (!premium) {
                for (Item movie : movies) {
                    total = total.add(movie.getUnitPrice().multiply(BigDecimal.valueOf(movie.getQuantity())));
                    description += movie.getMovieTitle() + ", ";
                }
            } else {
                for (Item movie : movies) {
                    String pre = "SELECT premium_discount " +
                            "FROM billing.movie_price " +
                            "WHERE movie_id = :movie_id;";
                    MapSqlParameterSource pres = new MapSqlParameterSource()
                            .addValue("movie_id", movie.getMovieId(), Types.INTEGER);
                    Integer prem = repo.getTemplate().queryForObject(
                            pre, pres,
                            (rs, rowNum) -> rs.getInt("premium_discount"));

                    BigDecimal newUnitPrice = movie.getUnitPrice().multiply(BigDecimal.valueOf((1 - (prem / 100.0)))).setScale(2, RoundingMode.DOWN);
                    movie.setUnitPrice(newUnitPrice);
                    total = total.add(newUnitPrice.multiply(BigDecimal.valueOf(movie.getQuantity())));
                    description += movie.getMovieTitle() + ", ";
                }
            }

            Long amountInTotalCents = total.longValue();
            description = description.substring(0, description.length()-2);

            PaymentIntentCreateParams paymentIntentCreateParams =
                    PaymentIntentCreateParams
                            .builder()
                            .setCurrency("USD") // This will always be the same for our project
                            .setDescription(description)
                            .setAmount(amountInTotalCents)
                            // We use MetaData to keep track of the user that should pay for the order
                            .putMetadata("userId", userId)
                            .setAutomaticPaymentMethods(
                                    // This will tell stripe to generate the payment methods automatically
                                    // This will always be the same for our project
                                    PaymentIntentCreateParams.AutomaticPaymentMethods
                                            .builder()
                                            .setEnabled(true)
                                            .build()
                            )
                            .build();

            try {
                PaymentIntent paymentIntent = PaymentIntent.create(paymentIntentCreateParams);
                resp.setResult(BillingResults.ORDER_PAYMENT_INTENT_CREATED);
                resp.setPaymentIntentId(paymentIntent.getId());
                resp.setClientSecret(paymentIntent.getClientSecret());
                return ResponseEntity.status(HttpStatus.OK)
                        .body(resp);
            } catch (StripeException e) {
                resp.setResult(BillingResults.STRIPE_ERROR);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(resp);
            }
        }

    }


    @PostMapping("/order/complete")
    public ResponseEntity<CInsertResp> orderComplete(@AuthenticationPrincipal SignedJWT user, @RequestBody OCompleteReq req) throws StripeException {
        CInsertResp resp = new CInsertResp();
        boolean showAll = false;
        Long user_id;
        boolean premium = false;
        try {
            user_id = user.getJWTClaimsSet().getLongClaim(JWTManager.CLAIM_ID);
            List<String> role = user.getJWTClaimsSet().getStringListClaim((JWTManager.CLAIM_ROLES));
            if(!role.isEmpty()){
                showAll = (role.get(0).equalsIgnoreCase("Admin") ||
                        role.get(0).equalsIgnoreCase("Employee"));

                premium = role.get(0).equalsIgnoreCase("Premium");
            }
        } catch (ParseException | ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(resp);
        }

        PaymentIntent retrievedPaymentIntent = PaymentIntent.retrieve(req.getPaymentIntentId());


        if (!retrievedPaymentIntent.getMetadata().get("userId").equals(user_id.toString())){
            resp.setResult(BillingResults.ORDER_CANNOT_COMPLETE_WRONG_USER);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(resp);
        }
        String status = retrievedPaymentIntent.getStatus();

        if (status.equals("succeeded")){
            String sale= "INSERT INTO billing.sale(user_id, total, order_date) " +
                    "VALUES (:user_id, :total, :order_date) ";
            Timestamp now = Timestamp.from(Instant.now());
            MapSqlParameterSource sale_source = new MapSqlParameterSource()
                    .addValue("user_id", user_id, Types.INTEGER)
                    .addValue("total", retrievedPaymentIntent.getAmount()/100.0, Types.DECIMAL)
                    .addValue("order_date", now, Types.TIMESTAMP);
            repo.getTemplate().update(sale, sale_source);

            String temp = "SELECT sale.id " +
                    "FROM billing.sale " +
                    "ORDER BY id;";
            List<Integer> sale_ids = repo.getTemplate().query(temp, (rs, rowNum) -> rs.getInt("sale.id"));
            Integer sale_id = sale_ids.get(sale_ids.size()-1);


            String sql_cart = "SELECT movie_id, quantity " +
                    "FROM billing.cart " +
                    "WHERE user_id = :user_id;";
            MapSqlParameterSource cart_source = new MapSqlParameterSource()
                    .addValue("user_id", user_id, Types.INTEGER);
            List<Item> cart = repo.getTemplate().query(sql_cart, cart_source,
                    (rs, rowNum) -> new Item()
                            .setMovieId(rs.getLong("movie_id"))
                            .setQuantity(rs.getInt("quantity")));


            for (Item item:cart) {
                String sale_item = "INSERT INTO billing.sale_item(sale_id, movie_id, quantity) " +
                        "VALUES (:sale_id, :movie_id, :quantity) ";
                MapSqlParameterSource saleItem_source = new MapSqlParameterSource()
                        .addValue("sale_id", sale_id, Types.INTEGER)
                        .addValue("movie_id", item.getMovieId(), Types.INTEGER)
                        .addValue("quantity", item.getQuantity(), Types.INTEGER);
                repo.getTemplate().update(sale_item, saleItem_source);
            }

            String delete = "DELETE FROM billing.cart " +
                    "WHERE user_id = :user_id;";

            MapSqlParameterSource delete_source = new MapSqlParameterSource()
                    .addValue("user_id", user_id, Types.INTEGER);
            repo.getTemplate().update(delete, delete_source);

            resp.setResult(BillingResults.ORDER_COMPLETED);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(resp);
        }
        resp.setResult(BillingResults.ORDER_CANNOT_COMPLETE_NOT_SUCCEEDED);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(resp);


    }


    @GetMapping("order/list")
    public ResponseEntity<saleResp> orderList(@AuthenticationPrincipal SignedJWT user){
        saleResp resp = new saleResp();
        boolean showAll = false;
        Long user_id;
        boolean premium = false;
        try {
            user_id = user.getJWTClaimsSet().getLongClaim(JWTManager.CLAIM_ID);
            List<String> role = user.getJWTClaimsSet().getStringListClaim((JWTManager.CLAIM_ROLES));
            if(!role.isEmpty()){
                showAll = (role.get(0).equalsIgnoreCase("Admin") ||
                        role.get(0).equalsIgnoreCase("Employee"));

                premium = role.get(0).equalsIgnoreCase("Premium");
            }
        } catch (ParseException | ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(resp);
        }
        String sql_sale = "SELECT id, total, order_date " +
                "FROM billing.sale " +
                "WHERE user_id = :user_id " +
                "ORDER BY order_date DESC " +
                "LIMIT 5;";
        MapSqlParameterSource sale_source = new MapSqlParameterSource()
                .addValue("user_id", user_id, Types.INTEGER);
        List<Sale> sales = repo.getTemplate().query(sql_sale, sale_source,
                (rs, rowNum) -> new Sale()
                        .setSaleId(rs.getLong("id"))
                        .setTotal(rs.getBigDecimal("total"))
                        .setOrderDate(rs.getTimestamp("order_date").toInstant()));


        if (sales.isEmpty()){
            resp.setResult(BillingResults.ORDER_LIST_NO_SALES_FOUND);
        }
        else{
            resp.setSales(sales);
            resp.setResult(BillingResults.ORDER_LIST_FOUND_SALES);
        }
        return ResponseEntity.status(HttpStatus.OK)
                .body(resp);


    }


    @GetMapping("/order/detail/{saleId}")
    public ResponseEntity<CRetrieveResp> orderDetail(@AuthenticationPrincipal SignedJWT user, @PathVariable Long saleId){
        CRetrieveResp resp = new CRetrieveResp();
        boolean showAll = false;
        Long user_id;
        boolean premium = false;
        try {
            user_id = user.getJWTClaimsSet().getLongClaim(JWTManager.CLAIM_ID);
            List<String> role = user.getJWTClaimsSet().getStringListClaim((JWTManager.CLAIM_ROLES));
            if(!role.isEmpty()){
                showAll = (role.get(0).equalsIgnoreCase("Admin") ||
                        role.get(0).equalsIgnoreCase("Employee"));

                premium = role.get(0).equalsIgnoreCase("Premium");
            }
        } catch (ParseException | ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(resp);
        }

        String sql = "SELECT total " +
                "FROM billing.sale " +
                "WHERE id = :sale_id AND user_id = :user_id;";
        MapSqlParameterSource source = new MapSqlParameterSource()
                .addValue("user_id", user_id)
                .addValue("sale_id", saleId);
        List<BigDecimal> totals = repo.getTemplate().query(sql, source, (rs, rowNum)-> rs.getBigDecimal("total"));
        if (totals.isEmpty()){
            resp.setResult(BillingResults.ORDER_DETAIL_NOT_FOUND);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(resp);
        }


        String select  = "SELECT sale_item.movie_id , quantity, unit_price, title, backdrop_path, poster_path " +
                "FROM billing.sale " +
                "JOIN billing.sale_item ON sale.id = sale_item.sale_id " +
                "JOIN movies.movie ON sale_item.movie_id = movie.id " +
                "JOIN billing.movie_price ON sale_item.movie_id = movie_price.movie_id " +
                "WHERE user_id = :user_id AND sale.id=:sale_id;";

        MapSqlParameterSource select_source = new MapSqlParameterSource()
                .addValue("user_id", user_id, Types.INTEGER)
                .addValue("sale_id", saleId, Types.INTEGER);

        List<Item> movies = repo.getTemplate().query(
                select, select_source,
                (rs, rowNum) -> new Item()
                        .setMovieId(rs.getLong("movie_id"))
                        .setQuantity(rs.getInt("quantity"))
                        .setMovieTitle(rs.getString("title"))
                        .setUnitPrice(rs.getBigDecimal("unit_price").setScale(2, RoundingMode.DOWN))
                        .setBackdropPath(rs.getString("backdrop_path"))
                        .setPosterPath(rs.getString("poster_path"))
        );

        BigDecimal total = BigDecimal.valueOf(0.00).setScale(2, RoundingMode.DOWN);
        if (!premium){
            for (Item movie : movies) {
                total = total.add(movie.getUnitPrice().multiply(BigDecimal.valueOf(movie.getQuantity())));
            }
            resp.setTotal(total);
            resp.setItems(movies);
            resp.setResult(BillingResults.ORDER_DETAIL_FOUND);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(resp);
        }
        else {
            for(Item movie: movies) {
                String pre = "SELECT premium_discount " +
                        "FROM billing.movie_price " +
                        "WHERE movie_id = :movie_id;";
                MapSqlParameterSource pres = new MapSqlParameterSource()
                        .addValue("movie_id", movie.getMovieId(), Types.INTEGER);
                Integer prem = repo.getTemplate().queryForObject(
                        pre, pres,
                        (rs, rowNum) -> rs.getInt("premium_discount"));

                BigDecimal newUnitPrice = movie.getUnitPrice().multiply(BigDecimal.valueOf((1-(prem/100.0)))).setScale(2, RoundingMode.DOWN);
                movie.setUnitPrice(newUnitPrice);
                total = total.add(newUnitPrice.multiply(BigDecimal.valueOf(movie.getQuantity())));
            }
            resp.setItems(movies);
            resp.setTotal(total);
            resp.setResult(BillingResults.ORDER_DETAIL_FOUND);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(resp);
        }





    }





}
