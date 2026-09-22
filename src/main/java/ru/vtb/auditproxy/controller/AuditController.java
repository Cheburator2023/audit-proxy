package ru.vtb.auditproxy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.vtb.auditproxy.dto.AuditRequest;
import ru.vtb.auditproxy.dto.AuditResponse;
import ru.vtb.auditproxy.exception.AuditSendException;
import ru.vtb.auditproxy.service.AuditService;
import ru.vtb.auditproxy.validation.EventCodeValidator;

@Slf4j
@RestController
@RequestMapping("/api/v2")
@RequiredArgsConstructor
public class AuditController {

    private static final String HEADER_TRACEPARENT = "traceparent";

    private final AuditService auditService;
    private final EventCodeValidator eventCodeValidator;

    @PostMapping("/audit")
    public ResponseEntity<AuditResponse> audit(@Valid @RequestBody AuditRequest request,
                                               HttpServletRequest httpRequest) {
        log.info("Received audit request (v2): eventCode={}, eventClass={}, correlationId={}, timestamp={}",
                request.getEventCode(), request.getEventClass(), request.getCorrelationId(), request.getTimestamp());

        if (!eventCodeValidator.isValid(request.getEventCode())) {
            log.warn("Invalid event code: {}", request.getEventCode());
            return ResponseEntity.badRequest()
                    .body(new AuditResponse("error", "Invalid event code: " + request.getEventCode()));
        }

        enrichRequestFromHttpContext(request, httpRequest);

        try {
            AuditResponse response = auditService.sendAuditEvent(request);
            log.debug("Audit request processed successfully for eventCode={}", request.getEventCode());
            return ResponseEntity.ok(response);
        } catch (AuditSendException e) {
            log.error("AuditSendException caught for eventCode={}", request.getEventCode(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AuditResponse("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected exception for eventCode={}", request.getEventCode(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AuditResponse("error", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Дополняет запрос данными HTTP-контекста:
     *  - recipientIp = HttpServletRequest::getLocalAddr;
     *  - traceId/spanId — из W3C-заголовка traceparent (позиции 2 и 3).
     *
     * Если значения уже заданы в теле запроса, они не перезаписываются.
     */
    private void enrichRequestFromHttpContext(AuditRequest request, HttpServletRequest httpRequest) {
        if (httpRequest == null) {
            return;
        }

        try {
            if (request.getRecipientIp() == null) {
                request.setRecipientIp(httpRequest.getLocalAddr());
            }
        } catch (Exception e) {
            log.warn("Failed to extract recipientIp from HttpServletRequest", e);
        }

        String traceparent = httpRequest.getHeader(HEADER_TRACEPARENT);
        if (traceparent != null && !traceparent.isEmpty()) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 4) {
                if (request.getTraceId() == null) {
                    request.setTraceId(parts[1]);
                }
                if (request.getSpanId() == null) {
                    request.setSpanId(parts[2]);
                }
            } else {
                log.warn("Invalid traceparent header format: {}", traceparent);
            }
        }
    }
}