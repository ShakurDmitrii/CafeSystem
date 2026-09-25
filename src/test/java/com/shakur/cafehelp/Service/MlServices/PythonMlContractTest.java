package com.shakur.cafehelp.Service.MlServices;

import com.shakur.cafehelp.DTO.MlDTO.BatchPredictionRequestDTO;
import com.shakur.cafehelp.DTO.MlDTO.RollPredictionRequestDTO;
import com.shakur.cafehelp.DTO.MlDTO.SalesRecordDTO;
import com.shakur.cafehelp.DTO.MlDTO.OptimizationRequestDTO;
import com.shakur.cafehelp.Service.DishService;
import com.shakur.cafehelp.Service.ProductService;
import com.shakur.cafehelp.Service.TechProductService;
import com.shakur.cafehelp.exception.PythonServiceException;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PythonMlContractTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private PredictionService predictionService;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        predictionService = new PredictionService(
                restTemplate,
                mock(SalesService.class),
                mock(MenuService.class),
                mock(InventoryService.class),
                mock(DishService.class),
                mock(ProductService.class),
                mock(TechProductService.class),
                mock(DSLContext.class)
        );
        ReflectionTestUtils.setField(predictionService, "mlServiceUrl", "http://python:8000");
    }

    @Test
    void singlePredictionSendsOnlyFieldsAcceptedByStrictPythonSchema() {
        RollPredictionRequestDTO request = new RollPredictionRequestDTO();
        request.setIngredients(List.of("rice", "salmon"));
        request.setIncludeCostAnalysis(true);
        request.setLocationId("cafe-1");

        server.expect(once(), requestTo("http://python:8000/api/ml/predict"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"ingredients":["rice","salmon"]}
                        """, true))
                .andRespond(withSuccess("""
                        {
                          "predictedSales": 12.5,
                          "ingredients": ["rice", "salmon"],
                          "confidenceScore": 0.82,
                          "modelVersion": "model-1"
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = predictionService.predictSales(request);

        assertThat(response.getPredictedSales()).isEqualTo(12.5);
        assertThat(response.getModelVersion()).isEqualTo("model-1");
        server.verify();
    }

    @Test
    void batchPredictionMapsPythonResultsFieldToJavaPredictions() {
        RollPredictionRequestDTO roll = new RollPredictionRequestDTO();
        roll.setIngredients(List.of("rice"));
        BatchPredictionRequestDTO request = new BatchPredictionRequestDTO();
        request.setRequestId("java-request-id");
        request.setRolls(List.of(roll));

        server.expect(once(), requestTo("http://python:8000/api/ml/predict/batch"))
                .andExpect(content().json("""
                        {"rolls":[{"ingredients":["rice"]}]}
                        """, true))
                .andRespond(withSuccess("""
                        {
                          "results": [{
                            "ingredients": ["rice"],
                            "predictedSales": 8.0,
                            "confidenceScore": 0.75,
                            "modelVersion": "model-1"
                          }],
                          "modelVersion": "model-1"
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = predictionService.batchPredict(request);

        assertThat(response.getPredictions()).hasSize(1);
        assertThat(response.getPredictions().getFirst().getPredictedSales()).isEqualTo(8.0);
        server.verify();
    }

    @Test
    void partialPredictionResponseIsRejected() {
        RollPredictionRequestDTO request = new RollPredictionRequestDTO();
        request.setIngredients(List.of("rice"));
        server.expect(requestTo("http://python:8000/api/ml/predict"))
                .andRespond(withSuccess("{\"ingredients\":[\"rice\"]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> predictionService.predictSales(request))
                .isInstanceOfSatisfying(PythonServiceException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(PythonServiceException.Code.INVALID_RESPONSE)
                );
    }

    @Test
    void trainingKeepsIngredientListWithoutStringBrackets() {
        MlTrainingService trainingService = new MlTrainingService(
                mock(SalesService.class),
                mock(MenuService.class),
                mock(InventoryService.class),
                restTemplate
        );
        SalesRecordDTO sale = SalesRecordDTO.builder()
                .rollName("Филадельфия")
                .ingredients(List.of("Лосось", "Рис"))
                .quantity(4)
                .saleDate(LocalDate.of(2026, 8, 29))
                .build();

        var records = trainingService.prepareTrainingData(List.of(sale), List.of());

        assertThat(records).singleElement().satisfies(record ->
                assertThat(record.get("ingredients")).isEqualTo(List.of("лосось", "рис"))
        );
    }

    @Test
    void trainingSkipsSalesWithoutRecipeIngredients() {
        MlTrainingService trainingService = new MlTrainingService(
                mock(SalesService.class),
                mock(MenuService.class),
                mock(InventoryService.class),
                restTemplate
        );
        SalesRecordDTO withoutRecipe = SalesRecordDTO.builder()
                .rollName("Лимонад")
                .ingredients(List.of())
                .quantity(1)
                .saleDate(LocalDate.of(2026, 8, 29))
                .build();
        SalesRecordDTO withRecipe = SalesRecordDTO.builder()
                .rollName("Филадельфия")
                .ingredients(List.of("Рис", "Лосось"))
                .quantity(2)
                .saleDate(LocalDate.of(2026, 8, 29))
                .build();

        var records = trainingService.prepareTrainingData(
                List.of(withoutRecipe, withRecipe),
                List.of()
        );

        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.get("rollName")).isEqualTo("Филадельфия");
            assertThat(record.get("ingredients")).isEqualTo(List.of("рис", "лосось"));
        });
    }

    @Test
    void malformedPythonJsonIsReportedAsInvalidUpstreamResponse() {
        RollPredictionRequestDTO request = new RollPredictionRequestDTO();
        request.setIngredients(List.of("rice"));
        server.expect(requestTo("http://python:8000/api/ml/predict"))
                .andRespond(withSuccess("{broken-json", MediaType.APPLICATION_JSON));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> predictionService.predictSales(request))
                .isInstanceOfSatisfying(PythonServiceException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(PythonServiceException.Code.INVALID_RESPONSE)
                );
    }

    @Test
    void optimizationResponseCanBeDeserializedIntoReachableJavaDtos() {
        OptimizationRequestDTO request = new OptimizationRequestDTO();
        request.setRequestId("optimization-1");
        server.expect(requestTo("http://python:8000/api/ml/optimize"))
                .andRespond(withSuccess("""
                        {
                          "status": "completed",
                          "results": [{
                            "ingredients": ["rice", "salmon"],
                            "predictedSales": 10.0,
                            "estimatedCost": 150.0,
                            "estimatedProfit": 250.0,
                            "profitMargin": 0.625,
                            "noveltyScore": 0.7,
                            "fitnessScore": 0.9,
                            "generationFound": 12
                          }],
                          "statistics": {"populationSize": 100, "generations": 50}
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = predictionService.optimizeRolls(request);

        assertThat(response.getStatus()).isEqualTo("completed");
        assertThat(response.getResults()).singleElement().satisfies(result -> {
            assertThat(result.getIngredients()).containsExactly("rice", "salmon");
            assertThat(result.getEstimatedProfit()).isEqualTo(250.0);
        });
        assertThat(response.getStatistics().getPopulationSize()).isEqualTo(100);
        server.verify();
    }

    @Test
    void trainingRejectsBatchSmallerThanPythonMinimumBeforeNetworkCall() {
        MlTrainingService trainingService = trainingService();

        assertThatThrownBy(() -> trainingService.sendToPythonML(List.of(Map.of(
                "ingredients", List.of("rice"),
                "sales", 1,
                "date", "2026-08-29"
        )))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10");
    }

    @Test
    void emptySuccessfulTrainingResponseIsRejectedAsInvalid() {
        MlTrainingService trainingService = trainingService();
        ReflectionTestUtils.setField(trainingService, "pythonMlServiceUrl", "http://python:8000");
        List<Map<String, Object>> records = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> Map.<String, Object>of(
                        "ingredients", List.of("rice"),
                        "sales", index + 1,
                        "date", "2026-08-29"
                ))
                .toList();
        server.expect(requestTo("http://python:8000/api/ml/train"))
                .andRespond(withSuccess());

        assertThatThrownBy(() -> trainingService.sendToPythonML(records))
                .isInstanceOfSatisfying(PythonServiceException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(PythonServiceException.Code.INVALID_RESPONSE)
                );
        server.verify();
    }

    private MlTrainingService trainingService() {
        return new MlTrainingService(
                mock(SalesService.class),
                mock(MenuService.class),
                mock(InventoryService.class),
                restTemplate
        );
    }

    @Test
    void trainingAggregatesDishPerDayWithoutInventingZeroSaleDays() {
        var first = SalesRecordDTO.builder().rollId("1").rollName("Ролл")
                .ingredients(List.of("Рис", "Лосось")).quantity(2)
                .saleDate(LocalDate.of(2026, 8, 1)).build();
        var second = SalesRecordDTO.builder().rollId("1").rollName("Ролл")
                .ingredients(List.of("лосось", "рис", "рис")).quantity(3)
                .saleDate(LocalDate.of(2026, 8, 1)).build();
        var nextDay = SalesRecordDTO.builder().rollId("1").rollName("Ролл")
                .ingredients(List.of("рис", "лосось")).quantity(1)
                .saleDate(LocalDate.of(2026, 8, 3)).build();
        var records = trainingService().prepareTrainingData(List.of(first, second, nextDay), List.of());
        assertThat(records).hasSize(2);
        assertThat(records.getFirst().get("sales")).isEqualTo(5);
        assertThat(records.getFirst().get("date")).isEqualTo("2026-08-01");
        assertThat(records.get(1).get("sales")).isEqualTo(1);
    }

    @Test
    void uncalibratedConfidenceIsAcceptedAsNull() {
        var request = new RollPredictionRequestDTO();
        request.setIngredients(List.of("rice"));
        server.expect(requestTo("http://python:8000/api/ml/predict"))
                .andRespond(withSuccess("""
                        {"ingredients":["rice"],"predictedSales":3,"confidenceScore":null,
                         "modelVersion":"test","target":"daily_quantity_on_sale_days"}
                        """, MediaType.APPLICATION_JSON));
        assertThat(predictionService.predictSales(request).getConfidenceScore()).isNull();
        server.verify();
    }
}
