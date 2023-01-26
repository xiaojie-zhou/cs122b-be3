package com.github.klefstad_teaching.cs122b.billing.rest;

import com.github.klefstad_teaching.cs122b.billing.model.Item;
import com.github.klefstad_teaching.cs122b.billing.model.request.CInsertReq;
import com.github.klefstad_teaching.cs122b.billing.model.response.CInsertResp;
import com.github.klefstad_teaching.cs122b.billing.model.response.CRetrieveResp;
import com.github.klefstad_teaching.cs122b.billing.repo.BillingRepo;
import com.github.klefstad_teaching.cs122b.billing.util.Validate;
import com.github.klefstad_teaching.cs122b.core.result.BillingResults;
import com.github.klefstad_teaching.cs122b.core.security.JWTManager;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.tags.form.SelectTag;

import javax.management.openmbean.KeyAlreadyExistsException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Types;
import java.text.ParseException;
import java.util.List;

@RestController
public class CartController
{
    private final BillingRepo repo;
    private final Validate    validate;

    @Autowired
    public CartController(BillingRepo repo, Validate validate)
    {
        this.repo = repo;
        this.validate = validate;
    }

    @PostMapping("cart/insert")
    public ResponseEntity<CInsertResp> cartInsert(@AuthenticationPrincipal SignedJWT user,
                                                     @RequestBody CInsertReq cart)
    {
        CInsertResp resp = new CInsertResp();
        if (cart.getQuantity()<1){
            resp.setResult(BillingResults.INVALID_QUANTITY);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(resp);
        }
        if (cart.getQuantity()>10){
            resp.setResult(BillingResults.MAX_QUANTITY);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(resp);
        }

        boolean showAll = false;
        Long user_id;
        try {
            user_id = user.getJWTClaimsSet().getLongClaim(JWTManager.CLAIM_ID);
            List<String> role = user.getJWTClaimsSet().getStringListClaim((JWTManager.CLAIM_ROLES));
            if(!role.isEmpty()){
                showAll = (role.get(0).equalsIgnoreCase("Admin") ||
                        role.get(0).equalsIgnoreCase("Employee"));
            }
        } catch (ParseException | ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(resp);
        }



        try {
            String sql = "INSERT INTO billing.cart(user_id, movie_id, quantity)" +
                    "VALUES (:user_id, :movie_id, :quantity)";

            MapSqlParameterSource source = new MapSqlParameterSource()
                    .addValue("user_id", user_id, Types.INTEGER)
                    .addValue("movie_id", cart.getMovieId(), Types.INTEGER)
                    .addValue("quantity", cart.getQuantity(), Types.INTEGER);

            repo.getTemplate().update(sql, source);

            resp.setResult(BillingResults.CART_ITEM_INSERTED);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(resp);
        }
        catch (DuplicateKeyException e){
            resp.setResult(BillingResults.CART_ITEM_EXISTS);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(resp);
        }


    }

    @PostMapping("cart/update")
    public ResponseEntity<CInsertResp> cartUpdate(@AuthenticationPrincipal SignedJWT user,
                                                  @RequestBody CInsertReq cart)
    {
        CInsertResp resp = new CInsertResp();
        boolean showAll = false;
        Long user_id;
        try {
            user_id = user.getJWTClaimsSet().getLongClaim(JWTManager.CLAIM_ID);
            List<String> role = user.getJWTClaimsSet().getStringListClaim((JWTManager.CLAIM_ROLES));
            if(!role.isEmpty()){
                showAll = (role.get(0).equalsIgnoreCase("Admin") ||
                        role.get(0).equalsIgnoreCase("Employee"));
            }
        } catch (ParseException | ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(resp);
        }

        if (cart.getQuantity()<1){
            resp.setResult(BillingResults.INVALID_QUANTITY);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(resp);
        }
        if (cart.getQuantity()>10){
            resp.setResult(BillingResults.MAX_QUANTITY);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(resp);
        }

        String select  = "SELECT movie_id " +
                "FROM billing.cart " +
                "WHERE user_id = :user_id AND movie_id = :movie_id;";

        MapSqlParameterSource select_source = new MapSqlParameterSource()
                .addValue("user_id", user_id, Types.INTEGER)
                .addValue("movie_id", cart.getMovieId(), Types.INTEGER);

        List<Integer> check = repo.getTemplate().query(
                select, select_source,
                (rs, rowNum) -> rs.getInt("movie_id"));

        if (check.isEmpty()){
            resp.setResult(BillingResults.CART_ITEM_DOES_NOT_EXIST);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(resp);
        }

        String sql = "UPDATE billing.cart " +
                "SET quantity = :quantity " +
                "WHERE user_id = :user_id AND movie_id = :movie_id;";

        MapSqlParameterSource source = new MapSqlParameterSource()
                .addValue("user_id", user_id, Types.INTEGER)
                .addValue("movie_id", cart.getMovieId(), Types.INTEGER)
                .addValue("quantity", cart.getQuantity(), Types.INTEGER);

        repo.getTemplate().update(sql, source);

        resp.setResult(BillingResults.CART_ITEM_UPDATED);
        return ResponseEntity.status(HttpStatus.OK)
                .body(resp);

    }

    @DeleteMapping("/cart/delete/{movieId}")
    public ResponseEntity<CInsertResp> cartDelete(@AuthenticationPrincipal SignedJWT user,
                                                  @PathVariable Long movieId)
    {

        CInsertResp resp = new CInsertResp();
        boolean showAll = false;
        Long user_id;
        try {
            user_id = user.getJWTClaimsSet().getLongClaim(JWTManager.CLAIM_ID);
            List<String> role = user.getJWTClaimsSet().getStringListClaim((JWTManager.CLAIM_ROLES));
            if(!role.isEmpty()){
                showAll = (role.get(0).equalsIgnoreCase("Admin") ||
                        role.get(0).equalsIgnoreCase("Employee"));
            }
        } catch (ParseException | ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(resp);
        }

        String select  = "SELECT movie_id " +
                "FROM billing.cart " +
                "WHERE user_id = :user_id AND movie_id = :movie_id;";

        MapSqlParameterSource select_source = new MapSqlParameterSource()
                .addValue("user_id", user_id, Types.INTEGER)
                .addValue("movie_id", movieId, Types.INTEGER);

        List<Long> check = repo.getTemplate().query(
                select, select_source,
                (rs, rowNum) -> rs.getLong("movie_id"));

        if (check.isEmpty()){
            resp.setResult(BillingResults.CART_ITEM_DOES_NOT_EXIST);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(resp);
        }

        String sql = "DELETE FROM billing.cart "+
                "WHERE user_id = :user_id AND movie_id = :movie_id;";

        MapSqlParameterSource source = new MapSqlParameterSource()
                .addValue("user_id", user_id, Types.INTEGER)
                .addValue("movie_id", movieId, Types.INTEGER);
        repo.getTemplate().update(sql, source);

        resp.setResult(BillingResults.CART_ITEM_DELETED);
        return ResponseEntity.status(HttpStatus.OK)
                .body(resp);

    }

    @GetMapping("/cart/retrieve")
    public ResponseEntity<CRetrieveResp> cartRetrieve(@AuthenticationPrincipal SignedJWT user){
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

        String select  = "SELECT cart.movie_id, quantity, unit_price, title, backdrop_path, poster_path " +
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
                        .setBackdropPath(rs.getString("backdrop_path"))
                        .setPosterPath(rs.getString("poster_path"))
        );

        if (movies.isEmpty()){
            resp.setResult(BillingResults.CART_EMPTY);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(resp);
        }
        resp.setResult(BillingResults.CART_RETRIEVED);


        BigDecimal total = BigDecimal.valueOf(0.00).setScale(2, RoundingMode.DOWN);
        if (!premium) {
            for (Item movie : movies) {
                total = total.add(movie.getUnitPrice().multiply(BigDecimal.valueOf(movie.getQuantity())));
            }
            resp.setTotal(total);
            resp.setItems(movies);
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
            return ResponseEntity.status(HttpStatus.OK)
                    .body(resp);
        }
    }

    @PostMapping("/cart/clear")
    public ResponseEntity<CInsertResp> cartClear(@AuthenticationPrincipal SignedJWT user){
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
        String select  = "SELECT movie_id " +
                "FROM billing.cart " +
                "WHERE user_id = :user_id;";

        MapSqlParameterSource select_source = new MapSqlParameterSource()
                .addValue("user_id", user_id, Types.INTEGER);

        List<Long> check = repo.getTemplate().query(
                select, select_source,
                (rs, rowNum) -> rs.getLong("movie_id"));

        if (check.isEmpty()){
            resp.setResult(BillingResults.CART_EMPTY);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(resp);
        }
        else {
            String delete = "DELETE FROM billing.cart " +
                    "WHERE user_id = :user_id;";

            MapSqlParameterSource delete_source = new MapSqlParameterSource()
                    .addValue("user_id", user_id, Types.INTEGER);
            repo.getTemplate().update(delete, delete_source);
            resp.setResult(BillingResults.CART_CLEARED);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(resp);
        }

    }
}
