package br.com.jetinopay.mdb

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Gerenciador do protocolo MDB para comunicação com a Jetinno.
 *
 * Implementa a máquina de estados MDB (cashless device, Nível 1).
 * Roda em thread dedicada e despacha callbacks para a Main thread.
 *
 * Fluxo completo:
 *   VMC RESET → SETUP CONFIG → READER ENABLE
 *   → (produto selecionado) → POLL → app envia BEGIN SESSION
 *   → VMC VEND REQUEST → app processa pagamento
 *   → app envia VEND APPROVED / VEND DENIED
 *   → VMC VEND SUCCESS/FAILURE → app envia END SESSION
 *
 * Para testar sem hardware serial, chame [simularVendRequest].
 */
class MdbManager(
    private val listener: MdbListener,
    serialDevice: String = MdbSerialPort.DEFAULT_DEVICE
) {

    companion object {
        private const val TAG = "MdbManager"
        private const val POLL_INTERVAL_MS = 200L
        private const val INICIO_SESSAO_DELAY_MS = 500L
    }

    private val serial = MdbSerialPort(serialDevice)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    private var estadoAtual: MdbState = MdbState.Inactive
    private var pollFuture: Future<*>? = null

    // Flag de sessão de teste (sem hardware MDB real)
    private var modoSimulacao = false

    // ─── Ciclo de vida ───────────────────────────────────────────────────────

    /**
     * Inicializa a comunicação MDB.
     * Tenta abrir a porta serial; se não encontrar hardware, ativa modo simulação.
     */
    fun iniciar() {
        executor.submit {
            try {
                serial.open()
                modoSimulacao = !serial.aberta || !java.io.File(MdbSerialPort.DEFAULT_DEVICE).exists()
                if (modoSimulacao) {
                    Log.w(TAG, "Hardware MDB não encontrado — modo simulação ativo")
                }
                mudarEstado(MdbState.Inactive)
                iniciarLoop()
            } catch (e: IOException) {
                Log.e(TAG, "Falha ao iniciar MDB: ${e.message}")
                modoSimulacao = true
                mudarEstado(MdbState.Inactive)
                iniciarLoop()
            }
        }
    }

    /** Para a comunicação MDB e libera recursos. */
    fun parar() {
        pollFuture?.cancel(true)
        executor.shutdownNow()
        serial.close()
        Log.i(TAG, "MDB parado")
    }

    // ─── Loop de polling ─────────────────────────────────────────────────────

    private fun iniciarLoop() {
        pollFuture = executor.scheduleAtFixedRate(
            ::processarFrame,
            0L,
            POLL_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun processarFrame() {
        if (modoSimulacao) return

        val bytes = serial.lerFrame() ?: return
        if (bytes.isEmpty()) return

        val frame = decodificarFrame(bytes) ?: run {
            Log.w(TAG, "Frame MDB inválido descartado")
            return
        }

        tratarFrame(frame)
    }

    // ─── Decodificação de frames ─────────────────────────────────────────────

    private fun decodificarFrame(raw: ByteArray): MdbFrame? {
        if (raw.size < 2) return null
        if (!MdbProtocol.isChecksumValid(raw)) {
            Log.w(TAG, "Checksum inválido: ${raw.toHex()}")
            return null
        }

        val addrByte = raw[0].toInt().and(0xFF)
        val address = addrByte.and(0xF8)         // 5 bits de endereço
        val command = addrByte.and(0x07)         // 3 bits de comando
        val data = raw.drop(1).dropLast(1).toByteArray()
        val subCommand = if (data.isNotEmpty()) data[0].toInt().and(0xFF) else null

        return MdbFrame(
            address = address,
            command = command,
            subCommand = subCommand,
            data = data,
            raw = raw
        )
    }

    // ─── Máquina de estados ──────────────────────────────────────────────────

    private fun tratarFrame(frame: MdbFrame) {
        Log.d(TAG, "Frame recebido: addr=0x${frame.address.toString(16)} cmd=${frame.command} estado=$estadoAtual")

        when {
            frame.isReset -> tratarReset()

            frame.isSetup && estadoAtual is MdbState.Inactive -> tratarSetup(frame)

            frame.isPoll -> tratarPoll()

            frame.isReader -> tratarReader(frame)

            frame.isVend -> tratarVend(frame)

            else -> enviarAck()
        }
    }

    private fun tratarReset() {
        Log.i(TAG, "RESET recebido do VMC")
        mudarEstado(MdbState.Inactive)
        // Responde JUST RESET após próximo POLL
    }

    private fun tratarSetup(frame: MdbFrame) {
        when (frame.subCommand) {
            MdbProtocol.SETUP_CONFIG_DATA -> {
                Log.i(TAG, "SETUP CONFIG DATA recebido")
                enviarReaderConfigData()
                mudarEstado(MdbState.Disabled)
            }
            MdbProtocol.SETUP_MAX_MIN -> {
                Log.i(TAG, "SETUP MAX/MIN recebido")
                enviarAck()
            }
        }
    }

    private fun tratarPoll() {
        when (val estado = estadoAtual) {
            is MdbState.Inactive -> {
                // Envia JUST RESET para indicar que reiniciou
                val frame = MdbProtocol.buildFrame(MdbProtocol.RSP_JUST_RESET)
                enviarFrame(frame)
                mudarEstado(MdbState.Disabled)
            }
            is MdbState.Disabled, is MdbState.Enabled -> {
                enviarAck()
                notificarStatus()
            }
            is MdbState.SessionIdle -> {
                // Se a sessão está ociosa, o VMC continua fazendo POLL
                enviarAck()
            }
            is MdbState.Vend -> {
                // Pagamento em andamento — continua aguardando
                enviarAck()
            }
            else -> enviarAck()
        }
    }

    private fun tratarReader(frame: MdbFrame) {
        when (frame.subCommand) {
            MdbProtocol.READER_ENABLE -> {
                Log.i(TAG, "READER ENABLE — terminal habilitado pela Jetinno")
                mudarEstado(MdbState.Enabled)
                enviarAck()
                despacharMain { listener.onReaderEnabled() }

                // Inicia sessão após pequeno delay (simula detecção de comprador)
                mainHandler.postDelayed({
                    iniciarSessao()
                }, INICIO_SESSAO_DELAY_MS)
            }
            MdbProtocol.READER_DISABLE -> {
                Log.i(TAG, "READER DISABLE — terminal desabilitado pela Jetinno")
                mudarEstado(MdbState.Disabled)
                enviarAck()
                despacharMain { listener.onReaderDisabled() }
            }
            MdbProtocol.READER_CANCEL -> {
                Log.i(TAG, "READER CANCEL — sessão cancelada pela Jetinno")
                mudarEstado(MdbState.Enabled)
                enviarFrame(MdbProtocol.buildFrame(MdbProtocol.RSP_CANCELLED))
                despacharMain { listener.onSessionCancelled() }
            }
        }
    }

    private fun tratarVend(frame: MdbFrame) {
        when (frame.subCommand) {
            MdbProtocol.VEND_REQUEST -> {
                if (frame.data.size < 5) { enviarAck(); return }
                val valorCentavos = MdbProtocol.valorDe2Bytes(frame.data[1], frame.data[2])
                val itemNumber = MdbProtocol.valorDe2Bytes(frame.data[3], frame.data[4])
                Log.i(TAG, "VEND REQUEST: valor=${valorCentavos}c item=$itemNumber")
                mudarEstado(MdbState.Vend(valorCentavos, itemNumber))
                despacharMain { listener.onVendRequest(valorCentavos, itemNumber) }
            }
            MdbProtocol.VEND_CANCEL -> {
                Log.i(TAG, "VEND CANCEL recebido")
                mudarEstado(MdbState.SessionIdle)
                enviarFrame(MdbProtocol.buildFrame(MdbProtocol.RSP_CANCELLED))
                despacharMain { listener.onSessionCancelled() }
            }
            MdbProtocol.VEND_SUCCESS -> {
                Log.i(TAG, "VEND SUCCESS — produto dispensado pela Jetinno")
                mudarEstado(MdbState.SessionIdle)
                enviarAck()
            }
            MdbProtocol.VEND_FAILURE -> {
                Log.i(TAG, "VEND FAILURE — dispensação falhou")
                mudarEstado(MdbState.SessionIdle)
                enviarAck()
            }
            MdbProtocol.VEND_SESSION_CMPL -> {
                Log.i(TAG, "SESSION COMPLETE")
                mudarEstado(MdbState.Enabled)
                enviarFrame(MdbProtocol.buildFrame(MdbProtocol.RSP_END_SESSION))
                despacharMain { listener.onSessionComplete() }
            }
        }
    }

    // ─── Ações do app → VMC ─────────────────────────────────────────────────

    private fun iniciarSessao() {
        executor.submit {
            val (high, low) = MdbProtocol.centavosParaBytes(MdbProtocol.FUNDS_UNLIMITED)
            val frame = MdbProtocol.buildFrame(MdbProtocol.RSP_BEGIN_SESSION, high, low)
            enviarFrame(frame)
            mudarEstado(MdbState.SessionIdle)
            Log.i(TAG, "BEGIN SESSION enviado para Jetinno")
        }
    }

    /**
     * Aprova a venda: envia VEND APPROVED com o valor solicitado.
     * Chamar após o pagamento ser aprovado.
     *
     * @param valorCentavos Valor aprovado (deve ser igual ao do VEND REQUEST)
     */
    fun aprovarVenda(valorCentavos: Int) {
        executor.submit {
            val (high, low) = MdbProtocol.centavosParaBytes(valorCentavos)
            val frame = MdbProtocol.buildFrame(MdbProtocol.RSP_VEND_APPROVED, high, low)
            enviarFrame(frame)
            Log.i(TAG, "VEND APPROVED enviado: ${valorCentavos}c")
            mudarEstado(MdbState.SessionIdle)
        }
    }

    /**
     * Nega a venda: envia VEND DENIED.
     * Chamar após o pagamento ser recusado ou cancelado.
     */
    fun negarVenda() {
        executor.submit {
            val frame = MdbProtocol.buildFrame(MdbProtocol.RSP_VEND_DENIED)
            enviarFrame(frame)
            Log.i(TAG, "VEND DENIED enviado")
            mudarEstado(MdbState.SessionIdle)
        }
    }

    // ─── Simulação (sem hardware) ────────────────────────────────────────────

    /**
     * Simula um VEND REQUEST da Jetinno para testes sem hardware MDB.
     * Útil para validar o fluxo de pagamento no laboratório.
     *
     * @param valorCentavos Valor simulado em centavos
     * @param itemNumber    Número do item simulado
     */
    fun simularVendRequest(valorCentavos: Int = 550, itemNumber: Int = 1) {
        Log.i(TAG, "SIMULAÇÃO: VEND REQUEST valor=${valorCentavos}c item=$itemNumber")
        mudarEstado(MdbState.Enabled)
        mainHandler.postDelayed({
            mudarEstado(MdbState.SessionIdle)
            mainHandler.postDelayed({
                mudarEstado(MdbState.Vend(valorCentavos, itemNumber))
                despacharMain { listener.onVendRequest(valorCentavos, itemNumber) }
            }, 300)
        }, 200)
    }

    // ─── Envio de frames ─────────────────────────────────────────────────────

    private fun enviarAck() {
        enviarFrame(byteArrayOf(MdbProtocol.RSP_ACK.toByte()))
    }

    private fun enviarReaderConfigData() {
        val (maxHigh, maxLow) = MdbProtocol.centavosParaBytes(99999)
        val (minHigh, minLow) = MdbProtocol.centavosParaBytes(50)
        val frame = MdbProtocol.buildFrame(
            MdbProtocol.RSP_READER_CONFIG_DATA,
            MdbProtocol.FEATURE_LEVEL,
            (MdbProtocol.COUNTRY_CODE shr 8).and(0xFF),
            MdbProtocol.COUNTRY_CODE.and(0xFF),
            MdbProtocol.SCALE_FACTOR,
            MdbProtocol.DECIMAL_PLACES,
            MdbProtocol.MAX_RESP_TIME,
            MdbProtocol.MISC_OPTIONS,
            maxHigh, maxLow,
            minHigh, minLow
        )
        enviarFrame(frame)
    }

    private fun enviarFrame(frame: ByteArray) {
        if (!modoSimulacao) {
            try {
                serial.enviar(frame)
            } catch (e: IOException) {
                Log.e(TAG, "Erro ao enviar frame: ${e.message}")
                mudarEstado(MdbState.Erro("Falha serial: ${e.message}"))
            }
        } else {
            Log.d(TAG, "Simulação TX → ${frame.toHex()}")
        }
    }

    // ─── Utilitários ─────────────────────────────────────────────────────────

    private fun mudarEstado(novo: MdbState) {
        val anterior = estadoAtual
        estadoAtual = novo
        if (anterior != novo) {
            Log.i(TAG, "Estado MDB: $anterior → $novo")
            despacharMain { listener.onStateChanged(anterior, novo) }
        }
    }

    private fun notificarStatus() {
        despacharMain { listener.onStatusAtualizado(estadoAtual) }
    }

    private fun despacharMain(block: () -> Unit) {
        mainHandler.post(block)
    }

    val estadoMdb: MdbState get() = estadoAtual
    val emSimulacao: Boolean get() = modoSimulacao

    private fun ByteArray.toHex() = joinToString(" ") { "%02X".format(it) }
}
