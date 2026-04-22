package br.com.jetinopay.network.models

import com.google.gson.annotations.SerializedName

data class CreatePaymentRequest(
    @SerializedName("valor") val valor: Double,
    @SerializedName("tipo") val tipo: String
)

data class PaymentResult(
    @SerializedName("codigoAutorizacao") val codigoAutorizacao: String?,
    @SerializedName("nsu") val nsu: String?,
    @SerializedName("bandeira") val bandeira: String?,
    @SerializedName("modoEntrada") val modoEntrada: String?,
    @SerializedName("mensagem") val mensagem: String?
)

data class Transaction(
    @SerializedName("id") val id: String,
    @SerializedName("valor") val valor: Double,
    @SerializedName("tipo") val tipo: String,
    @SerializedName("status") val status: String,
    @SerializedName("resultado") val resultado: PaymentResult?,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
)

data class CreatePaymentResponse(
    @SerializedName("mensagem") val mensagem: String,
    @SerializedName("transacao") val transacao: Transaction
)

data class StatusResponse(
    @SerializedName("transacao") val transacao: Transaction
)

data class WebhookRequest(
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: String,
    @SerializedName("mensagem") val mensagem: String? = null
)

data class WebhookResponse(
    @SerializedName("mensagem") val mensagem: String,
    @SerializedName("transacao") val transacao: Transaction
)
