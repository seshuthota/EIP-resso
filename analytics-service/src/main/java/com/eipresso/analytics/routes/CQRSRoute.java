package com.eipresso.analytics.routes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CQRS Pattern Implementation for Analytics Service
 * 
 * EIP Pattern: Command Query Responsibility Segregation (CQRS)
 * Purpose: Separate read and write models for different analytical views
 * Clustering: Active-Active compatible (read model distribution)
 * 
 * Routes:
 * 1. cqrs-command-entry: Command side processing
 * 2. cqrs-query-entry: Query side processing  
 * 3. write-model-processor: Command/write model handling
 * 4. read-model-processor: Query/read model optimization
 * 5. analytical-view-builder: Build optimized analytical views
 * 6. query-optimization-processor: Optimize read queries
 */
@Component
public class CQRSRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        
        // Global error handling for CQRS pattern
        errorHandler(deadLetterChannel("direct:cqrs-dead-letter")
            .maximumRedeliveries(5)
            .redeliveryDelay(1000)
            .retryAttemptedLogLevel(LoggingLevel.WARN)
            .useExponentialBackOff()
            .backOffMultiplier(2)
            .maximumRedeliveryDelay(60000));

        /**
         * Route 1: CQRS Command Entry Point
         */
        from("direct:cqrs-command-entry")
            .routeId("cqrs-command-entry")
            .description("CQRS Pattern: Command side entry point")
            .log("📝 Processing CQRS command: ${header.commandType}")
            
            .process(exchange -> {
                String commandId = UUID.randomUUID().toString();
                exchange.getIn().setHeader("commandId", commandId);
                exchange.getIn().setHeader("commandTimestamp", LocalDateTime.now());
                exchange.getIn().setHeader("cqrsVersion", "1.0");
                
                String commandType = exchange.getIn().getHeader("commandType", String.class);
                exchange.getIn().setHeader("writeModel", true);
                
                log.info("📝 CQRS Command processed: {} [{}]", commandType, commandId);
            })
            
            .to("direct:write-model-processor")
            .to("direct:read-model-sync")
            .log("✅ CQRS command processing completed for ${header.commandId}");

        /**
         * Route 2: CQRS Query Entry Point
         */
        from("direct:cqrs-query-entry")
            .routeId("cqrs-query-entry")
            .description("CQRS Pattern: Query side entry point")
            .log("🔍 Processing CQRS query: ${header.queryType}")
            
            .process(exchange -> {
                String queryId = UUID.randomUUID().toString();
                exchange.getIn().setHeader("queryId", queryId);
                exchange.getIn().setHeader("queryTimestamp", LocalDateTime.now());
                
                String queryType = exchange.getIn().getHeader("queryType", String.class);
                exchange.getIn().setHeader("readModel", true);
                
                log.info("🔍 CQRS Query processed: {} [{}]", queryType, queryId);
            })
            
            .to("direct:query-optimization-processor")
            .to("direct:read-model-processor")
            .log("✅ CQRS query processing completed for ${header.queryId}");

        /**
         * Route 3: Write Model Processor
         */
        from("direct:write-model-processor")
            .routeId("write-model-processor")
            .description("CQRS Pattern: Write model processing")
            .log("✍️ Processing write model operations")
            
            .choice()
                .when(header("commandType").isEqualTo("CREATE_ORDER_ANALYTICS"))
                    .to("direct:order-write-model")
                .when(header("commandType").isEqualTo("UPDATE_USER_METRICS"))
                    .to("direct:user-write-model")
                .when(header("commandType").isEqualTo("CREATE_REVENUE_RECORD"))
                    .to("direct:revenue-write-model")
                .otherwise()
                    .to("direct:generic-write-model")
            .end()
            
            .log("✅ Write model processing completed");

        /**
         * Route 4: Read Model Processor
         */
        from("direct:read-model-processor")
            .routeId("read-model-processor")
            .description("CQRS Pattern: Read model processing")
            .log("📖 Processing read model operations")
            
            .choice()
                .when(header("queryType").isEqualTo("GET_ORDER_ANALYTICS"))
                    .to("direct:order-read-model")
                .when(header("queryType").isEqualTo("GET_USER_DASHBOARD"))
                    .to("direct:user-read-model")
                .when(header("queryType").isEqualTo("GET_REVENUE_REPORT"))
                    .to("direct:revenue-read-model")
                .otherwise()
                    .to("direct:generic-read-model")
            .end()
            
            .log("✅ Read model processing completed");

        /**
         * Route 5: Analytical View Builder
         */
        from("direct:analytical-view-builder")
            .routeId("analytical-view-builder")
            .description("CQRS Pattern: Build optimized analytical views")
            .log("🏗️ Building analytical views")
            
            .process(exchange -> {
                String viewType = exchange.getIn().getHeader("viewType", String.class);
                LocalDateTime buildTimestamp = LocalDateTime.now();
                
                Map<String, Object> analyticalView = new HashMap<>();
                analyticalView.put("viewType", viewType);
                analyticalView.put("buildTimestamp", buildTimestamp);
                analyticalView.put("optimized", true);
                
                exchange.getIn().setBody(analyticalView);
                exchange.getIn().setHeader("viewBuilt", true);
                
                log.info("🏗️ Analytical view built: {} at {}", viewType, buildTimestamp);
            })
            
            .multicast()
                .parallelProcessing(true)
                .to("direct:view-cache-updater",
                   "direct:view-index-builder")
            .end()
            
            .log("✅ Analytical view building completed");

        /**
         * Route 6: Query Optimization Processor
         */
        from("direct:query-optimization-processor")
            .routeId("query-optimization-processor")
            .description("CQRS Pattern: Optimize read queries")
            .log("⚡ Optimizing query performance")
            
            .process(exchange -> {
                String queryType = exchange.getIn().getHeader("queryType", String.class);
                String optimization = determineOptimization(queryType);
                
                exchange.getIn().setHeader("optimizationStrategy", optimization);
                exchange.getIn().setHeader("cacheEnabled", true);
                exchange.getIn().setHeader("indexHint", getIndexHint(queryType));
                
                log.info("⚡ Query optimization applied: {} -> {}", queryType, optimization);
            })
            
            .choice()
                .when(header("optimizationStrategy").isEqualTo("CACHE_FIRST"))
                    .to("direct:cache-query-processor")
                .when(header("optimizationStrategy").isEqualTo("INDEX_OPTIMIZED"))
                    .to("direct:index-query-processor")
                .otherwise()
                    .to("direct:standard-query-processor")
            .end()
            
            .log("✅ Query optimization completed");

        // Specialized Write Model Routes
        from("direct:order-write-model")
            .routeId("order-write-model")
            .description("CQRS: Order write model")
            .log("🛒 Processing order write model")
            .process(exchange -> {
                Map<String, Object> orderData = new HashMap<>();
                orderData.put("orderId", exchange.getIn().getHeader("orderId"));
                orderData.put("writeTimestamp", LocalDateTime.now());
                orderData.put("modelType", "order-write");
                exchange.getIn().setBody(orderData);
            })
            .to("elasticsearch:orders-write?operation=INDEX&indexName=orders-write-model")
            .log("✅ Order write model processed");

        from("direct:user-write-model")
            .routeId("user-write-model")
            .description("CQRS: User write model")
            .log("👤 Processing user write model")
            .process(exchange -> {
                Map<String, Object> userData = new HashMap<>();
                userData.put("userId", exchange.getIn().getHeader("userId"));
                userData.put("writeTimestamp", LocalDateTime.now());
                userData.put("modelType", "user-write");
                exchange.getIn().setBody(userData);
            })
            .to("elasticsearch:users-write?operation=INDEX&indexName=users-write-model")
            .log("✅ User write model processed");

        from("direct:revenue-write-model")
            .routeId("revenue-write-model")
            .description("CQRS: Revenue write model")
            .log("💰 Processing revenue write model")
            .process(exchange -> {
                Map<String, Object> revenueData = new HashMap<>();
                revenueData.put("amount", exchange.getIn().getHeader("amount"));
                revenueData.put("writeTimestamp", LocalDateTime.now());
                revenueData.put("modelType", "revenue-write");
                exchange.getIn().setBody(revenueData);
            })
            .to("elasticsearch:revenue-write?operation=INDEX&indexName=revenue-write-model")
            .log("✅ Revenue write model processed");

        // Specialized Read Model Routes
        from("direct:order-read-model")
            .routeId("order-read-model")
            .description("CQRS: Order read model")
            .log("🛒 Processing order read model")
            .to("elasticsearch:orders-read?operation=SEARCH&indexName=orders-read-model")
            .log("✅ Order read model processed");

        from("direct:user-read-model")
            .routeId("user-read-model")
            .description("CQRS: User read model")
            .log("👤 Processing user read model")
            .to("elasticsearch:users-read?operation=SEARCH&indexName=users-read-model")
            .log("✅ User read model processed");

        from("direct:revenue-read-model")
            .routeId("revenue-read-model")
            .description("CQRS: Revenue read model")
            .log("💰 Processing revenue read model")
            .to("elasticsearch:revenue-read?operation=SEARCH&indexName=revenue-read-model")
            .log("✅ Revenue read model processed");

        // Read Model Synchronization
        from("direct:read-model-sync")
            .routeId("read-model-sync")
            .description("CQRS: Synchronize read models")
            .log("🔄 Synchronizing read models")
            .delay(100) // Small delay for eventual consistency
            .to("direct:analytical-view-builder")
            .log("✅ Read model synchronization completed");

        // Cache and Index Optimization Routes
        from("direct:cache-query-processor")
            .routeId("cache-query-processor")
            .log("🚀 Processing cache-optimized query")
            .to("redis:cache-analytics?command=GET")
            .log("✅ Cache query processed");

        from("direct:index-query-processor")
            .routeId("index-query-processor")
            .log("📊 Processing index-optimized query")
            .log("✅ Index query processed");

        from("direct:standard-query-processor")
            .routeId("standard-query-processor")
            .log("🔧 Processing standard query")
            .log("✅ Standard query processed");

        from("direct:view-cache-updater")
            .routeId("view-cache-updater")
            .log("💾 Updating view cache")
            .to("redis:cache-views?command=SET")
            .log("✅ View cache updated");

        from("direct:view-index-builder")
            .routeId("view-index-builder")
            .log("🏗️ Building view indexes")
            .log("✅ View indexes built");

        // Mock Generic Routes
        from("direct:generic-write-model").routeId("generic-write-model")
            .log("🔧 Mock: Generic write model - ${body}");
        
        from("direct:generic-read-model").routeId("generic-read-model")
            .log("🔧 Mock: Generic read model - ${body}");

        /**
         * Dead Letter Channel
         */
        from("direct:cqrs-dead-letter")
            .routeId("cqrs-dead-letter")
            .description("CQRS Pattern: Dead letter channel")
            .log(LoggingLevel.ERROR, "💀 CQRS processing failed: ${exception.message}")
            .process(exchange -> {
                String failureId = UUID.randomUUID().toString();
                exchange.getIn().setHeader("failureId", failureId);
                exchange.getIn().setHeader("failureTimestamp", LocalDateTime.now());
                log.error("💀 CQRS failure logged: {}", failureId);
            })
            .log("💾 CQRS failure logged for analysis");
    }

    // Helper methods
    private String determineOptimization(String queryType) {
        if (queryType != null) {
            if (queryType.contains("DASHBOARD")) return "CACHE_FIRST";
            if (queryType.contains("REPORT")) return "INDEX_OPTIMIZED";
        }
        return "STANDARD";
    }

    private String getIndexHint(String queryType) {
        if (queryType != null) {
            if (queryType.contains("ORDER")) return "orders_timestamp_idx";
            if (queryType.contains("USER")) return "users_activity_idx";
            if (queryType.contains("REVENUE")) return "revenue_date_idx";
        }
        return "default_idx";
    }
} 