package ru.vtb.auditproxy.controller;

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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuditController {
    private final AuditService auditService;
    private final EventCodeValidator eventCodeValidator;

    @PostMapping("/audit")
    public ResponseEntity<AuditResponse> audit(@Valid @RequestBody AuditRequest request) {
        if (!eventCodeValidator.isValid(request.getEventCode())) {
            return ResponseEntity.badRequest()
                    .body(new AuditResponse("error", "Invalid event code: " + request.getEventCode()));
        }

        try {
            AuditResponse response = auditService.sendAuditEvent(request);
            return ResponseEntity.ok(response);
        } catch (AuditSendException e) {
            log.error("AuditSendException caught", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AuditResponse("error", e.getMessage()));
        }
    }
}