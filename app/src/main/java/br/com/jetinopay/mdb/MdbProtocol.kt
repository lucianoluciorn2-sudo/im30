package br.com.jetinopay.mdb

/**
 * Constantes e estruturas do protocolo MDB (Multi-Drop Bus) para dispositivo
 * cashless (Nível 1 e 2), conforme especificação MDB/ICP versão 4.3.
 *
 * O PAX IM30 funciona como DEV (Cashless Device).
 * A Jetinno funciona como VMC (Vending Machine Controller).
 *
 * Porta serial no IM30: /dev/ttyS4, 9600 baud, 9 bits (modo MDB).
 * A PAX SDK fornece a camada de hardware via MdbDevice; sem o SDK usamos
 * SerialPort diretamente e emulamos o 9º bit via framing de software.
 */
object MdbProtocol {

    // ─── Endereçamento ──────────────────────────────────────────────────────

    /** Endereço MDB do Cashless Device #1 (bit 7..4 = 0001, CMD = 3 bits). */
    const val ADDR_CASHLESS_1 = 0x10

    /** Máscara do bit de modo (9º bit emulado no primeiro byte do frame). */
    const val MODE_BIT = 0x100

    // ─── Comandos VMC → DEV ─────────────────────────────────────────────────

    const val CMD_RESET         = 0x00
    const val CMD_SETUP         = 0x01
    const val CMD_POLL          = 0x02
    const val CMD_VEND          = 0x03
    const val CMD_READER        = 0x04
    const val CMD_REVALUE       = 0x05
    const val CMD_EXPANSION     = 0x07

    // Sub-comandos de VEND
    const val VEND_REQUEST      = 0x00
    const val VEND_CANCEL       = 0x01
    const val VEND_SUCCESS      = 0x02
    const val VEND_FAILURE      = 0x03
    const val VEND_SESSION_CMPL = 0x04
    const val VEND_CASH_SALE    = 0x05

    // Sub-comandos de READER
    const val READER_DISABLE    = 0x00
    const val READER_ENABLE     = 0x01
    const val READER_CANCEL     = 0x02

    // Sub-comandos de SETUP
    const val SETUP_CONFIG_DATA = 0x00
    const val SETUP_MAX_MIN     = 0x01

    // ─── Respostas DEV → VMC ────────────────────────────────────────────────

    const val RSP_ACK                   = 0x00
    const val RSP_JUST_RESET            = 0x00   // também usado como ACK após RESET
    const val RSP_READER_CONFIG_DATA    = 0x01
    const val RSP_DISPLAY_REQUEST       = 0x02
    const val RSP_BEGIN_SESSION         = 0x03
    const val RSP_SESSION_CANCEL        = 0x04
    const val RSP_VEND_APPROVED         = 0x05
    const val RSP_VEND_DENIED           = 0x06
    const val RSP_END_SESSION           = 0x07
    const val RSP_CANCELLED             = 0x08
    const val RSP_PERIPHERAL_ID         = 0x09
    const val RSP_ERROR                 = 0x0A
    const val RSP_DIAGNOSTICS           = 0x0B
    const val RSP_NACK                  = 0xFF

    // ─── Configuração do dispositivo (resposta SETUP CONFIG DATA) ───────────

    /** Recursos suportados: crédito, débito, PIX. */
    const val FEATURE_LEVEL     = 0x02           // Nível 2
    const val COUNTRY_CODE      = 0x0076         // Brasil (076)
    const val SCALE_FACTOR      = 0x01           // 1 centavo por unidade
    const val DECIMAL_PLACES    = 0x02           // 2 casas decimais
    const val MAX_RESP_TIME     = 0x0A           // 10 segundos
    const val MISC_OPTIONS      = 0x06           // Bit 1: suporta saldo, Bit 2: suporta desconto

    // ─── Valores especiais ──────────────────────────────────────────────────

    /** Fundos ilimitados reportados ao VMC no BEGIN SESSION. */
    const val FUNDS_UNLIMITED   = 0xFFFF

    // ─── Dados de identificação periférica ──────────────────────────────────

    const val MANUFACTURER_CODE = "JPY"          // JetinoPay
    const val SERIAL_NUMBER     = "IM3000000001"
    const val MODEL_NUMBER      = "JETINOPAY-1.0"
    const val SOFTWARE_VERSION  = 0x0100         // v1.0

    // ─── Helpers ────────────────────────────────────────────────────────────

    /**
     * Constrói um frame MDB para resposta do DEV.
     * O checksum é a soma de todos os bytes de dados (mod 256).
     */
    fun buildFrame(vararg bytes: Int): ByteArray {
        val data = bytes.map { it.and(0xFF).toByte() }
        val checksum = data.sumOf { it.toInt().and(0xFF) }.and(0xFF)
        return (data + checksum.toByte()).toByteArray()
    }

    /** Calcula o checksum MDB para validar frames recebidos. */
    fun isChecksumValid(frame: ByteArray): Boolean {
        if (frame.size < 2) return false
        val expected = frame.last().toInt().and(0xFF)
        val calculated = frame.dropLast(1).sumOf { it.toInt().and(0xFF) }.and(0xFF)
        return expected == calculated
    }

    /** Extrai valor em centavos de 2 bytes MDB (big-endian). */
    fun valorDe2Bytes(high: Byte, low: Byte): Int =
        (high.toInt().and(0xFF) shl 8) or low.toInt().and(0xFF)

    /** Converte centavos para 2 bytes MDB. */
    fun centavosParaBytes(centavos: Int): Pair<Int, Int> =
        Pair((centavos shr 8).and(0xFF), centavos.and(0xFF))
}

/** Representa um frame MDB decodificado recebido do VMC. */
data class MdbFrame(
    val address: Int,
    val command: Int,
    val subCommand: Int?,
    val data: ByteArray,
    val raw: ByteArray
) {
    val isVend: Boolean get() = command == MdbProtocol.CMD_VEND
    val isPoll: Boolean get() = command == MdbProtocol.CMD_POLL
    val isReset: Boolean get() = command == MdbProtocol.CMD_RESET
    val isSetup: Boolean get() = command == MdbProtocol.CMD_SETUP
    val isReader: Boolean get() = command == MdbProtocol.CMD_READER

    override fun equals(other: Any?) = other is MdbFrame && raw.contentEquals(other.raw)
    override fun hashCode() = raw.contentHashCode()
}
