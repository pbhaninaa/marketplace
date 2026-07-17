package com.agrimarket.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.service.PeachPaymentService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PeachControllerTest {

    @Test
    void shopperFormPostIsProcessedThenRedirectedToSpaWithSeeOther() throws Exception {
        PeachPaymentService service = mock(PeachPaymentService.class);
        when(service.getFrontendReturnUrl("Ref123456"))
                .thenReturn("https://shop.example/peach/return?ref=Ref123456");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PeachController(service)).build();

        mvc.perform(post("/api/public/peach/return")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("merchantTransactionId", "Ref123456")
                        .param("signature", "signed"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "https://shop.example/peach/return?ref=Ref123456"));

        verify(service).handleWebhook(anyMap());
    }

    @Test
    void jsonWebhookFlattensNestedResultForSignatureValidation() throws Exception {
        PeachPaymentService service = mock(PeachPaymentService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PeachController(service)).build();

        mvc.perform(post("/api/public/peach/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantTransactionId": "Ref123456",
                                  "result": {"code": "000.100.110"}
                                }
                                """))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(service).handleWebhook(captor.capture());
        assertThat(captor.getValue()).containsEntry("result.code", "000.100.110");
    }
}
