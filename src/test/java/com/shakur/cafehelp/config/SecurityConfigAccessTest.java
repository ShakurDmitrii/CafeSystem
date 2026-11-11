package com.shakur.cafehelp.config;

import com.shakur.cafehelp.security.JwtAuthenticationFilter;
import com.shakur.cafehelp.security.JwtService;
import com.shakur.cafehelp.security.ServiceTokenAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigAccessTest.StubController.class)
@Import({
        SecurityConfig.class,
        JwtService.class,
        JwtAuthenticationFilter.class,
        ServiceTokenAuthenticationFilter.class,
        SecurityConfigAccessTest.StubController.class
})
@TestPropertySource(properties = {
        "security.jwt.secret=Y2FmZWhlbHAtdGVzdC1qd3Qtc2VjcmV0LWtleS0zMi1ieXRlcw==",
        "internal.service.token=cafehelp-test-internal-token"
})
class SecurityConfigAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void workerCanReadMenuAndCatalog() throws Exception {
        expect("WORKER", HttpMethod.GET, "/api/dishes", 200);
        expect("WORKER", HttpMethod.GET, "/api/dishes/1", 200);
        expect("WORKER", HttpMethod.GET, "/api/dish-sets", 200);
        expect("WORKER", HttpMethod.GET, "/api/product", 200);
        expect("WORKER", HttpMethod.GET, "/api/shifts", 200);
    }

    @Test
    void workerCannotModifyMenuCatalogOrUploadFiles() throws Exception {
        expect("WORKER", HttpMethod.POST, "/api/dishes", 403);
        expect("WORKER", HttpMethod.PUT, "/api/dishes/1", 403);
        expect("WORKER", HttpMethod.DELETE, "/api/dishes/1", 403);
        expect("WORKER", HttpMethod.POST, "/api/dish-sets", 403);
        expect("WORKER", HttpMethod.PUT, "/api/dish-sets/1", 403);
        expect("WORKER", HttpMethod.DELETE, "/api/dish-sets/1", 403);
        expect("WORKER", HttpMethod.POST, "/api/product", 403);
        expect("WORKER", HttpMethod.PUT, "/api/product/1", 403);
        expect("WORKER", HttpMethod.POST, "/api/files/upload-image", 403);
    }

    @Test
    void workerCanOpenAndCloseShiftButNotEditIt() throws Exception {
        expect("WORKER", HttpMethod.POST, "/api/shifts/create", 200);
        expect("WORKER", HttpMethod.POST, "/api/shifts/open", 200);
        expect("WORKER", HttpMethod.POST, "/api/shifts/1/close", 200);
        expect("WORKER", HttpMethod.POST, "/api/shifts/1/update", 403);
    }

    @Test
    void ownerKeepsFullAccess() throws Exception {
        expect("OWNER", HttpMethod.POST, "/api/dishes", 200);
        expect("OWNER", HttpMethod.DELETE, "/api/dishes/1", 200);
        expect("OWNER", HttpMethod.PUT, "/api/dish-sets/1", 200);
        expect("OWNER", HttpMethod.PUT, "/api/product/1", 200);
        expect("OWNER", HttpMethod.POST, "/api/files/upload-image", 200);
        expect("OWNER", HttpMethod.POST, "/api/shifts/1/update", 200);
    }

    private void expect(String role, HttpMethod method, String path, int expectedStatus) throws Exception {
        String token = jwtService.generateToken(role.toLowerCase(), 1, 1, role);
        mockMvc.perform(request(method, path).header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus));
    }

    @RestController
    static class StubController {
        @RequestMapping({
                "/api/dishes", "/api/dishes/{id}",
                "/api/dish-sets", "/api/dish-sets/{id}",
                "/api/product", "/api/product/{id}",
                "/api/files/upload-image",
                "/api/shifts", "/api/shifts/create", "/api/shifts/open",
                "/api/shifts/{id}/close", "/api/shifts/{id}/update"
        })
        String ok() {
            return "ok";
        }
    }
}
