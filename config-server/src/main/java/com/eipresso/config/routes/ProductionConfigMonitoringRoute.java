package com.eipresso.config.routes;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production-Ready Configuration Monitoring Routes
 * 
 * Different strategies for monitoring configuration changes in production:
 * 1. Webhook-based monitoring (recommended for production)
 * 2. External Git repository polling via HTTP
 * 3. Message queue-based configuration events
 * 4. Config Server refresh events
 * 
 * Deployment Scenarios:
 * - Docker containers with external Git repos
 * - Kubernetes with ConfigMaps and Secrets
 * - Cloud deployments with managed Git services
 * - Traditional server deployments
 */
@Component
@ConditionalOnProperty(name = "eip-resso.config.monitoring.mode", havingValue = "production")
public class ProductionConfigMonitoringRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        
        // Production error handler with more sophisticated retry logic
        errorHandler(deadLetterChannel("activemq:queue:config-errors")
                .maximumRedeliveries(5)
                .backOffMultiplier(2)
                .maximumRedeliveryDelay(60000)
                .retryAttemptedLogLevel(org.apache.camel.LoggingLevel.WARN));

        // Route 1: Webhook-based Configuration Change Monitoring (RECOMMENDED)
        from("servlet:/config-webhook?servletName=ConfigWebhook")
                .routeId("config-webhook-monitor")
                .log("Configuration webhook received: ${body}")
                
                // Validate webhook source (GitHub, GitLab, Bitbucket)
                .choice()
                    .when(header("X-GitHub-Event").isEqualTo("push"))
                        .to("direct:github-config-change")
                    .when(header("X-GitLab-Event").isEqualTo("Push Hook"))
                        .to("direct:gitlab-config-change")
                    .otherwise()
                        .log("Unknown webhook source, ignoring")
                .end();

        // Route 2: External Git Repository Monitoring via HTTP API
        from("timer://git-api-poll?period=300000") // Every 5 minutes
                .routeId("git-api-monitor")
                .log("Checking external Git repository for changes...")
                .setHeader("Authorization", simple("Bearer ${properties:git.api.token}"))
                .to("https://api.github.com/repos/{{git.repo.owner}}/{{git.repo.name}}/commits?per_page=1")
                .unmarshal().json(JsonLibrary.Jackson)
                .choice()
                    .when(jsonpath("$[0].sha").isNotEqualTo("{{git.last.commit.sha}}"))
                        .log("New commit detected: ${jsonpath($.sha)}")
                        .to("direct:external-git-change")
                    .otherwise()
                        .log("No new commits")
                .end();

        // Route 3: Spring Cloud Bus Integration (for distributed config refresh)
        from("spring-cloud-bus:refresh")
                .routeId("cloud-bus-monitor")
                .log("Cloud Bus refresh event received")
                .to("direct:distributed-config-refresh");

        // Route 4: Config Server Actuator Endpoint Monitoring
        from("timer://actuator-monitor?period=30000") // Every 30 seconds
                .routeId("actuator-config-monitor")
                .to("http://localhost:8888/actuator/env?bridgeEndpoint=true")
                .choice()
                    .when(body().contains("\"configserver:"))
                        .log("Config server environment active")
                        .to("direct:config-health-check")
                    .otherwise()
                        .log("Config server environment issues detected")
                        .to("direct:config-alert")
                .end();

        // Route 5: Message Queue-based Configuration Events
        from("activemq:topic:config.changes")
                .routeId("mq-config-monitor")
                .log("Configuration change message received: ${body}")
                .unmarshal().json(JsonLibrary.Jackson)
                .choice()
                    .when(jsonpath("$.service").isEqualTo("config-server"))
                        .to("direct:config-server-change")
                    .otherwise()
                        .to("direct:service-specific-change")
                .end();

        // Handler Routes for Different Scenarios

        // GitHub Webhook Handler
        from("direct:github-config-change")
                .routeId("github-webhook-handler")
                .log("Processing GitHub webhook")
                .setHeader("GitProvider", constant("GitHub"))
                .setHeader("Repository", jsonpath("$.repository.full_name"))
                .setHeader("CommitSha", jsonpath("$.head_commit.id"))
                .setHeader("CommitMessage", jsonpath("$.head_commit.message"))
                .to("direct:process-git-change");

        // GitLab Webhook Handler
        from("direct:gitlab-config-change")
                .routeId("gitlab-webhook-handler")
                .log("Processing GitLab webhook")
                .setHeader("GitProvider", constant("GitLab"))
                .setHeader("Repository", jsonpath("$.project.path_with_namespace"))
                .setHeader("CommitSha", jsonpath("$.checkout_sha"))
                .setHeader("CommitMessage", jsonpath("$.commits[0].message"))
                .to("direct:process-git-change");

        // Common Git Change Processor
        from("direct:process-git-change")
                .routeId("git-change-processor")
                .log("Processing git change from ${header.GitProvider}: ${header.CommitMessage}")
                
                // Trigger Config Server refresh
                .to("http://localhost:8888/actuator/refresh?bridgeEndpoint=true&httpMethod=POST")
                
                // Notify other services via Cloud Bus
                .multicast()
                    .to("spring-cloud-bus:refresh")
                    .to("direct:config-change-notification")
                    .to("direct:config-audit-production");

        // External Git API Change Handler
        from("direct:external-git-change")
                .routeId("external-git-handler")
                .log("External Git repository change detected")
                .setProperty("newCommitSha", jsonpath("$[0].sha"))
                .setProperty("commitMessage", jsonpath("$[0].commit.message"))
                
                // Update last known commit SHA
                .to("properties-component:setProperty?key=git.last.commit.sha&value=${exchangeProperty.newCommitSha}")
                
                // Trigger refresh
                .to("direct:trigger-config-refresh");

        // Distributed Config Refresh Handler
        from("direct:distributed-config-refresh")
                .routeId("distributed-refresh-handler")
                .log("Distributed configuration refresh triggered")
                .setBody(simple("{\"event\":\"config-refresh\",\"timestamp\":\"${date:now:ISO}\",\"source\":\"cloud-bus\"}"))
                .marshal().json(JsonLibrary.Jackson)
                .to("activemq:topic:config.events");

        // Configuration Health Check
        from("direct:config-health-check")
                .routeId("config-health-checker")
                .log("Configuration server health check passed")
                .setBody(constant("{\"status\":\"healthy\",\"timestamp\":\"${date:now:ISO}\"}"))
                .to("activemq:queue:monitoring.health");

        // Configuration Alert Handler
        from("direct:config-alert")
                .routeId("config-alert-handler")
                .log("Configuration server alert triggered")
                .setBody(simple("{\"alert\":\"config-server-issue\",\"timestamp\":\"${date:now:ISO}\",\"details\":\"Environment check failed\"}"))
                .marshal().json(JsonLibrary.Jackson)
                .to("activemq:queue:alerts.config");

        // Production Audit Trail
        from("direct:config-audit-production")
                .routeId("config-audit-production")
                .log("Production config audit: ${header.GitProvider} - ${header.Repository}")
                .setBody(simple("{\"event\":\"config-change\",\"provider\":\"${header.GitProvider}\",\"repository\":\"${header.Repository}\",\"commit\":\"${header.CommitSha}\",\"message\":\"${header.CommitMessage}\",\"timestamp\":\"${date:now:ISO}\"}"))
                .marshal().json(JsonLibrary.Jackson)
                .to("elasticsearch:config-audit?operation=INDEX&indexName=config-changes");

        // Configuration Change Notification
        from("direct:config-change-notification")
                .routeId("config-notification-prod")
                .log("Sending production configuration change notification")
                .setBody(simple("{\"type\":\"CONFIG_CHANGE\",\"repository\":\"${header.Repository}\",\"commit\":\"${header.CommitSha}\",\"timestamp\":\"${date:now:ISO}\"}"))
                .marshal().json(JsonLibrary.Jackson)
                .to("activemq:topic:notifications.config");
    }
} 