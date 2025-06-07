package com.eipresso.product.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Product Recipient List Route - Recipient List Pattern Implementation
 * 
 * EIP Pattern: Recipient List
 * Purpose: Dynamic routing based on product categories and regional availability
 * Clustering: Active-Active compatible (stateless routing logic)
 * 
 * Routes:
 * 1. product-route-request: Main routing entry point
 * 2. calculate-recipients: Determine target services based on product data
 * 3. regional-routing: Route based on geographic availability  
 * 4. category-routing: Route based on product categories
 * 5. availability-routing: Route based on stock availability
 */
@Component
public class ProductRecipientListRoute extends RouteBuilder {
    
    @Override
    public void configure() throws Exception {
        
        // Error handling for routing operations
        errorHandler(deadLetterChannel("direct:routing-error-handler")
            .maximumRedeliveries(2)
            .redeliveryDelay(1500)
            .logStackTrace(true));
        
        /**
         * Route 1: Product Route Request - Main Entry Point
         * Input: Product message with routing context
         * Purpose: Analyze product and determine appropriate recipients
         */
        from("direct:product-route-request")
            .routeId("product-route-request")
            .description("Recipient List: Main product routing entry point")
            .log("🎯 Processing routing request for product ${header.productId}")
            
            // Enrich with product data if not present
            .choice()
                .when(body().isNull())
                    .log("📦 Fetching product data for routing")
                    .to("direct:product-cache-get")
                .otherwise()
                    .log("📦 Using provided product data for routing")
            .end()
            
            // Calculate recipient list based on product data
            .to("direct:calculate-recipients")
            
            // Execute dynamic routing using recipient list
            .recipientList(header("RecipientList"))
                .parallelProcessing(true)
                .stopOnException()
                .timeout(5000)
            .end()
            
            .log("✅ Product routing completed for ${header.productId}");
        
        /**
         * Route 2: Calculate Recipients - Dynamic Recipient Determination
         * Input: Product JSON
         * Purpose: Analyze product and build recipient list
         */
        from("direct:calculate-recipients")
            .routeId("calculate-recipients")
            .description("Recipient List: Dynamic recipient calculation")
            .log("🧮 Calculating recipients for product routing")
            
            .process(exchange -> {
                String routingContext = exchange.getIn().getHeader("RoutingContext", String.class);
                String category = exchange.getIn().getHeader("Category", String.class);
                String region = exchange.getIn().getHeader("Region", String.class);
                
                // Set defaults
                if (category == null) category = "COFFEE";
                if (region == null) region = "ALL";
                
                java.util.List<String> recipients = new java.util.ArrayList<>();
                
                // Route based on context
                switch (routingContext != null ? routingContext : "DEFAULT") {
                    case "PRICE_UPDATE":
                        recipients.add("direct:route-to-analytics");
                        recipients.add("direct:route-to-inventory");
                        break;
                    case "STOCK_UPDATE":
                        recipients.add("direct:route-to-analytics");
                        recipients.add("direct:route-to-inventory");
                        recipients.add("direct:category-routing");
                        break;
                    default:
                        recipients.add("direct:route-to-analytics");
                        recipients.add("direct:category-routing");
                }
                
                // Set recipient list header for Camel recipient list
                String recipientListStr = String.join(",", recipients);
                exchange.getIn().setHeader("RecipientList", recipientListStr);
                exchange.getIn().setHeader("RecipientCount", recipients.size());
                
                log.info("🎯 Calculated {} recipients: {}", recipients.size(), recipientListStr);
            })
            
            .log("📋 Recipients calculated: ${header.RecipientCount} endpoints");
        
        /**
         * Route 3: Category-Based Routing Handler
         * Purpose: Route to category-specific services
         */
        from("direct:category-routing")
            .routeId("category-routing")
            .description("Recipient List: Category-based routing")
            .log("📚 Processing category routing for ${header.Category}")
            
            // Route based on product category
            .choice()
                .when(header("Category").in("ESPRESSO", "COFFEE", "TEA"))
                    .to("direct:beverages-endpoint")
                .when(header("Category").in("BAKERY", "SANDWICH"))
                    .to("direct:food-endpoint")
                .otherwise()
                    .to("direct:general-endpoint")
            .end()
            
            .log("✅ Category routing completed for ${header.Category}");
        
        /**
         * Route 4: Service-Specific Routing Endpoints
         * Purpose: Handle routing to specific microservices
         */
        from("direct:route-to-analytics")
            .routeId("route-to-analytics")
            .description("Recipient List: Analytics service routing")
            .setHeader("ServiceTarget", constant("analytics"))
            .to("direct:analytics-routing-endpoint")
            .log("📊 Routed to Analytics service");
        
        from("direct:route-to-inventory")
            .routeId("route-to-inventory") 
            .description("Recipient List: Inventory service routing")
            .setHeader("ServiceTarget", constant("inventory"))
            .to("direct:inventory-routing-endpoint")
            .log("📦 Routed to Inventory service");
        
        from("direct:route-to-notifications")
            .routeId("route-to-notifications")
            .description("Recipient List: Notification service routing")
            .setHeader("ServiceTarget").constant("notifications")
            .to("direct:notifications-routing-endpoint")
            .log("🔔 Routed to Notification service");
        
        from("direct:route-to-marketing")
            .routeId("route-to-marketing")
            .description("Recipient List: Marketing service routing")
            .setHeader("ServiceTarget").constant("marketing")
            .to("direct:marketing-routing-endpoint")
            .log("📢 Routed to Marketing service");
        
        /**
         * Route 5: Routing Error Handler
         * Purpose: Handle routing failures gracefully
         */
        from("direct:routing-error-handler")
            .routeId("routing-error-handler")
            .description("Error Handler: Routing failures")
            .log("❌ Product routing failed: ${exception.message}")
            
            .to("direct:routing-failures-endpoint")
            .log("💾 Routing failure stored for analysis");
        
        // Mock endpoints for routing targets
        from("direct:beverages-endpoint")
            .routeId("beverages-endpoint")
            .description("Mock: Beverages category endpoint")
            .log("☕ Beverages routing: ${body}");
        
        from("direct:food-endpoint")
            .routeId("food-endpoint")
            .description("Mock: Food category endpoint")
            .log("🥪 Food routing: ${body}");
        
        from("direct:general-endpoint")
            .routeId("general-endpoint")
            .description("Mock: General category endpoint")
            .log("📦 General routing: ${body}");
        
        from("direct:analytics-routing-endpoint")
            .routeId("analytics-routing-endpoint")
            .description("Mock: Analytics routing endpoint")
            .log("📊 Analytics routing: ${body}");
        
        from("direct:inventory-routing-endpoint")
            .routeId("inventory-routing-endpoint")
            .description("Mock: Inventory routing endpoint")
            .log("📦 Inventory routing: ${body}");
        
        from("direct:routing-failures-endpoint")
            .routeId("routing-failures-endpoint")
            .description("Mock: Routing failures storage")
            .log("❌ Routing failure: ${body}");
        
        from("direct:notifications-routing-endpoint")
            .routeId("notifications-routing-endpoint")
            .description("Mock: Notifications routing endpoint")
            .log("🔔 Notifications routing: ${body}");
        
        from("direct:marketing-routing-endpoint")
            .routeId("marketing-routing-endpoint")
            .description("Mock: Marketing routing endpoint")
            .log("📢 Marketing routing: ${body}");
    }
} 