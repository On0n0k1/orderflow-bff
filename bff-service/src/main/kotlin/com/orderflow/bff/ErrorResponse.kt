package com.orderflow.bff

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String)
