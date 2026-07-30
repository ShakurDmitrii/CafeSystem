package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.Service.VkClientLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VkBotControllerSecurityTest {

    @Test
    void missingApiTokenClosesVkBotApi() throws Exception {
        VkClientLinkService service = mock(VkClientLinkService.class);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new VkBotController(service, ""))
                .build();

        mockMvc.perform(get("/api/vk-bot/status").param("vkUserId", "1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("VK bot API is not configured"));

        verifyNoInteractions(service);
    }
}
