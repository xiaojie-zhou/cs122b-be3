package com.github.klefstad_teaching.cs122b.billing;

import com.github.klefstad_teaching.cs122b.core.result.BillingResults;
import com.github.klefstad_teaching.cs122b.core.result.Result;
import com.github.klefstad_teaching.cs122b.core.security.JWTAuthenticationFilter;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.PaymentIntentCreateParams;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MockMvcBuilder;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Types;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Sql("/idm-test-data.sql")
@SqlMergeMode(SqlMergeMode.MergeMode.MERGE)
@AutoConfigureMockMvc
public class BillingServiceTest
{
    private static final String CART_INSERT_PATH    = "/cart/insert";
    private static final String CART_UPDATE_PATH    = "/cart/update";
    private static final String CART_DELETE_ID_PATH = "/cart/delete/{movieId}";
    private static final String CART_RETRIEVE_PATH  = "/cart/retrieve";
    private static final String CART_CLEAR_PATH     = "/cart/clear";

    private static final String ORDER_PAYMENT_PATH  = "/order/payment";
    private static final String ORDER_COMPLETE_PATH = "/order/complete";
    private static final String ORDER_LIST_PATH     = "/order/list";
    private static final String ORDER_DETAIL_PATH   = "/order/detail/{saleId}";

    private static final String EXPECTED_MODELS_FILE_NAME = "expected-models.json";
    private static final String USERS_FILE_NAME           = "users.json";

    private static final Long EMPLOYEE_SALE_ONE_ID = 1L;
    private static final Long EMPLOYEE_SALE_TWO_ID = 2L;
    private static final Long PREMIUM_SALE_ONE_ID  = 3L;

    private final MockMvc    mockMvc;
    private final JSONObject expectedModels;
    private final JSONObject users;

    private final String adminHeader;
    private final String employeeHeader;
    private final String premiumHeader;

    private final Long adminId;
    private final Long employeeId;
    private final Long premiumId;

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public BillingServiceTest(MockMvcBuilder mockMvc, NamedParameterJdbcTemplate template)
    {
        this.mockMvc = mockMvc.build();

        this.expectedModels = createModel(EXPECTED_MODELS_FILE_NAME);
        this.users = createModel(USERS_FILE_NAME);

        this.adminHeader = getToken("Admin@example.com");
        this.employeeHeader = getToken("Employee@example.com");
        this.premiumHeader = getToken("Premium@example.com");

        this.adminId = getId("Admin@example.com");
        this.employeeId = getId("Employee@example.com");
        this.premiumId = getId("Premium@example.com");

        this.template = template;
    }

    private String getToken(String email)
    {
        return JWTAuthenticationFilter.BEARER_PREFIX +
               ((JSONObject) this.users.get(email)).getAsString("token");
    }

    private Long getId(String email)
    {
        return ((JSONObject) this.users.get(email)).getAsNumber("id").longValue();
    }

    private JSONObject createModel(String fileName)
    {
        try {
            File file = ResourceUtils.getFile(
                ResourceUtils.CLASSPATH_URL_PREFIX + fileName
            );

            return (JSONObject) new JSONParser(JSONParser.MODE_STRICTEST)
                .parse(new FileReader(file));

        } catch (IOException | ParseException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private ResultMatcher[] isResult(Result result)
    {
        return new ResultMatcher[]{
            status().is(result.status().value()),
            jsonPath("result.code").value(result.code()),
            jsonPath("result.message").value(result.message())
        };
    }

    private <T> T getModel(String modelIdentifier, Class<T> clazz)
    {
        return clazz.cast(getModel(modelIdentifier));
    }

    private Object getModel(String modelIdentifier)
    {
        String[] identifiers = modelIdentifier.split("\\.");

        if (identifiers.length == 1) {
            return expectedModels.get(modelIdentifier);
        }

        Object model = expectedModels;

        for (String identifier : identifiers) {
            model = ((JSONObject) model).get(identifier);
        }

        return model;
    }

    private JSONArray getCart(Long userId)
    {
        JSONArray jsonArray = new JSONArray();

        jsonArray.addAll(template.query(
            "SELECT movie_id, quantity " +
            "FROM billing.cart " +
            "WHERE user_id = :userId " +
            "ORDER BY movie_id",
            new MapSqlParameterSource()
                .addValue("userId", userId, Types.INTEGER),
            (rs, num) -> {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("movieId", rs.getLong("movie_id"));
                jsonObject.put("quantity", rs.getInt("quantity"));
                return jsonObject;
            }));

        return jsonArray;
    }

    private Long getMostRecentSale(Long userId)
    {
        return template.queryForObject(
            "SELECT id " +
            "FROM billing.sale " +
            "WHERE user_id " +
            "ORDER BY order_date DESC " +
            "LIMIT 1;",
            new MapSqlParameterSource()
                .addValue("userId", userId, Types.INTEGER),
            Long.class
        );
    }

    private JSONObject getSaleItems(Long saleId)
    {
        JSONObject sale = new JSONObject();

        JSONArray saleItems = new JSONArray();

        List<JSONObject> items = template.query(
            "SELECT movie_id, quantity " +
            "FROM billing.sale_item si " +
            "WHERE si.sale_id = :saleId " +
            "ORDER BY movie_id",
            new MapSqlParameterSource()
                .addValue("saleId", saleId, Types.INTEGER),
            (rs, num) -> {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("movieId", rs.getLong("movie_id"));
                jsonObject.put("quantity", rs.getInt("quantity"));
                return jsonObject;
            });

        saleItems.addAll(items);

        BigDecimal total = template.queryForObject(
            "SELECT total " +
            "FROM billing.sale " +
            "WHERE id = :saleId;",
            new MapSqlParameterSource()
                .addValue("saleId", saleId, Types.INTEGER),
            BigDecimal.class
        );

        sale.put("total", total.setScale(2, RoundingMode.UNNECESSARY));

        sale.put("saleItems", saleItems);

        return sale;
    }

    @Test
    public void applicationLoads()
    {
    }

    // Cart insert tests

    @Test
    @Sql("/empty-billing-test-data.sql")
    public void cartInsertInvalidQuantityZero()
        throws Exception
    {
        JSONObject request = new JSONObject();
        request.put("movieId", 15324);
        request.put("quantity", 0);

        this.mockMvc.perform(post(CART_INSERT_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .content(request.toJSONString())
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.INVALID_QUANTITY));
    }

    @Test
    @Sql("/empty-billing-test-data.sql")
    public void cartInsertInvalidQuantityNegative()
        throws Exception
    {
        JSONObject request = new JSONObject();
        request.put("movieId", 15324);
        request.put("quantity", -1);

        this.mockMvc.perform(post(CART_INSERT_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .content(request.toJSONString())
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.INVALID_QUANTITY));
    }

    @Test
    @Sql("/empty-billing-test-data.sql")
    public void cartInsertInvalidQuantityMax()
        throws Exception
    {
        JSONObject request = new JSONObject();
        request.put("movieId", 15324);
        request.put("quantity", 11);

        this.mockMvc.perform(post(CART_INSERT_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .content(request.toJSONString())
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.MAX_QUANTITY));
    }

    @Test
    @Sql("/empty-billing-test-data.sql")
    public void cartInsertMovie()
        throws Exception
    {
        JSONObject request = new JSONObject();
        request.put("movieId", 15324);
        request.put("quantity", 2);

        JSONArray expected = new JSONArray();
        expected.add(request);

        this.mockMvc.perform(post(CART_INSERT_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .content(request.toJSONString())
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_ITEM_INSERTED));

        Assertions.assertEquals(expected.toJSONString(), getCart(adminId).toJSONString());
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void cartInsertMovieAlreadyInserted()
        throws Exception
    {
        JSONObject request = new JSONObject();
        request.put("movieId", 4154796);
        request.put("quantity", 1);

        this.mockMvc.perform(post(CART_INSERT_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .content(request.toJSONString())
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_ITEM_EXISTS));

        JSONArray expected = getModel("adminCart", JSONArray.class);

        Assertions.assertEquals(expected.toJSONString(), getCart(adminId).toJSONString());
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void cartInsertMovieExistingCart()
        throws Exception
    {
        JSONObject request = new JSONObject();
        request.put("movieId", 1013743);
        request.put("quantity", 1);

        this.mockMvc.perform(post(CART_INSERT_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .content(request.toJSONString())
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_ITEM_INSERTED));

        JSONArray expected = getModel("cartInsertMovieExistingCart", JSONArray.class);

        Assertions.assertEquals(expected.toJSONString(), getCart(adminId).toJSONString());
    }

    @Test
    @Sql("/empty-billing-test-data.sql")
    public void cartInsertMovieMultiple()
        throws Exception
    {
        JSONArray expected = new JSONArray();

        JSONObject firstRequest = new JSONObject();
        firstRequest.put("movieId", 15324);
        firstRequest.put("quantity", 2);

        expected.add(firstRequest);

        this.mockMvc.perform(post(CART_INSERT_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .content(firstRequest.toString())
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_ITEM_INSERTED));

        JSONObject secondRequest = new JSONObject();
        secondRequest.put("movieId", 4154796);
        secondRequest.put("quantity", 7);

        expected.add(secondRequest);

        this.mockMvc.perform(post(CART_INSERT_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .content(secondRequest.toString())
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_ITEM_INSERTED));

        Assertions.assertEquals(expected.toJSONString(), getCart(adminId).toJSONString());
    }

    // Cart update tests

    @Test
    @Sql("/billing-test-data.sql")
    public void cartUpdateInvalidQuantityZero()
        throws Exception
    {
        JSONObject request = new JSONObject();
        request.put("movieId", 1843866);
        request.put("quantity", 0);

        this.mockMvc.perform(post(CART_UPDATE_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .content(request.toJSONString())
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.INVALID_QUANTITY));
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void cartUpdateInvalidQuantityNegative()
        throws Exception
    {
        JSONObject request = new JSONObject();
        request.put("movieId", 1843866);
        request.put("quantity", -1);

        this.mockMvc.perform(post(CART_UPDATE_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .content(request.toJSONString())
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.INVALID_QUANTITY));
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void cartUpdateInvalidQuantityMax()
        throws Exception
    {
        JSONObject request = new JSONObject();
        request.put("movieId", 1843866);
        request.put("quantity", 11);

        this.mockMvc.perform(post(CART_UPDATE_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .content(request.toJSONString())
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.MAX_QUANTITY));
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void cartUpdateMovieNotInCart()
        throws Exception
    {
        JSONObject request = new JSONObject();
        request.put("movieId", 15324);
        request.put("quantity", 1);

        this.mockMvc.perform(post(CART_UPDATE_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .content(request.toJSONString())
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_ITEM_DOES_NOT_EXIST));
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void cartUpdateMovieQuantity()
        throws Exception
    {
        JSONObject request = new JSONObject();
        request.put("movieId", 1843866);
        request.put("quantity", 8);

        this.mockMvc.perform(post(CART_UPDATE_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .content(request.toJSONString())
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_ITEM_UPDATED));

        JSONArray expected = getModel("cartUpdateMovieQuantity", JSONArray.class);

        Assertions.assertEquals(expected.toJSONString(), getCart(adminId).toJSONString());
    }

    // Cart update tests

    @Test
    @Sql("/billing-test-data.sql")
    public void cartDeleteDoesNotExist()
        throws Exception
    {
        this.mockMvc.perform(delete(CART_DELETE_ID_PATH, 15324)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_ITEM_DOES_NOT_EXIST));
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void cartDeleteSuccess()
        throws Exception
    {
        this.mockMvc.perform(delete(CART_DELETE_ID_PATH, 1843866)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_ITEM_DELETED));

        JSONArray expected = getModel("cartDeleteSuccess", JSONArray.class);

        Assertions.assertEquals(expected.toJSONString(), getCart(adminId).toJSONString());
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void cartDeleteFailOnSecond()
        throws Exception
    {
        this.mockMvc.perform(delete(CART_DELETE_ID_PATH, 1843866)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_ITEM_DELETED));

        JSONArray expected = getModel("cartDeleteSuccess", JSONArray.class);

        Assertions.assertEquals(expected.toJSONString(), getCart(adminId).toJSONString());
        this.mockMvc.perform(delete(CART_DELETE_ID_PATH, 1843866)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_ITEM_DOES_NOT_EXIST));
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void cartDeleteDoesntEffectSameMovie()
        throws Exception
    {
        this.mockMvc.perform(delete(CART_DELETE_ID_PATH, 1843866)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_ITEM_DELETED));

        JSONArray expected = getModel("premiumCart", JSONArray.class);

        Assertions.assertEquals(expected.toJSONString(), getCart(premiumId).toJSONString());
    }

    // Cart Retrieve

    @Test
    @Sql("/empty-billing-test-data.sql")
    public void cartRetrieveEmpty()
        throws Exception
    {

        this.mockMvc.perform(get(CART_RETRIEVE_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_EMPTY))
                    .andExpect(jsonPath("items").doesNotHaveJsonPath())
                    .andExpect(jsonPath("total").doesNotHaveJsonPath());

    }

    @Test
    @Sql("/billing-test-data.sql")
    public void cartRetrieveAdmin()
        throws Exception
    {
        JSONObject expected = getModel("cartRetrieveAdmin", JSONObject.class);

        this.mockMvc.perform(get(CART_RETRIEVE_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_RETRIEVED))
                    .andExpect(jsonPath("total").value(expected.get("total")))
                    .andExpect(jsonPath("items").value(expected.get("items")));

    }

    @Test
    @Sql("/billing-test-data.sql")
    public void cartRetrieveEmployee()
        throws Exception
    {
        JSONObject expected = getModel("cartRetrieveEmployee", JSONObject.class);

        this.mockMvc.perform(get(CART_RETRIEVE_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, employeeHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_RETRIEVED))
                    .andExpect(jsonPath("total").value(expected.get("total")))
                    .andExpect(jsonPath("items").value(expected.get("items")));
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void cartRetrievePremium()
        throws Exception
    {
        JSONObject expected = getModel("cartRetrievePremium", JSONObject.class);

        this.mockMvc.perform(get(CART_RETRIEVE_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, premiumHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_RETRIEVED))
                    .andExpect(jsonPath("total").value(expected.get("total")))
                    .andExpect(jsonPath("items").value(expected.get("items")));

    }

    // Cart clear

    @Test
    @Sql("/billing-test-data.sql")
    public void cartClearAdmin()
        throws Exception
    {
        this.mockMvc.perform(post(CART_CLEAR_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_CLEARED));

        JSONArray employeeCart = getModel("employeeCart", JSONArray.class);
        JSONArray premiumCart  = getModel("premiumCart", JSONArray.class);

        Assertions.assertEquals("[]", getCart(adminId).toJSONString());
        Assertions.assertEquals(employeeCart.toJSONString(), getCart(employeeId).toJSONString());
        Assertions.assertEquals(premiumCart.toJSONString(), getCart(premiumId).toJSONString());
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void cartClearEmployee()
        throws Exception
    {
        this.mockMvc.perform(post(CART_CLEAR_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, employeeHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_CLEARED));

        JSONArray adminCart   = getModel("adminCart", JSONArray.class);
        JSONArray premiumCart = getModel("premiumCart", JSONArray.class);

        Assertions.assertEquals(adminCart.toJSONString(), getCart(adminId).toJSONString());
        Assertions.assertEquals("[]", getCart(employeeId).toJSONString());
        Assertions.assertEquals(premiumCart.toJSONString(), getCart(premiumId).toJSONString());
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void cartClearPremium()
        throws Exception
    {
        this.mockMvc.perform(post(CART_CLEAR_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, premiumHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_CLEARED));

        JSONArray adminCart    = getModel("adminCart", JSONArray.class);
        JSONArray employeeCart = getModel("employeeCart", JSONArray.class);

        Assertions.assertEquals(adminCart.toJSONString(), getCart(adminId).toJSONString());
        Assertions.assertEquals(employeeCart.toJSONString(), getCart(employeeId).toJSONString());
        Assertions.assertEquals("[]", getCart(premiumId).toJSONString());
    }

    // Order Payment

    @Test
    @Sql("/empty-billing-test-data.sql")
    public void orderPaymentEmptyCart()
        throws Exception
    {
        this.mockMvc.perform(get(ORDER_PAYMENT_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.CART_EMPTY))
                    .andExpect(jsonPath("paymentIntentId").doesNotHaveJsonPath())
                    .andExpect(jsonPath("clientSecret").doesNotHaveJsonPath());
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void orderPaymentCreated()
        throws Exception
    {
        this.mockMvc.perform(get(ORDER_PAYMENT_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.ORDER_PAYMENT_INTENT_CREATED))
                    .andExpect(jsonPath("paymentIntentId").hasJsonPath())
                    .andExpect(jsonPath("clientSecret").hasJsonPath());
    }

    // Order Complete

    @Test
    @Sql("/billing-test-data.sql")
    public void orderComplete()
        throws Exception
    {
        PaymentIntent intent = PaymentIntent.create(
            PaymentIntentCreateParams
                .builder()
                .setCurrency("USD")
                .setDescription("Test")
                .setAmount(26915L)
                .putMetadata("userId", adminId.toString())
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods
                        .builder()
                        .setEnabled(true)
                        .build()
                )
                .build());

        intent.confirm(
            PaymentIntentConfirmParams
                .builder()
                .setPaymentMethod("pm_card_visa")
                .setReturnUrl("http://localhost")
                .build()
        );

        JSONObject request = new JSONObject();

        request.put("paymentIntentId", intent.getId());

        this.mockMvc.perform(post(ORDER_COMPLETE_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .content(request.toJSONString())
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.ORDER_COMPLETED));

        JSONObject adminSale = getModel("orderComplete", JSONObject.class);

        Long saleId = getMostRecentSale(adminId);

        Assertions.assertEquals(adminSale.toJSONString(), getSaleItems(saleId).toJSONString());
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void orderCompleteWrongUser()
        throws Exception
    {
        PaymentIntent intent = PaymentIntent.create(
            PaymentIntentCreateParams
                .builder()
                .setCurrency("USD")
                .setDescription("Test")
                .setAmount(24920L)
                .putMetadata("userId", premiumId.toString())
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods
                        .builder()
                        .setEnabled(true)
                        .build()
                )
                .build());

        intent.confirm(
            PaymentIntentConfirmParams
                .builder()
                .setPaymentMethod("pm_card_visa")
                .setReturnUrl("http://localhost")
                .build()
        );

        JSONObject request = new JSONObject();

        request.put("paymentIntentId", intent.getId());

        this.mockMvc.perform(post(ORDER_COMPLETE_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .content(request.toJSONString())
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.ORDER_CANNOT_COMPLETE_WRONG_USER));
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void orderCompleteNotCompleted()
        throws Exception
    {
        PaymentIntent intent = PaymentIntent.create(
            PaymentIntentCreateParams
                .builder()
                .setCurrency("USD")
                .setDescription("Test")
                .setAmount(24920L)
                .putMetadata("userId", adminId.toString())
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods
                        .builder()
                        .setEnabled(true)
                        .build()
                )
                .build());

        JSONObject request = new JSONObject();

        request.put("paymentIntentId", intent.getId());

        this.mockMvc.perform(post(ORDER_COMPLETE_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .content(request.toJSONString())
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.ORDER_CANNOT_COMPLETE_NOT_SUCCEEDED));
    }

    // Order List

    @Test
    @Sql("/billing-test-data.sql")
    public void orderListFoundAdmin()
        throws Exception
    {
        this.mockMvc.perform(get(ORDER_LIST_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.ORDER_LIST_FOUND_SALES))
                    .andExpect(jsonPath("sales").value(getModel("orderListFoundAdmin")));
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void orderListFoundEmployee()
        throws Exception
    {
        this.mockMvc.perform(get(ORDER_LIST_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, employeeHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.ORDER_LIST_FOUND_SALES))
                    .andExpect(jsonPath("sales").value(getModel("orderListFoundEmployee")));
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void orderListFoundPremium()
        throws Exception
    {
        this.mockMvc.perform(get(ORDER_LIST_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, premiumHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.ORDER_LIST_FOUND_SALES))
                    .andExpect(jsonPath("sales").value(getModel("orderListFoundPremium")));
    }

    @Test
    @Sql("/empty-billing-test-data.sql")
    public void orderListNoneFound()
        throws Exception
    {
        this.mockMvc.perform(get(ORDER_LIST_PATH)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.ORDER_LIST_NO_SALES_FOUND))
                    .andExpect(jsonPath("sales").doesNotHaveJsonPath());
    }

    // Order Detail

    @Test
    @Sql("/billing-test-data.sql")
    public void orderDetailWrongUserAdmin()
        throws Exception
    {
        this.mockMvc.perform(get(ORDER_DETAIL_PATH, EMPLOYEE_SALE_ONE_ID)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, adminHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.ORDER_DETAIL_NOT_FOUND))
                    .andExpect(jsonPath("total").doesNotHaveJsonPath())
                    .andExpect(jsonPath("items").doesNotHaveJsonPath());
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void orderDetailEmployee()
        throws Exception
    {
        JSONObject expected = getModel("orderDetailEmployee", JSONObject.class);

        this.mockMvc.perform(get(ORDER_DETAIL_PATH, EMPLOYEE_SALE_TWO_ID)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, employeeHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.ORDER_DETAIL_FOUND))
                    .andExpect(jsonPath("total").value(expected.get("total")))
                    .andExpect(jsonPath("items").value(expected.get("items")));
    }

    @Test
    @Sql("/billing-test-data.sql")
    public void orderDetailPremium()
        throws Exception
    {
        JSONObject expected = getModel("orderDetailPremium", JSONObject.class);

        this.mockMvc.perform(get(ORDER_DETAIL_PATH, PREMIUM_SALE_ONE_ID)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .header(HttpHeaders.AUTHORIZATION, premiumHeader))
                    .andDo(print())
                    .andExpectAll(isResult(BillingResults.ORDER_DETAIL_FOUND))
                    .andExpect(jsonPath("total").value(expected.get("total")))
                    .andExpect(jsonPath("items").value(expected.get("items")));
    }
}
