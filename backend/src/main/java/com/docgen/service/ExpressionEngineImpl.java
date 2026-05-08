package com.docgen.service;

import com.docgen.dto.ExpressionValidationResult;
import com.docgen.entity.ExpressionType;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Expression engine implementation that delegates evaluation to the
 * Docxtemplater Node.js service's /evaluate endpoint.
 * <p>
 * JavaScript expressions are executed in a secure sandbox (isolated-vm).
 * Excel formulas are executed via Formula.js in the Node.js service.
 */
@Service
public class ExpressionEngineImpl implements ExpressionEngine {

    private static final Logger log = LoggerFactory.getLogger(ExpressionEngineImpl.class);

    private final RestTemplate restTemplate;
    private final String serviceUrl;

    public ExpressionEngineImpl(
            RestTemplate restTemplate,
            @Value("${docxtemplater.service-url:http://localhost:3000}") String serviceUrl) {
        this.restTemplate = restTemplate;
        this.serviceUrl = serviceUrl;
    }

    @Override
    public Object evaluate(String expression, ExpressionType type, Map<String, Object> context) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("expression", expression);
        requestBody.put("type", type.toEvaluateApiType());
        requestBody.put("context", context != null ? context : Collections.emptyMap());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    serviceUrl + "/evaluate",
                    HttpMethod.POST,
                    entity,
                    Map.class);

            Map<?, ?> body = response.getBody();
            if (body == null) {
                throw new BusinessException(ErrorCode.EXPRESSION_EVALUATION_FAILED,
                        "Expression evaluation returned an empty result", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            Boolean success = (Boolean) body.get("success");
            if (Boolean.FALSE.equals(success)) {
                String error = stringifyEvaluateError(body.get("error"));
                throw new BusinessException(ErrorCode.EXPRESSION_EVALUATION_FAILED,
                        "Expression evaluation failed: " + error, HttpStatus.BAD_REQUEST);
            }

            return body.get("result");
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Failed to call expression evaluation service: {}", e.getMessage());
            throw new BusinessException(ErrorCode.EXPRESSION_EVALUATION_FAILED,
                    "Expression service call failed: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE, e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> evaluateAll(List<ExpressionConfig> expressions, Map<String, Object> context) {
        Map<String, Object> results = new LinkedHashMap<>();
        Map<String, Object> currentContext = new HashMap<>(context != null ? context : Collections.emptyMap());

        for (ExpressionConfig config : expressions) {
            try {
                Object result = evaluate(config.expression(), config.type(), currentContext);
                results.put(config.name(), result);
                // Make result available to subsequent expressions
                currentContext.put(config.name(), result);
            } catch (BusinessException e) {
                log.warn("Expression '{}' evaluation failed: {}", config.name(), e.getMessage());
                throw e;
            }
        }

        return results;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExpressionValidationResult validateExpression(String expression, ExpressionType type) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("expression", expression);
        requestBody.put("type", type.toEvaluateApiType());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    serviceUrl + "/evaluate",
                    HttpMethod.POST,
                    entity,
                    Map.class);

            Map<?, ?> body = response.getBody();
            if (body == null) {
                return ExpressionValidationResult.failure("Validation service returned an empty result", null);
            }

            Boolean success = (Boolean) body.get("success");
            if (Boolean.TRUE.equals(success)) {
                return ExpressionValidationResult.success();
            }

            String error = stringifyEvaluateError(body.get("error"));
            Integer position = body.get("errorPosition") != null
                    ? ((Number) body.get("errorPosition")).intValue()
                    : null;
            return ExpressionValidationResult.failure(error, position);
        } catch (RestClientException e) {
            log.error("Failed to call expression validation service: {}", e.getMessage());
            return ExpressionValidationResult.failure("Expression service unavailable: " + e.getMessage(), null);
        }
    }

    /**
     * Normalizes {@code error} from {@code /evaluate}: either a plain string or {@code { code, message }}.
     */
    static String stringifyEvaluateError(Object error) {
        if (error == null) {
            return "Unknown error";
        }
        if (error instanceof String s) {
            return s;
        }
        if (error instanceof Map<?, ?> m) {
            Object message = m.get("message");
            Object code = m.get("code");
            if (message != null && code != null) {
                return code + ": " + message;
            }
            if (message != null) {
                return String.valueOf(message);
            }
        }
        return String.valueOf(error);
    }
}
