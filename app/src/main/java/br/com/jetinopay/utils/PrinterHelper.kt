package br.com.jetinopay.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder

/**
 * Auxiliar de impressão para o terminal PAX IM30.
 *
 * Utiliza o serviço de impressão do SmartPOS (Software Express).
 * Requer a biblioteca smartpos-lib.aar no projeto.
 *
 * Nota: as chamadas ao serviço de impressão são feitas via reflexão
 * para evitar dependência de compilação. Após adicionar o .aar,
 * substitua por chamadas diretas ao IPrinterService.
 */
class PrinterHelper(private val context: Context) {

    private var printerService: IBinder? = null
    private var connected = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            printerService = service
            connected = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            printerService = null
            connected = false
        }
    }

    fun conectar() {
        val intent = Intent("SmartPOSService.registerPrinterService")
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun desconectar() {
        if (connected) context.unbindService(connection)
    }

    /**
     * Imprime o comprovante de pagamento.
     * Substitua as chamadas por reflexão pelo SDK real após receber os .aar.
     */
    fun imprimirComprovante(
        valor: String,
        tipo: String,
        status: String,
        nsu: String,
        codAutorizacao: String,
        bandeira: String,
        modoEntrada: String,
        dataHora: String,
        viaCliente: Boolean = false
    ) {
        // TODO: substituir por chamada real ao IPrinterService após adicionar .aar
        // Exemplo com SDK real:
        //   printerService?.printText("JetinoPay\n")
        //   printerService?.printText("Valor: R$ $valor\n")
        //   printerService?.feedPaper(5)
        //   printerService?.start()

        val via = if (viaCliente) "VIA CLIENTE" else "VIA ESTABELECIMENTO"

        android.util.Log.d("PrinterHelper", buildString {
            appendLine("=== COMPROVANTE JETINOPAY ===")
            appendLine(via)
            appendLine("Valor: R$ $valor")
            appendLine("Tipo: $tipo")
            appendLine("Status: $status")
            appendLine("Bandeira: $bandeira")
            appendLine("Modo: $modoEntrada")
            appendLine("NSU: $nsu")
            appendLine("Autorização: $codAutorizacao")
            appendLine("Data/Hora: $dataHora")
            appendLine("=============================")
        })
    }
}
