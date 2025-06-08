package com.eipresso.payment.routes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wire Tap Pattern Implementation for Payment Service
 * 
 * EIP Pattern: Wire Tap
 * Purpose: Comprehensive audit trail for all financial transactions
 * Clustering: Active-Passive compatible (audit consistency)
 * 
 * Routes:
 * 1. payment-wire-tap-entry: Main wire tap entry point
 * 2. transaction-audit-processor: Process transaction audit records
 * 3. fraud-monitoring-processor: Real-time fraud monitoring
 * 4. compliance-audit-processor: Compliance and regulatory audit
 * 5. security-audit-processor: Security event audit
 * 6. business-metrics-processor: Business intelligence metrics
 */
@Component
public class WireTapRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        
        // Global error handling for Wire Tap pattern
        errorHandler(deadLetterChannel("direct:wire-tap-dead-letter")
            .maximumRedeliveries(3)
            .redeliveryDelay(1000)
            .retryAttemptedLogLevel(LoggingLevel.WARN)
            .useExponentialBackOff()
            .backOffMultiplier(2)
            .maximumRedeliveryDelay(10000));

        /**
         * Route 1: Payment Wire Tap Entry Point
         */
        from("direct:payment-wire-tap-entry")
            .routeId("payment-wire-tap-entry")
            .description("Wire Tap Pattern: Main wire tap entry point")
            .log("📡 Wire tapping payment transaction: ${header.paymentId}")
            
            .process(exchange -> {
                String auditId = UUID.randomUUID().toString();
                exchange.getIn().setHeader("auditId", auditId);
                exchange.getIn().setHeader("auditTimestamp", LocalDateTime.now());
                exchange.getIn().setHeader("wireTapEnabled", true);
                
                String paymentId = exchange.getIn().getHeader("paymentId", String.class);
                String transactionType = exchange.getIn().getHeader("transactionType", String.class);
                
                log.info("📡 Wire tap audit initiated: {} for payment {}", auditId, paymentId);
            })
            
            // Wire tap to multiple audit processors simultaneously
            .wireTap("direct:transaction-audit-processor")
            .wireTap("direct:fraud-monitoring-processor")
            .wireTap("direct:compliance-audit-processor")
            .wireTap("direct:security-audit-processor")
            .wireTap("direct:business-metrics-processor")
            
            .log("✅ Wire tap processing completed for payment ${header.paymentId}");

        /**
         * Route 2: Transaction Audit Processor
         */
        from("direct:transaction-audit-processor")
            .routeId("transaction-audit-processor")
            .description("Wire Tap Pattern: Transaction audit processing")
            .log("💰 Processing transaction audit: ${header.paymentId}")
            
            .process(exchange -> {
                String paymentId = exchange.getIn().getHeader("paymentId", String.class);
                String auditId = exchange.getIn().getHeader("auditId", String.class);
                
                Map<String, Object> transactionAudit = new HashMap<>();
                transactionAudit.put("auditId", auditId);
                transactionAudit.put("paymentId", paymentId);
                transactionAudit.put("auditType", "TRANSACTION");
                transactionAudit.put("timestamp", LocalDateTime.now());
                transactionAudit.put("amount", exchange.getIn().getHeader("amount"));
                transactionAudit.put("gateway", exchange.getIn().getHeader("paymentGateway"));
                transactionAudit.put("method", exchange.getIn().getHeader("paymentMethod"));
                transactionAudit.put("status", exchange.getIn().getHeader("paymentStatus"));
                transactionAudit.put("userId", exchange.getIn().getHeader("userId"));
                transactionAudit.put("orderId", exchange.getIn().getHeader("orderId"));
                
                // Add transaction context
                transactionAudit.put("ipAddress", exchange.getIn().getHeader("customerIp"));
                transactionAudit.put("userAgent", exchange.getIn().getHeader("userAgent"));
                transactionAudit.put("correlationId", exchange.getIn().getHeader("correlationId"));
                
                exchange.getIn().setBody(transactionAudit);
                
                log.info("💰 Transaction audit record created: {} for payment {}", auditId, paymentId);
            })
            
            .to("mock:transaction-audit")
            .log("✅ Transaction audit processing completed");

        /**
         * Route 3: Fraud Monitoring Processor
         */
        from("direct:fraud-monitoring-processor")
            .routeId("fraud-monitoring-processor")
            .description("Wire Tap Pattern: Real-time fraud monitoring")
            .log("🚨 Processing fraud monitoring: ${header.paymentId}")
            
            .process(exchange -> {
                String paymentId = exchange.getIn().getHeader("paymentId", String.class);
                
                Map<String, Object> fraudMonitoring = new HashMap<>();
                fraudMonitoring.put("paymentId", paymentId);
                fraudMonitoring.put("auditType", "FRAUD_MONITORING");
                fraudMonitoring.put("timestamp", LocalDateTime.now());
                
                // Fraud detection parameters
                fraudMonitoring.put("amount", exchange.getIn().getHeader("amount"));
                fraudMonitoring.put("customerCountry", exchange.getIn().getHeader("customerCountry", "US"));
                fraudMonitoring.put("transactionTime", LocalDateTime.now().getHour());
                fraudMonitoring.put("paymentMethod", exchange.getIn().getHeader("paymentMethod"));
                fraudMonitoring.put("ipAddress", exchange.getIn().getHeader("customerIp"));
                
                // Calculate basic fraud indicators
                Double amount = exchange.getIn().getHeader("amount", Double.class);
                boolean highValueTransaction = amount != null && amount > 500.0;
                boolean offHours = LocalDateTime.now().getHour() < 6 || LocalDateTime.now().getHour() > 22;
                
                fraudMonitoring.put("highValueTransaction", highValueTransaction);
                fraudMonitoring.put("offHoursTransaction", offHours);
                fraudMonitoring.put("riskScore", calculateBasicRiskScore(highValueTransaction, offHours));
                
                exchange.getIn().setBody(fraudMonitoring);
                
                log.info("🚨 Fraud monitoring record created for payment {}", paymentId);
            })
            
            .choice()
                .when(simple("${body[riskScore]} > 70"))
                    .to("direct:high-risk-fraud-alert")
                .otherwise()
                    .to("direct:standard-fraud-processing")
            .end()
            
            .log("✅ Fraud monitoring processing completed");

        /**
         * Route 4: Compliance Audit Processor
         */
        from("direct:compliance-audit-processor")
            .routeId("compliance-audit-processor")
            .description("Wire Tap Pattern: Compliance and regulatory audit")
            .log("📋 Processing compliance audit: ${header.paymentId}")
            
            .process(exchange -> {
                String paymentId = exchange.getIn().getHeader("paymentId", String.class);
                
                Map<String, Object> complianceAudit = new HashMap<>();
                complianceAudit.put("paymentId", paymentId);
                complianceAudit.put("auditType", "COMPLIANCE");
                complianceAudit.put("timestamp", LocalDateTime.now());
                
                // Compliance tracking
                complianceAudit.put("jurisdiction", determineJurisdiction(exchange));
                complianceAudit.put("regulations", getApplicableRegulations(exchange));
                complianceAudit.put("dataRetentionRequired", true);
                complianceAudit.put("pcidssRequired", isPCIDSSRequired(exchange));
                complianceAudit.put("kycRequired", isKYCRequired(exchange));
                complianceAudit.put("amlRequired", isAMLRequired(exchange));
                
                exchange.getIn().setBody(complianceAudit);
                
                log.info("📋 Compliance audit record created for payment {}", paymentId);
            })
            
            .to("mock:compliance-audit")
            .log("✅ Compliance audit processing completed");

        /**
         * Route 5: Security Audit Processor
         */
        from("direct:security-audit-processor")
            .routeId("security-audit-processor")
            .description("Wire Tap Pattern: Security event audit")
            .log("🔐 Processing security audit: ${header.paymentId}")
            
            .process(exchange -> {
                String paymentId = exchange.getIn().getHeader("paymentId", String.class);
                
                Map<String, Object> securityAudit = new HashMap<>();
                securityAudit.put("paymentId", paymentId);
                securityAudit.put("auditType", "SECURITY");
                securityAudit.put("timestamp", LocalDateTime.now());
                
                // Security event details
                securityAudit.put("encryptionUsed", true);
                securityAudit.put("tlsVersion", "TLS 1.3");
                securityAudit.put("authenticationMethod", "JWT");
                securityAudit.put("ipAddress", exchange.getIn().getHeader("customerIp"));
                securityAudit.put("userAgent", exchange.getIn().getHeader("userAgent"));
                securityAudit.put("sessionId", exchange.getIn().getHeader("sessionId"));
                securityAudit.put("requestId", exchange.getIn().getHeader("correlationId"));
                
                // Security risk assessment
                boolean suspiciousActivity = detectSuspiciousActivity(exchange);
                securityAudit.put("suspiciousActivity", suspiciousActivity);
                securityAudit.put("securityLevel", suspiciousActivity ? "HIGH" : "NORMAL");
                
                exchange.getIn().setBody(securityAudit);
                
                log.info("🔐 Security audit record created for payment {}", paymentId);
            })
            
            .choice()
                .when(simple("${body[suspiciousActivity]} == true"))
                    .to("direct:security-alert-processor")
                .otherwise()
                    .to("direct:standard-security-processing")
            .end()
            
            .log("✅ Security audit processing completed");

        /**
         * Route 6: Business Metrics Processor
         */
        from("direct:business-metrics-processor")
            .routeId("business-metrics-processor")
            .description("Wire Tap Pattern: Business intelligence metrics")
            .log("📊 Processing business metrics: ${header.paymentId}")
            
            .process(exchange -> {
                String paymentId = exchange.getIn().getHeader("paymentId", String.class);
                
                Map<String, Object> businessMetrics = new HashMap<>();
                businessMetrics.put("paymentId", paymentId);
                businessMetrics.put("auditType", "BUSINESS_METRICS");
                businessMetrics.put("timestamp", LocalDateTime.now());
                
                // Business intelligence data
                businessMetrics.put("amount", exchange.getIn().getHeader("amount"));
                businessMetrics.put("currency", exchange.getIn().getHeader("currency", "USD"));
                businessMetrics.put("paymentMethod", exchange.getIn().getHeader("paymentMethod"));
                businessMetrics.put("gateway", exchange.getIn().getHeader("paymentGateway"));
                businessMetrics.put("customerCountry", exchange.getIn().getHeader("customerCountry", "US"));
                businessMetrics.put("timeOfDay", LocalDateTime.now().getHour());
                businessMetrics.put("dayOfWeek", LocalDateTime.now().getDayOfWeek());
                businessMetrics.put("processingTimeMs", System.currentTimeMillis());
                
                exchange.getIn().setBody(businessMetrics);
                
                log.info("📊 Business metrics record created for payment {}", paymentId);
            })
            
                                            .to("log:business-metrics?level=INFO&showBody=true")
            .log("✅ Business metrics processing completed");

        // Specialized Processing Routes
        from("direct:high-risk-fraud-alert")
            .routeId("high-risk-fraud-alert")
            .description("Wire Tap: High-risk fraud alert")
            .log(LoggingLevel.WARN, "🚨 HIGH RISK FRAUD ALERT: Payment ${header.paymentId}")
            .to("mock:fraud-alerts")
            .log("✅ High-risk fraud alert sent");

        from("direct:standard-fraud-processing")
            .routeId("standard-fraud-processing")
            .description("Wire Tap: Standard fraud processing")
            .log("🔍 Standard fraud processing for payment ${header.paymentId}")
            .to("mock:fraud-monitoring")
            .log("✅ Standard fraud processing completed");

        from("direct:security-alert-processor")
            .routeId("security-alert-processor")
            .description("Wire Tap: Security alert processing")
            .log(LoggingLevel.WARN, "🔐 SECURITY ALERT: Payment ${header.paymentId}")
                            .to("log:security-alerts?level=WARN&showBody=true")
            .log("✅ Security alert processed");

        from("direct:standard-security-processing")
            .routeId("standard-security-processing")
            .description("Wire Tap: Standard security processing")
            .log("🔒 Standard security processing for payment ${header.paymentId}")
                            .to("log:security-audit?level=INFO&showBody=true")
            .log("✅ Standard security processing completed");

        /**
         * Dead Letter Channel
         */
        from("direct:wire-tap-dead-letter")
            .routeId("wire-tap-dead-letter")
            .description("Wire Tap Pattern: Dead letter channel")
            .log(LoggingLevel.ERROR, "💀 Wire tap audit failed: ${exception.message}")
            .process(exchange -> {
                String failureId = UUID.randomUUID().toString();
                exchange.getIn().setHeader("failureId", failureId);
                exchange.getIn().setHeader("failureTimestamp", LocalDateTime.now());
                log.error("💀 Wire tap failure logged: {}", failureId);
            })
            .log("💾 Wire tap failure logged for analysis");
    }

    // Helper methods
    private int calculateBasicRiskScore(boolean highValue, boolean offHours) {
        int score = 0;
        if (highValue) score += 40;
        if (offHours) score += 30;
        return Math.min(score, 100);
    }

    private String determineJurisdiction(Exchange exchange) {
        String country = exchange.getIn().getHeader("customerCountry", String.class);
        return country != null ? country : "US";
    }

    private String getApplicableRegulations(Exchange exchange) {
        String country = exchange.getIn().getHeader("customerCountry", String.class);
        if (country == null) country = "US";
        switch (country) {
            case "US": return "PCI-DSS,SOX,CCPA";
            case "GB": return "PCI-DSS,FCA,GDPR";
            case "DE": return "PCI-DSS,BaFin,GDPR";
            default: return "PCI-DSS";
        }
    }

    private boolean isPCIDSSRequired(Exchange exchange) {
        return true; // Always required for payment processing
    }

    private boolean isKYCRequired(Exchange exchange) {
        Double amount = exchange.getIn().getHeader("amount", Double.class);
        return amount != null && amount > 1000.0;
    }

    private boolean isAMLRequired(Exchange exchange) {
        Double amount = exchange.getIn().getHeader("amount", Double.class);
        return amount != null && amount > 10000.0;
    }

    private boolean detectSuspiciousActivity(Exchange exchange) {
        // Basic suspicious activity detection
        String userAgent = exchange.getIn().getHeader("userAgent", String.class);
        String ipAddress = exchange.getIn().getHeader("customerIp", String.class);
        
        return (userAgent != null && userAgent.contains("bot")) ||
               (ipAddress != null && ipAddress.startsWith("10.0."));
    }
} 