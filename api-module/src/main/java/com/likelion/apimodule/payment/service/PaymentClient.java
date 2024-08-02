package com.likelion.apimodule.payment.service;


import com.likelion.apimodule.payment.dto.request.ApprovalRequest;
import com.likelion.apimodule.payment.dto.request.ApproveFinalReq;
import com.likelion.apimodule.payment.dto.response.TossPaymentResponse;
import com.likelion.apimodule.payment.provider.PaymentAuthorizationHeaderProvider;
import com.likelion.apimodule.payment.provider.TossAuthorizationHeaderProvider;
import com.likelion.coremodule.payment.exception.PaymentErrorCode;
import com.likelion.coremodule.payment.exception.PaymentException;
import lombok.Getter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.lang.reflect.InvocationTargetException;

@Component
public class PaymentClient {

    private final WebClient webClient;
    private final String authorizationHeader;

    public PaymentClient(WebClient webClient, PaymentAuthorizationHeaderProvider.Factory providerFactory)
            throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        this.webClient = webClient;
        TossAuthorizationHeaderProvider provider = providerFactory.create(TossAuthorizationHeaderProvider.class);
        this.authorizationHeader = provider.getAuthorizationHeader();

    }

    public TossPaymentResponse confirmPayment(ApprovalRequest request, String orderNum) {

        ApproveFinalReq finalReq = new ApproveFinalReq(
                request.amount(),
                orderNum,
                request.paymentKey()
        );

        return paymentGatewayRequest(TossApiEndpoint.APPROVAL)
                .bodyValue(finalReq)
                .retrieve()
                .bodyToMono(TossPaymentResponse.class)
                .doOnError(throwable -> {
                    WebClientResponseException exception = (WebClientResponseException) throwable;
                    throw new PaymentException(PaymentErrorCode.PAYMENT_FAIILED, exception);
                })
                .block();
    }

    public Boolean isValidOrderId(String orderId) {
        TossApiEndpoint.CHECK.addPathValue(orderId);
        return paymentGatewayRequest(TossApiEndpoint.CHECK)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(voidResponseEntity -> voidResponseEntity.getStatusCode().is2xxSuccessful())
                .thenReturn(Boolean.FALSE)//존재하는 경우 FALSE를 반환해 사용 불가능한 orderId임을 알림
                .onErrorReturn(Boolean.TRUE)// 존재하지 않는경우 사용 가능한것으로 판단하여 TRUE 반환
                .block();

    }

    private WebClient.RequestBodySpec paymentGatewayRequest(TossApiEndpoint endpoint) {
        return webClient.method(endpoint.getHttpMethod())
                .uri(endpoint.getPath())
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .contentType(MediaType.APPLICATION_JSON);
    }

    @Getter
    private enum TossApiEndpoint {
        APPROVAL("payments/confirm", HttpMethod.POST),
        CHECK("payments/orders/", HttpMethod.GET);

        private static final String ROOT_PATH = "https://api.tosspayments.com/v1/";
        private String path;
        private final HttpMethod httpMethod;

        TossApiEndpoint(String path, HttpMethod httpMethod) {
            this.path = ROOT_PATH.concat(path);
            this.httpMethod = httpMethod;
        }

        public void addPathValue(String orderId) {
            this.path += "/" + orderId;
        }
    }
}