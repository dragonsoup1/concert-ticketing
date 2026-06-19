package com.api.backend.payment.api;

import com.api.backend.global.response.ApiResponse;
import com.api.backend.payment.api.dto.PaymentRequest;
import com.api.backend.payment.api.dto.PaymentResponse;
import com.api.backend.payment.application.AlreadyProcessedPaymentException;
import com.api.backend.payment.application.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ApiResponse<PaymentResponse> processPayment(
        @Valid @RequestBody PaymentRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            return ApiResponse.ok(paymentService.processPayment(request, idempotencyKey));
        } catch (AlreadyProcessedPaymentException e) {
            return ApiResponse.ok(PaymentResponse.from(e.getPayment()));
        }
    }
}
