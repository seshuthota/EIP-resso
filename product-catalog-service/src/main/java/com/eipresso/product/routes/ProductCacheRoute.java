package com.eipresso.product.routes;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

/**
 * Product Cache Route - Cache Pattern Implementation
 * 
 * EIP Pattern: Cache Pattern
 * Purpose: Intelligent caching with TTL management for product data
 * Clustering: Active-Active compatible (cache handles synchronization)
 * 
 * Routes:
 * 1. product-cache-get: Check cache first, fallback to database
 * 2. product-cache-put: Store product in cache with intelligent TTL
 * 3. product-cache-invalidate: Remove specific products from cache
 * 4. product-cache-refresh: Periodic cache refresh for popular products
 */
@Component
public class ProductCacheRoute extends RouteBuilder {
    
    @Override
    public void configure() throws Exception {
        
        // Enable error handling for cache operations
        errorHandler(deadLetterChannel("direct:cache-error-handler")
            .maximumRedeliveries(3)
            .redeliveryDelay(1000)
            .logStackTrace(true));
        
        /**
         * Route 1: Product Cache GET - Cache Pattern Implementation
         * Input: productId in header
         * Output: Product JSON or empty if not found
         * 
         * Flow: Cache Check -> Database (if cache miss) -> Update Cache
         */
        from("direct:product-cache-get")
            .routeId("product-cache-get")
            .description("Cache Pattern: Intelligent product retrieval with caching")
            .log("🔍 Cache GET: Looking for product ${header.productId}")
            
            // Try cache first (using simple in-memory cache for demo)
            .setHeader("CacheKey").simple("product:${header.productId}")
            .to("direct:check-cache")
            
            // Check if cache hit or miss
            .choice()
                .when(body().isNotNull())
                    .log("✅ Cache HIT for product ${header.productId}")
                    .setHeader("CacheHit").constant(true)
                .endChoice()
                .otherwise()
                    .log("❌ Cache MISS for product ${header.productId} - Fetching from database")
                    .setHeader("CacheHit").constant(false)
                    
                    // Fetch from database (mock for now)
                    .to("direct:get-from-database")
                    
                    // Store in cache with TTL
                    .to("direct:product-cache-put")
                .end()
            
            .log("📦 Returning product ${header.productId} (Cache: ${header.CacheHit})");
        
        /**
         * Route 2: Product Cache PUT - Intelligent TTL Management
         * Input: Product JSON in body, productId in header
         * Purpose: Store product with dynamic TTL based on popularity
         */
        from("direct:product-cache-put")
            .routeId("product-cache-put")
            .description("Cache Pattern: Store product with intelligent TTL")
            .log("💾 Cache PUT: Storing product ${header.productId}")
            
            // Calculate TTL based on product characteristics
            .process(exchange -> {
                String productJson = exchange.getIn().getBody(String.class);
                String productId = exchange.getIn().getHeader("productId", String.class);
                
                // Default TTL: 1 hour (3600 seconds)
                int ttl = 3600;
                
                // Check if product is featured/popular (longer TTL)
                if (productJson != null && productJson.contains("\"featured\":true")) {
                    ttl = 7200; // 2 hours for featured products
                }
                
                // Set TTL in header for cache
                exchange.getIn().setHeader("TTL", ttl);
                exchange.getIn().setHeader("CacheKey", "product:" + productId);
            })
            
            // Store in cache (using direct endpoint for demo)
            .to("direct:store-in-cache")
            .log("✅ Cached product ${header.productId} with TTL ${header.TTL} seconds");
        
        /**
         * Route 3: Cache Invalidation - Remove specific products
         * Input: productId in header
         * Purpose: Remove product from cache when updated
         */
        from("direct:product-cache-invalidate")
            .routeId("product-cache-invalidate")
            .description("Cache Pattern: Invalidate specific product cache")
            .log("🗑️ Cache INVALIDATE: Removing product ${header.productId}")
            
            .setHeader("CacheKey").simple("product:${header.productId}")
            .to("direct:remove-from-cache")
            
            .log("✅ Invalidated cache for product ${header.productId}");
        
        /**
         * Route 4: Periodic Cache Refresh - Polling Consumer Pattern
         * Purpose: Refresh cache for popular products proactively
         * Frequency: Every 30 minutes
         */
        from("timer://cache-refresh?period=1800000") // 30 minutes
            .routeId("product-cache-refresh")
            .description("Polling Consumer: Proactive cache refresh for popular products")
            .log("🔄 Starting periodic cache refresh for popular products")
            
            // Get list of popular products (mock for now)
            .process(exchange -> {
                // Mock popular product IDs
                java.util.List<String> popularProducts = java.util.Arrays.asList("1", "2", "3", "4", "5");
                exchange.getIn().setBody(popularProducts);
            })
            
            // Split the results and refresh each product
            .split(body())
                .process(exchange -> {
                    String productId = exchange.getIn().getBody(String.class);
                    exchange.getIn().setHeader("productId", productId);
                })
                
                // Invalidate old cache entry
                .to("direct:product-cache-invalidate")
                
                // Fetch fresh data and cache it
                .to("direct:product-cache-get")
                
                .log("🔄 Refreshed cache for popular product ${header.productId}")
            .end()
            
            .log("✅ Completed periodic cache refresh");
        
        /**
         * Route 5: Cache Error Handler - Dead Letter Channel Pattern
         * Purpose: Handle cache operation failures gracefully
         */
        from("direct:cache-error-handler")
            .routeId("cache-error-handler")
            .description("Dead Letter Channel: Handle cache operation failures")
            .log("❌ Cache operation failed: ${exception.message}")
            
            // Log error details
            .process(exchange -> {
                Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                String productId = exchange.getIn().getHeader("productId", String.class);
                
                log.error("Cache operation failed for product {}: {}", productId, 
                         exception != null ? exception.getMessage() : "Unknown error");
                
                // Set error response
                exchange.getIn().setBody("Cache operation failed");
                exchange.getIn().setHeader("CacheError", true);
            })
            
            // Continue processing without cache (degraded mode)
            .log("⚠️ Operating in degraded mode without cache for product ${header.productId}");
        
        // Helper routes for cache operations (mock implementations)
        from("direct:check-cache")
            .routeId("check-cache")
            .description("Helper: Check cache for product")
            .process(exchange -> {
                // Mock cache check - randomly return hit/miss
                if (Math.random() > 0.5) {
                    String productId = exchange.getIn().getHeader("productId", String.class);
                    String mockProduct = String.format("{\"id\":\"%s\",\"name\":\"Cached Product %s\",\"price\":5.99}", 
                                                      productId, productId);
                    exchange.getIn().setBody(mockProduct);
                } else {
                    exchange.getIn().setBody(null);
                }
            });
        
        from("direct:get-from-database")
            .routeId("get-from-database")
            .description("Helper: Get product from database")
            .process(exchange -> {
                String productId = exchange.getIn().getHeader("productId", String.class);
                String mockProduct = String.format(
                    "{\"id\":\"%s\",\"name\":\"Product %s\",\"price\":4.99,\"featured\":true}", 
                    productId, productId);
                exchange.getIn().setBody(mockProduct);
            });
        
        from("direct:store-in-cache")
            .routeId("store-in-cache")
            .description("Helper: Store product in cache")
            .log("📦 Storing ${header.CacheKey} in cache with TTL ${header.TTL}");
        
        from("direct:remove-from-cache")
            .routeId("remove-from-cache")
            .description("Helper: Remove product from cache")
            .log("🗑️ Removing ${header.CacheKey} from cache");
    }
} 