package com.batchengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class BatchControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void textUploadIsAcknowledgedAndResultsBecomeAvailableInOrder() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "prompts.txt", "text/plain", "one\ntwo\nthree".getBytes());

        MvcResult submitted = mockMvc.perform(multipart("/api/v1/batches").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.batchId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.total").value(3))
                .andReturn();
        String batchId = objectMapper.readTree(
                submitted.getResponse().getContentAsString()).get("batchId").asText();

        JsonNode response = awaitTerminalResults(batchId);
        assertThat(response.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(response.get("results").get(0).get("index").asInt()).isZero();
        assertThat(response.get("results").get(1).get("index").asInt()).isEqualTo(1);
        assertThat(response.get("results").get(2).get("index").asInt()).isEqualTo(2);
    }

    @Test
    void invalidUploadReturnsConsistentBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "prompts.txt", "text/plain", "one\n\ntwo".getBytes());

        mockMvc.perform(multipart("/api/v1/batches").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unknownBatchReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/batches/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BATCH_NOT_FOUND"));
    }

    private JsonNode awaitTerminalResults(String batchId) throws Exception {
        for (int attempt = 0; attempt < 40; attempt++) {
            MvcResult result = mockMvc.perform(
                            get("/api/v1/batches/{id}/results", batchId))
                    .andReturn();
            if (result.getResponse().getStatus() == 200) {
                return objectMapper.readTree(result.getResponse().getContentAsString());
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Batch did not complete");
    }
}
