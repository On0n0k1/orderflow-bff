package com.orderflow.bff

import com.orderflow.bff.orderservice.OrderServiceClient
import com.orderflow.bff.orderservice.OrderServiceUnavailableException
import com.orderflow.bff.summary.orderSummaryRoutes
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8081
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}

fun Application.module(orderServiceClient: OrderServiceClient = createDefaultOrderServiceClient()) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    install(StatusPages) {
        exception<OrderServiceUnavailableException> { call, cause ->
            call.respond(HttpStatusCode.BadGateway, ErrorResponse(cause.message ?: "failed to reach order-service"))
        }
    }

    routing {
        get("/health") {
            call.respondText("ok")
        }
        orderSummaryRoutes(orderServiceClient)
    }
}

private fun createDefaultOrderServiceClient(): OrderServiceClient {
    val orderServiceUrl = System.getenv("ORDER_SERVICE_URL") ?: "http://localhost:8080"
    val httpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    return OrderServiceClient(httpClient, orderServiceUrl)
}
