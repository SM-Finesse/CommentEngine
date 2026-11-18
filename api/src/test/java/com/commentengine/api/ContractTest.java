package com.commentengine.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commentengine.util.ErrorCode;
import com.commentengine.util.ErrorResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ContractTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Item item(String key) {
        return new Item(key, "label", "meaning", "12.3", "up", Degree.STRONG, "ok", List.of(), List.of());
    }

    @Test
    void itemUsesSnakeCase() {
        String json = mapper.writeValueAsString(item("a"));
        assertTrue(json.contains("\"value_text\""));
        assertTrue(json.contains("\"must_not\""));
        assertTrue(json.contains("\"degree\":\"strong\""));
    }

    @Test
    void lightRequestReadsSnakeCase() {
        String json = """
            {"items":[{"key":"a","label":"l","meaning":"m","value_text":"1",
              "direction":"up","degree":"weak","reliability":"ok"}],
             "deadline_ms":5000}
            """;
        LightRequest req = mapper.readValue(json, LightRequest.class);
        assertEquals(5000, req.deadlineMs());
        assertEquals(Degree.WEAK, req.items().get(0).degree());
        assertTrue(validator.validate(req).isEmpty());
    }

    @Test
    void deadlineRange() {
        assertFalse(validator.validate(new LightRequest(null, List.of(item("a")), 999)).isEmpty());
        assertTrue(validator.validate(new LightRequest(null, List.of(item("a")), 1000)).isEmpty());
        assertTrue(validator.validate(new LightRequest(null, List.of(item("a")), 120000)).isEmpty());
        assertFalse(validator.validate(new LightRequest(null, List.of(item("a")), 120001)).isEmpty());
    }

    @Test
    void itemCount() {
        List<Item> many = IntStream.range(0, 33).mapToObj(i -> item("k" + i)).toList();
        assertFalse(validator.validate(new LightRequest(null, List.of(), 5000)).isEmpty());
        assertTrue(validator.validate(new LightRequest(null, many.subList(0, 32), 5000)).isEmpty());
        assertFalse(validator.validate(new LightRequest(null, many, 5000)).isEmpty());
    }

    @Test
    void missingRequiredField() {
        Item bad = new Item("a", "", "m", "1", "up", null, "ok", null, null);
        assertEquals(2, validator.validate(new LightRequest(null, List.of(bad), 5000)).size());
    }

    @Test
    void sectionNeedsKeyAndTitle() {
        SectionRequest req = new SectionRequest(null, new Section("", "t", null), List.of(item("a")), 5000);
        assertEquals(1, validator.validate(req).size());
    }

    @Test
    void errorResponseUsesSnakeCase() {
        String json = mapper.writeValueAsString(ErrorResponse.of(ErrorCode.LLM_TIMEOUT, "late"));
        assertTrue(json.contains("\"error_code\":\"LLM_TIMEOUT\""));
        assertTrue(json.contains("\"message\":\"late\""));
    }
}
