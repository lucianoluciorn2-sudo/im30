package br.com.jetinopay.mdb

/**
 * Estados da máquina de estados MDB (Cashless Device, Nível 1 e 2).
 * Conforme especificação MDB/ICP v4.3 — Figura 7-1 (State Diagram).
 */
sealed class MdbState {

    /** Terminal acabou de ligar ou recebeu RESET. Aguarda SETUP do VMC. */
    object Inactive : MdbState() {
        override fun toString() = "INACTIVE"
    }

    /**
     * VMC enviou SETUP CONFIG DATA. Terminal respondeu READER CONFIG DATA.
     * Reader está desabilitado — VMC ainda não enviou READER ENABLE.
     */
    object Disabled : MdbState() {
        override fun toString() = "DISABLED"
    }

    /**
     * VMC enviou READER ENABLE. Terminal está pronto para detectar comprador.
     * Em integração real com a Jetinno: aguardando seleção de produto.
     */
    object Enabled : MdbState() {
        override fun toString() = "ENABLED"
    }

    /**
     * Sessão iniciada (BEGIN SESSION enviado ao VMC).
     * Aguardando VEND REQUEST com o valor do produto.
     */
    object SessionIdle : MdbState() {
        override fun toString() = "SESSION_IDLE"
    }

    /**
     * VMC enviou VEND REQUEST com o valor do produto.
     * O app JetinoPay está processando o pagamento via m-SiTef.
     *
     * @param valorCentavos Valor solicitado pela Jetinno (em centavos)
     * @param itemNumber    Número do item/produto selecionado
     */
    data class Vend(
        val valorCentavos: Int,
        val itemNumber: Int
    ) : MdbState() {
        override fun toString() = "VEND(valor=${valorCentavos}c, item=$itemNumber)"
    }

    /**
     * Revalue solicitado pelo VMC (crédito de devolução).
     * Suportado em Nível 2.
     */
    object Revalue : MdbState() {
        override fun toString() = "REVALUE"
    }

    /**
     * Erro de comunicação serial ou frame inválido detectado.
     * O terminal retorna ao estado Inactive após enviar ERROR.
     *
     * @param motivo Descrição do erro para log
     */
    data class Erro(val motivo: String) : MdbState() {
        override fun toString() = "ERRO($motivo)"
    }
}
