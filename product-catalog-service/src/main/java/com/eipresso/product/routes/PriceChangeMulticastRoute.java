package com.eipresso.product.routes;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Price Change Multicast Route - Multicast Pattern Implementation
 * 
 * EIP Pattern: Multicast
 * Purpose: Broadcast price changes to analytics, notifications, and inventory services
 * Clustering: Active-Active compatible (message broadcasting)
 * 
 * Routes:
 * 1. price-change-detect: Detect price changes and trigger multicast
 * 2. price-change-multicast: Broadcast to multiple services
 * 3. notify-analytics: Send price change to analytics service
 * 4. notify-inventory: Send price change to inventory service  
 * 5. notify-customers: Send price change to notification service
 */
@Component
public class PriceChangeMulticastRoute extends RouteBuilder {
    
    @Override
    public void configure() throws Exception {
        
        // Error handling for multicast operations
        errorHandler(deadLetterChannel("direct:price-change-error")
            .maximumRedeliveries(2)
            .redeliveryDelay(2000)
            .logStackTrace(true));
        
        /**
         * Route 1: Price Change Detection
         * Input: Product update message
         * Purpose: Detect if price has changed and trigger multicast
         */
        from("direct:product-price-update")
            .routeId("price-change-detect")
            .description("Price Change Detection: Monitor for price changes")
            .log("🔍 Checking for price change on product ${header.productId}")
            
            // Get current price from cache/database
            .to("direct:product-cache-get")
            .setHeader("CurrentProduct", body())
            
            // Compare with new price in message
            .process(exchange -> {
                String currentProductJson = exchange.getIn().getHeader("CurrentProduct", String.class);
                String newProductJson = exchange.getIn().getBody(String.class);
                
                // Extract prices (simplified JSON parsing for demo)
                double currentPrice = extractPrice(currentProductJson);
                double newPrice = extractPrice(newProductJson);
                
                if (Math.abs(currentPrice - newPrice) > 0.01) { // Price changed
                    exchange.getIn().setHeader("PriceChanged", true);
                    exchange.getIn().setHeader("OldPrice", currentPrice);
                    exchange.getIn().setHeader("NewPrice", newPrice);
                    exchange.getIn().setHeader("PriceChangeDelta", newPrice - currentPrice);
                    exchange.getIn().setHeader("PriceChangePercent", 
                        ((newPrice - currentPrice) / currentPrice) * 100);
                } else {
                    exchange.getIn().setHeader("PriceChanged", false);
                }
            })
            
            // Only proceed if price changed
            .filter(header("PriceChanged").isEqualTo(true))
                .log("💰 Price changed for product ${header.productId}: ${header.OldPrice} -> ${header.NewPrice} (${header.PriceChangePercent}%)")
                
                // Create price change event
                .process(exchange -> {
                    String productId = exchange.getIn().getHeader("productId", String.class);
                    double oldPrice = exchange.getIn().getHeader("OldPrice", Double.class);
                    double newPrice = exchange.getIn().getHeader("NewPrice", Double.class);
                    double changePercent = exchange.getIn().getHeader("PriceChangePercent", Double.class);
                    
                    // Create price change event JSON
                    String priceChangeEvent = String.format(
                        "{\"productId\":\"%s\",\"oldPrice\":%.2f,\"newPrice\":%.2f," +
                        "\"changePercent\":%.2f,\"timestamp\":\"%s\",\"eventType\":\"PRICE_CHANGE\"}",
                        productId, oldPrice, newPrice, changePercent, 
                        java.time.LocalDateTime.now().toString()
                    );
                    
                    exchange.getIn().setBody(priceChangeEvent);
                })
                
                // Trigger multicast
                .to("direct:price-change-multicast")
            .end();
        
        /**
         * Route 2: Price Change Multicast - Core Multicast Pattern
         * Input: Price change event JSON
         * Purpose: Broadcast to multiple services simultaneously
         */
        from("direct:price-change-multicast")
            .routeId("price-change-multicast")
            .description("Multicast Pattern: Broadcast price changes to multiple services")
            .log("📡 Broadcasting price change: ${body}")
            
            // Multicast to multiple endpoints
            .multicast()
                .parallelProcessing(true)
                .stopOnException()
                .to("direct:notify-analytics",
                   "direct:notify-inventory", 
                   "direct:notify-customers",
                   "direct:update-cache")
            .end()
            
            .log("✅ Price change broadcasted to all services");
        
        /**
         * Route 3: Analytics Notification
         * Purpose: Send price change to analytics service via messaging
         */
        from("direct:notify-analytics")
            .routeId("notify-analytics")
            .description("Multicast Target: Analytics service notification")
            .log("📊 Sending price change to Analytics service")
            
            // Add analytics-specific headers
            .setHeader("AnalyticsEventType", constant("PRICE_CHANGE"))
            .setHeader("ServiceTarget", constant("analytics"))
            
            // Send to analytics (mock endpoint for now)
            .to("direct:analytics-endpoint")
            
            .log("✅ Price change sent to Analytics service");
        
        /**
         * Route 4: Inventory Notification  
         * Purpose: Notify inventory service of price changes for reorder calculations
         */
        from("direct:notify-inventory")
            .routeId("notify-inventory")
            .description("Multicast Target: Inventory service notification")
            .log("📦 Sending price change to Inventory service")
            
            // Add inventory-specific data
            .process(exchange -> {
                String body = exchange.getIn().getBody(String.class);
                String productId = exchange.getIn().getHeader("productId", String.class);
                
                // Enhance message for inventory service
                String inventoryMessage = body.replace("\"eventType\":\"PRICE_CHANGE\"", 
                    "\"eventType\":\"PRICE_CHANGE\",\"requiresReorderCalculation\":true");
                    
                exchange.getIn().setBody(inventoryMessage);
                exchange.getIn().setHeader("InventoryAction", "RECALCULATE_REORDER_POINT");
            })
            
            // Send to inventory (mock endpoint for now)
            .to("direct:inventory-endpoint")
            
            .log("✅ Price change sent to Inventory service");
        
        /**
         * Route 5: Customer Notification
         * Purpose: Notify customers of significant price changes
         */
        from("direct:notify-customers")
            .routeId("notify-customers")
            .description("Multicast Target: Customer notification service")
            .log("🔔 Processing customer notification for price change")
            
            // Only notify for significant changes (>5%)
            .filter(header("PriceChangePercent").convertTo(Double.class).isGreaterThan(5.0))
                .log("📢 Significant price change detected (${header.PriceChangePercent}%) - Notifying customers")
                
                // Add notification-specific data
                .process(exchange -> {
                    double changePercent = exchange.getIn().getHeader("PriceChangePercent", Double.class);
                    String productId = exchange.getIn().getHeader("productId", String.class);
                    
                    String notificationType = changePercent > 0 ? "PRICE_INCREASE" : "PRICE_DECREASE";
                    
                    exchange.getIn().setHeader("NotificationType", notificationType);
                    exchange.getIn().setHeader("NotificationPriority", 
                        Math.abs(changePercent) > 20 ? "HIGH" : "MEDIUM"
                    );
                })
                
                // Send to notification service (mock endpoint for now)
                .to("direct:notification-endpoint")
                
                .log("✅ Customer notification sent for product ${header.productId}")
            .end();
        
        /**
         * Route 6: Cache Update
         * Purpose: Invalidate cache after price change
         */
        from("direct:update-cache")
            .routeId("update-cache-after-price-change")
            .description("Multicast Target: Cache invalidation")
            .log("🗑️ Invalidating cache after price change")
            
            // Invalidate cache for this product
            .to("direct:product-cache-invalidate")
            
            .log("✅ Cache invalidated for product ${header.productId}");
        
        /**
         * Route 7: Price Change Error Handler
         * Purpose: Handle multicast failures gracefully
         */
        from("direct:price-change-error")
            .routeId("price-change-error-handler")
            .description("Error Handler: Price change multicast failures")
            .log("❌ Price change multicast failed: ${exception.message}")
            
            .process(exchange -> {
                Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                String productId = exchange.getIn().getHeader("productId", String.class);
                String recipientList = exchange.getIn().getHeader("RecipientList", String.class);
                
                log.error("Price change multicast failed for product {}: {}", productId, 
                         exception != null ? exception.getMessage() : "Unknown error");
                
                // Store failed event for retry
                exchange.getIn().setHeader("FailedEvent", exchange.getIn().getBody());
                exchange.getIn().setHeader("FailureTimestamp", java.time.LocalDateTime.now().toString());
            })
            
            // Store in dead letter (mock endpoint for now)
            .to("direct:failed-events-endpoint")
            
            .log("💾 Failed price change event stored for manual review");
        
        // Mock endpoints for external services
        from("direct:analytics-endpoint")
            .routeId("analytics-mock-endpoint")
            .description("Mock: Analytics service endpoint")
            .log("📊 Analytics received: ${body}");
        
        from("direct:inventory-endpoint")
            .routeId("inventory-mock-endpoint")
            .description("Mock: Inventory service endpoint")
            .log("📦 Inventory received: ${body}");
        
        from("direct:notification-endpoint")
            .routeId("notification-mock-endpoint")
            .description("Mock: Notification service endpoint")
            .log("🔔 Notification received: ${body}");
        
        from("direct:failed-events-endpoint")
            .routeId("failed-events-mock-endpoint")
            .description("Mock: Failed events storage")
            .log("💾 Failed event stored: ${body}");
    }
    
    /**
     * Helper method to extract price from JSON
     * In real implementation, use proper JSON parsing
     */
    private double extractPrice(String productJson) {
        if (productJson == null) return 0.0;
        
        // Simplified price extraction (use proper JSON parser in production)
        try {
            int priceIndex = productJson.indexOf("\"price\":");
            if (priceIndex != -1) {
                int start = productJson.indexOf(":", priceIndex) + 1;
                int end = productJson.indexOf(",", start);
                if (end == -1) end = productJson.indexOf("}", start);
                
                String priceStr = productJson.substring(start, end).trim();
                return Double.parseDouble(priceStr);
            }
        } catch (Exception e) {
            log.warn("Failed to extract price from JSON: {}", e.getMessage());
        }
        
        return 0.0;
    }
} 