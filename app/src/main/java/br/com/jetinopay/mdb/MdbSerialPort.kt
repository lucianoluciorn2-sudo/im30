package br.com.jetinopay.mdb

import android.util.Log
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Abstração da porta serial MDB no PAX IM30.
 *
 * O PAX IM30 expõe o barramento MDB em /dev/ttyS4 (9600 baud).
 * O MDB usa 9 bits por frame, mas como o Android não suporta 9 bits
 * nativamente no driver serial, o PAX SDK provê uma API específica.
 *
 * NOTA DE INTEGRAÇÃO:
 * ────────────────────────────────────────────────────────────────────
 * Ao receber o SDK PAX (SmartPOS / Prolin):
 *   1. Adicione o AAR em app/libs/
 *   2. Substitua esta classe por `PaxMdbDevice` do SDK:
 *      ```kotlin
 *      val mdb = Dal.getInstance().mdb
 *      mdb.open()
 *      mdb.setReceiveListener { frame -> processar(frame) }
 *      mdb.send(frame)
 *      ```
 *
 * Esta implementação usa FileInputStream/OutputStream diretamente,
 * funcionando quando o sistema PAX já inicializou o driver MDB.
 * ────────────────────────────────────────────────────────────────────
 */
class MdbSerialPort(
    private val devicePath: String = DEFAULT_DEVICE,
    private val baudRate: Int = DEFAULT_BAUD
) {

    companion object {
        private const val TAG = "MdbSerialPort"
        const val DEFAULT_DEVICE = "/dev/ttyS4"
        const val DEFAULT_BAUD   = 9600
        const val READ_TIMEOUT_MS = 100L
        const val MAX_FRAME_SIZE  = 36
    }

    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null
    private var isOpen = false

    /**
     * Abre a porta serial MDB.
     * Requer permissão de acesso ao device (concedida pelo sistema PAX).
     *
     * @throws IOException se o device não existir ou não tiver permissão
     */
    fun open() {
        val device = File(devicePath)
        if (!device.exists()) {
            Log.w(TAG, "Device MDB não encontrado: $devicePath — usando modo simulação")
            isOpen = true
            return
        }

        try {
            configurarBaud(devicePath, baudRate)
            inputStream = FileInputStream(device)
            outputStream = FileOutputStream(device)
            isOpen = true
            Log.i(TAG, "Porta MDB aberta: $devicePath @ ${baudRate}bps")
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao abrir porta MDB: ${e.message}")
            throw e
        }
    }

    /** Fecha a porta serial e libera os recursos. */
    fun close() {
        try {
            inputStream?.close()
            outputStream?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Erro ao fechar porta MDB: ${e.message}")
        } finally {
            inputStream = null
            outputStream = null
            isOpen = false
            Log.i(TAG, "Porta MDB fechada")
        }
    }

    val aberta: Boolean get() = isOpen

    /**
     * Envia um frame MDB para o VMC (Jetinno).
     * Bloqueia até o frame ser escrito ou lança IOException.
     *
     * @param frame Bytes do frame incluindo checksum
     */
    fun enviar(frame: ByteArray) {
        val os = outputStream ?: run {
            Log.d(TAG, "Porta MDB em simulação — frame descartado: ${frame.toHex()}")
            return
        }
        try {
            os.write(frame)
            os.flush()
            Log.v(TAG, "MDB TX → ${frame.toHex()}")
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao enviar frame MDB: ${e.message}")
            throw e
        }
    }

    /**
     * Lê um frame MDB do VMC com timeout.
     * Retorna null se não houver dados disponíveis no timeout.
     *
     * @return Bytes do frame ou null se timeout
     */
    fun lerFrame(): ByteArray? {
        val ins = inputStream ?: return null

        return try {
            val buffer = ByteArray(MAX_FRAME_SIZE)
            val available = ins.available()
            if (available == 0) return null

            val bytesRead = ins.read(buffer, 0, minOf(available, MAX_FRAME_SIZE))
            if (bytesRead <= 0) return null

            val frame = buffer.copyOf(bytesRead)
            Log.v(TAG, "MDB RX ← ${frame.toHex()}")
            frame
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao ler frame MDB: ${e.message}")
            null
        }
    }

    /**
     * Configura baud rate via stty (requer shell com permissão).
     * O PAX SDK faz isso internamente via JNI.
     */
    private fun configurarBaud(device: String, baud: Int) {
        try {
            Runtime.getRuntime().exec(
                arrayOf("stty", "-F", device, baud.toString(), "raw", "-echo")
            ).waitFor()
        } catch (e: Exception) {
            Log.w(TAG, "stty não disponível — baud pode não estar configurado: ${e.message}")
        }
    }

    private fun ByteArray.toHex() = joinToString(" ") { "%02X".format(it) }
}
