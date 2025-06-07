package com.eipresso.product.routes;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.springframework.stereotype.Component;

/**
 * Product API Route - Content-Based Router Pattern Implementation
 * 
 * EIP Pattern: Content-Based Router
 * Purpose: REST API with VIP customer routing and request-based routing
 * Clustering: Active-Active compatible (stateless API routing)
 */
@Component
public class ProductApiRoute extends RouteBuilder {
    
    @Override
    public void configure() throws Exception {
        
        // REST configuration
        restConfiguration()
            .component("servlet")
            .port(8082)
            .host("0.0.0.0")
            .bindingMode(RestBindingMode.json)
            .dataFormatProperty("prettyPrint", "true")
            .enableCORS(true)
            .contextPath("/api/v1");
        
        // Global error handling
        errorHandler(deadLetterChannel("direct:api-error-handler")
            .maximumRedeliveries(2)
            .redeliveryDelay(1000)
            .logStackTrace(true));
        
        /**
         * REST API Definition
         */
        rest("/products")
            .description("Product Catalog REST API with Content-Based Routing")
            
            // GET /products - List products with routing based on user type
            .get()
                .description("Get all products with VIP routing")
                .param().name("limit").type(RestParamType.query).description("Max results").endParam()
                .param().name("category").type(RestParamType.query).description("Product category").endParam()
                .to("direct:api-get-products")
            
            // GET /products/{id} - Get specific product
            .get("/{id}")
                .description("Get product by ID")
                .param().name("id").type(RestParamType.path).description("Product ID").endParam()
                .to("direct:api-get-product-by-id");
        
        /**
         * Route 1: Get Products - Content-Based Router Pattern
         * Routes VIP customers to high-performance endpoints
         */
        from("direct:api-get-products")
            .routeId("api-get-products")
            .description("Content-Based Router: Product list with VIP routing")
            .log("🔍 API: Getting products list")
            
            // Extract headers for routing decisions
            .process(exchange -> {
                String userType = exchange.getIn().getHeader("X-User-Type", String.class);
                String apiKey = exchange.getIn().getHeader("X-API-Key", String.class);
                String limit = exchange.getIn().getHeader("limit", String.class);
                String category = exchange.getIn().getHeader("category", String.class);
                
                // Set defaults
                if (limit == null) limit = "10";
                if (category == null) category = "ALL";
                
                // Determine user priority
                boolean isVip = "VIP".equals(userType) || "PREMIUM".equals(userType);
                boolean hasApiKey = apiKey != null && !apiKey.isEmpty();
                
                exchange.getIn().setHeader("IsVIP", isVip);
                exchange.getIn().setHeader("HasApiKey", hasApiKey);
                exchange.getIn().setHeader("Limit", limit);
                exchange.getIn().setHeader("Category", category);
            })
            
            // Content-Based Routing based on user type
            .choice()
                .when(header("IsVIP").isEqualTo(true))
                    .log("👑 VIP customer detected - routing to premium service")
                    .to("direct:get-products-vip")
                .when(header("HasApiKey").isEqualTo(true))
                    .log("🔑 API key customer - routing to API service")
                    .to("direct:get-products-api")
                .otherwise()
                    .log("👤 Regular customer - routing to standard service")
                    .to("direct:get-products-standard")
            .end();
        
        /**
         * Route 2: Get Product by ID
         */
        from("direct:api-get-product-by-id")
            .routeId("api-get-product-by-id")
            .description("Get single product with caching")
            .log("🔍 API: Getting product ${header.id}")
            
            .setHeader("productId", header("id"))
            .to("direct:product-cache-get")
            
            .choice()
                .when(body().isNotNull())
                    .log("✅ Product ${header.id} found")
                .otherwise()
                    .setHeader("CamelHttpResponseCode", constant(404))
                    .setBody(constant("{\"error\":\"Product not found\"}"))
                    .log("❌ Product ${header.id} not found")
            .end();
        
        /**
         * VIP Service Routes - Higher performance/priority
         */
        from("direct:get-products-vip")
            .routeId("get-products-vip")
            .description("VIP product service with premium features")
            .log("👑 VIP Service: Getting products with premium features")
            
            .process(exchange -> {
                String vipProducts = 
                    "[{\"id\":\"vip-1\",\"name\":\"Exclusive Blend\",\"price\":19.99,\"vipOnly\":true}," +
                    "{\"id\":\"vip-2\",\"name\":\"Premium Roast\",\"price\":24.99,\"vipOnly\":true}]";
                
                exchange.getIn().setBody(vipProducts);
                exchange.getIn().setHeader("ProductCount", 2);
            })
            
            .log("👑 VIP products returned: ${header.ProductCount} items");
        
        from("direct:get-products-api")
            .routeId("get-products-api")
            .description("API customer service")
            .log("🔑 API Service: Getting products for API customer")
            
            .process(exchange -> {
                String apiProducts = 
                    "[{\"id\":\"api-1\",\"name\":\"Standard Blend\",\"price\":9.99}," +
                    "{\"id\":\"api-2\",\"name\":\"House Roast\",\"price\":11.99}]";
                
                exchange.getIn().setBody(apiProducts);
                exchange.getIn().setHeader("ProductCount", 2);
            })
            
            .log("🔑 API products returned: ${header.ProductCount} items");
        
        from("direct:get-products-standard")
            .routeId("get-products-standard")
            .description("Standard customer service")
            .log("👤 Standard Service: Getting products for regular customer")
            
            .process(exchange -> {
                String standardProducts = 
                    "[{\"id\":\"std-1\",\"name\":\"Regular Coffee\",\"price\":7.99}," +
                    "{\"id\":\"std-2\",\"name\":\"Daily Roast\",\"price\":8.99}]";
                
                exchange.getIn().setBody(standardProducts);
                exchange.getIn().setHeader("ProductCount", 2);
            })
            
            .log("👤 Standard products returned: ${header.ProductCount} items");
        
        /**
         * API Error Handler
         */
        from("direct:api-error-handler")
            .routeId("api-error-handler")
            .description("API error handling")
            .log("❌ API error: ${exception.message}")
            
            .setHeader("CamelHttpResponseCode", constant(500))
            .setBody(constant("{\"error\":\"Internal server error\"}"))
            
            .log("💾 API error logged and response sent");
    }
} 