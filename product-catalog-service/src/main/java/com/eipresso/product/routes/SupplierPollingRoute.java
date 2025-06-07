package com.eipresso.product.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Supplier Polling Route - Polling Consumer Pattern Implementation
 * 
 * EIP Pattern: Polling Consumer
 * Purpose: Regular price updates from supplier feeds and inventory monitoring
 * Clustering: Active-Active compatible (with coordination via Hazelcast)
 */
@Component
public class SupplierPollingRoute extends RouteBuilder {
    
    @Override
    public void configure() throws Exception {
        
        // Error handling for polling operations
        errorHandler(deadLetterChannel("direct:polling-error-handler")
            .maximumRedeliveries(3)
            .redeliveryDelay(5000)
            .logStackTrace(true));
        
        /**
         * Route 1: Supplier Price Polling - Main Polling Consumer
         * Purpose: Poll external supplier feeds every 15 minutes
         * Pattern: Polling Consumer with coordination
         */
        from("timer://supplier-price-poll?period=900000") // 15 minutes
            .routeId("supplier-price-polling")
            .description("Polling Consumer: Regular supplier price feed polling")
            .log("🔄 Starting supplier price polling cycle")
            
            // Coordinate polling across cluster nodes using simple logic
            .process(exchange -> {
                // Simple coordination - in real implementation use Hazelcast
                boolean shouldPoll = Math.random() > 0.5; // Simulate cluster coordination
                exchange.getIn().setHeader("ShouldPoll", shouldPoll);
            })
            
            .choice()
                .when(header("ShouldPoll").isEqualTo(true))
                    .log("🔒 Acquired polling lock - proceeding with supplier polling")
                    
                    // Poll multiple supplier feeds
                    .to("direct:poll-supplier-a")
                    .to("direct:poll-supplier-b")
                    .to("direct:poll-supplier-c")
                    
                    .log("✅ Supplier polling completed")
                .otherwise()
                    .log("ℹ️ Another node is handling supplier polling - skipping")
            .end();
        
        /**
         * Route 2: Individual Supplier Polling Routes
         */
        from("direct:poll-supplier-a")
            .routeId("poll-supplier-a")
            .description("Polling Consumer: Supplier A price feed")
            .log("📡 Polling Supplier A price feed")
            
            .setHeader("SupplierName", constant("SupplierA"))
            .setHeader("FeedUrl", constant("http://supplier-a.com/api/prices"))
            .setBody(constant("Mock Supplier A Data"))
            
            .to("direct:process-supplier-feed");
        
        from("direct:poll-supplier-b")
            .routeId("poll-supplier-b")
            .description("Polling Consumer: Supplier B price feed")
            .log("📡 Polling Supplier B price feed")
            
            .setHeader("SupplierName", constant("SupplierB"))
            .setBody(constant("Mock Supplier B Data"))
            
            .to("direct:process-supplier-feed");
        
        from("direct:poll-supplier-c")
            .routeId("poll-supplier-c")
            .description("Polling Consumer: Supplier C price feed")
            .log("📡 Polling Supplier C price feed")
            
            .setHeader("SupplierName", constant("SupplierC"))
            .setBody(constant("Mock Supplier C Data"))
            
            .to("direct:process-supplier-feed");
        
        /**
         * Route 3: Process Supplier Feed
         */
        from("direct:process-supplier-feed")
            .routeId("process-supplier-feed")
            .description("Polling Consumer: Process supplier feed data")
            .log("🔄 Processing feed from ${header.SupplierName}")
            
            .process(exchange -> {
                String supplierName = exchange.getIn().getHeader("SupplierName", String.class);
                
                // Mock price recommendation
                String recommendation = String.format(
                    "{\"productId\":\"1\",\"supplierName\":\"%s\",\"currentPrice\":5.99," +
                    "\"recommendedPrice\":4.99,\"timestamp\":\"%s\"," +
                    "\"eventType\":\"SUPPLIER_PRICE_RECOMMENDATION\"}",
                    supplierName, java.time.LocalDateTime.now().toString()
                );
                
                exchange.getIn().setBody(recommendation);
            })
            
            .to("direct:pricing-recommendations-endpoint")
            
            .log("✅ Processed supplier feed from ${header.SupplierName}");
        
        /**
         * Route 4: Inventory Level Polling
         */
        from("timer://inventory-poll?period=300000") // 5 minutes
            .routeId("inventory-level-polling")
            .description("Polling Consumer: Monitor inventory levels")
            .log("📦 Starting inventory level polling")
            
            .process(exchange -> {
                // Mock low stock alert
                String alert = String.format(
                    "{\"productId\":\"1\",\"productName\":\"Espresso Blend\",\"stockQuantity\":3," +
                    "\"minStockLevel\":5,\"timestamp\":\"%s\",\"alertType\":\"LOW_STOCK\"}",
                    java.time.LocalDateTime.now().toString()
                );
                
                exchange.getIn().setBody(alert);
            })
            
            .to("direct:inventory-alerts-endpoint")
            
            .log("✅ Inventory level polling completed");
        
        /**
         * Route 5: Polling Error Handler
         */
        from("direct:polling-error-handler")
            .routeId("polling-error-handler")
            .description("Error Handler: Polling operation failures")
            .log("❌ Polling operation failed: ${exception.message}")
            
            .to("direct:polling-failures-endpoint")
            .log("💾 Polling failure logged");
        
        // Mock endpoints
        from("direct:pricing-recommendations-endpoint")
            .routeId("pricing-recommendations-endpoint")
            .description("Mock: Pricing recommendations storage")
            .log("💰 Pricing recommendation: ${body}");
        
        from("direct:inventory-alerts-endpoint")
            .routeId("inventory-alerts-endpoint")
            .description("Mock: Inventory alerts endpoint")
            .log("📦 Inventory alert: ${body}");
        
        from("direct:polling-failures-endpoint")
            .routeId("polling-failures-endpoint")
            .description("Mock: Polling failures storage")
            .log("❌ Polling failure: ${body}");
    }
} 