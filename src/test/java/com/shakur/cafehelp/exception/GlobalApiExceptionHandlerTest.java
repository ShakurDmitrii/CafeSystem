package com.shakur.cafehelp.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalApiExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .build();
    }

    @Test
    void responseStatusReasonReachesTheClient() throws Exception {
        mvc.perform(get("/test/status"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.message").value("Не хватает ингредиентов на складе: Лосось (120.00/300.00 г)"));
    }

    @Test
    void russianRuntimeExceptionIsARejectionNotAServerCrash() throws Exception {
        mvc.perform(get("/test/runtime-ru"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Уже есть открытая смена у: Анна"));
    }

    @Test
    void technicalRuntimeExceptionIsHiddenBehindAnIncidentCode() throws Exception {
        mvc.perform(get("/test/runtime-tech"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("Код для поиска в логе")
                        .doesNotContain("NullPointer"));
    }

    @Test
    void missingAndMistypedParametersAreNamed() throws Exception {
        mvc.perform(get("/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Не передан обязательный параметр «personCode»"));
        mvc.perform(get("/test/param").param("personCode", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Параметр «personCode» должен быть целым числом (получено «abc»)"));
    }

    @Test
    void badJsonFieldIsNamed() throws Exception {
        mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"qty\":\"много\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Поле «qty» имеет неверный формат: «много»"));
    }

    @Test
    void integrityViolationsAreTranslated() {
        assertThat(GlobalApiExceptionHandler.describeIntegrity(
                "ERROR: update or delete on table \"dish\" violates foreign key constraint \"orderdish_dish_fk\" on table \"orderdish\""))
                .isEqualTo("Нельзя удалить или изменить запись: она используется в других данных (ограничение orderdish_dish_fk)");
        assertThat(GlobalApiExceptionHandler.describeIntegrity(
                "ERROR: duplicate key value violates unique constraint \"dish_unique\""))
                .isEqualTo("Такая запись уже существует (ограничение dish_unique)");
        assertThat(GlobalApiExceptionHandler.describeIntegrity(
                "ERROR: null value in column \"dishname\" of relation \"dish\" violates not-null constraint"))
                .isEqualTo("Не заполнено обязательное поле «dishname»");
    }

    @RestController
    static class FailingController {
        @GetMapping("/test/status")
        String status() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Не хватает ингредиентов на складе: Лосось (120.00/300.00 г)");
        }

        @GetMapping("/test/runtime-ru")
        String runtimeRu() {
            throw new RuntimeException("Уже есть открытая смена у: Анна");
        }

        @GetMapping("/test/runtime-tech")
        String runtimeTech() {
            throw new NullPointerException("Cannot invoke getId() because shift is null");
        }

        @GetMapping("/test/param")
        String param(@RequestParam int personCode) {
            return "ok";
        }

        @PostMapping("/test/body")
        String body(@RequestBody Payload payload) {
            return "ok";
        }
    }

    static class Payload {
        public Integer qty;
    }
}
