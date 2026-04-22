package br.com.jetinopay.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.com.jetinopay.databinding.ActivityPaymentBinding
import br.com.jetinopay.network.JetinoPayApi
import br.com.jetinopay.network.models.CreatePaymentRequest
import br.com.jetinopay.network.models.WebhookRequest
import br.com.jetinopay.sitef.MSitefManager
import br.com.jetinopay.sitef.SitefResult
import br.com.jetinopay.utils.PrinterHelper
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tela de pagamento — orquestra o fluxo completo:
 *
 *  1. Cria transação na JetinoPay API (status PENDENTE)
 *  2. Chama o m-SiTef via Intent (terminal assume controle)
 *  3. Recebe resultado em onActivityResult
 *  4. Notifica a JetinoPay API via webhook (APROVADO ou NEGADO)
 *  5. Imprime comprovante
 *  6. Exibe resultado ao usuário
 */
class PaymentActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VALOR = "extra_valor"
        const val EXTRA_TIPO = "extra_tipo"
    }

    private lateinit var binding: ActivityPaymentBinding
    private lateinit var printer: PrinterHelper
    private var transacaoId: String? = null
    private var valor: Double = 0.0
    private var tipo: String = "credito"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        printer = PrinterHelper(this).also { it.conectar() }

        valor = intent.getDoubleExtra(EXTRA_VALOR, 0.0)
        tipo = intent.getStringExtra(EXTRA_TIPO) ?: "credito"

        val valorFormatado = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(valor)
        binding.tvValor.text = valorFormatado

        binding.btnCancelar.setOnClickListener { finish() }

        iniciarFluxoPagamento()
    }

    private fun iniciarFluxoPagamento() {
        setEstado(Estado.CRIANDO)

        lifecycleScope.launch {
            try {
                // 1. Cria transação na JetinoPay API
                val response = JetinoPayApi.getService()
                    .criarPagamento(CreatePaymentRequest(valor, tipo))

                if (!response.isSuccessful || response.body() == null) {
                    setEstado(Estado.ERRO, "Falha ao registrar transação na API.")
                    return@launch
                }

                transacaoId = response.body()!!.transacao.id
                setEstado(Estado.AGUARDANDO_CARTAO)

                // 2. Chama o m-SiTef
                val modalidade = when (tipo) {
                    "debito" -> MSitefManager.Modalidade.DEBITO
                    "pix" -> MSitefManager.Modalidade.PIX
                    else -> MSitefManager.Modalidade.CREDITO
                }

                val agora = Date()
                val dataFiscal = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(agora)
                val horaFiscal = SimpleDateFormat("HHmmss", Locale.getDefault()).format(agora)
                val valorCentavos = (valor * 100).toLong()

                MSitefManager.iniciarPagamento(
                    activity = this@PaymentActivity,
                    valorCentavos = valorCentavos,
                    modalidade = modalidade,
                    numeroCupom = transacaoId!!.takeLast(8),
                    dataFiscal = dataFiscal,
                    horaFiscal = horaFiscal
                )

            } catch (e: Exception) {
                setEstado(Estado.ERRO, "Erro de conexão: ${e.message}")
            }
        }
    }

    @Deprecated("Needed for m-SiTef Intent result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != MSitefManager.REQUEST_CODE_PAYMENT) return

        val resultado = MSitefManager.parsearRetorno(data)
        processarResultadoSitef(resultado)
    }

    private fun processarResultadoSitef(resultado: SitefResult) {
        val id = transacaoId ?: run {
            setEstado(Estado.ERRO, "ID de transação perdido.")
            return
        }

        lifecycleScope.launch {
            try {
                setEstado(if (resultado.aprovado) Estado.APROVANDO else Estado.NEGANDO)

                // 3. Notifica a JetinoPay API com o resultado real do terminal
                val statusApi = if (resultado.aprovado) "APROVADO" else "NEGADO"
                val mensagemApi = buildString {
                    append(resultado.mensagem)
                    if (resultado.nsuHost.isNotEmpty()) append(" | NSU: ${resultado.nsuHost}")
                    if (resultado.codAutorizacao.isNotEmpty()) append(" | Aut: ${resultado.codAutorizacao}")
                }

                JetinoPayApi.getService().enviarWebhook(
                    WebhookRequest(id = id, status = statusApi, mensagem = mensagemApi)
                )

                // 4. Imprime comprovante (estabelecimento)
                val agora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                val valorFormatado = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(valor)

                if (resultado.aprovado) {
                    printer.imprimirComprovante(
                        valor = valorFormatado,
                        tipo = tipo.uppercase(),
                        status = "APROVADO",
                        nsu = resultado.nsuHost,
                        codAutorizacao = resultado.codAutorizacao,
                        bandeira = resultado.binCartao,
                        modoEntrada = "CHIP/CONTACTLESS",
                        dataHora = agora,
                        viaCliente = false
                    )
                    printer.imprimirComprovante(
                        valor = valorFormatado,
                        tipo = tipo.uppercase(),
                        status = "APROVADO",
                        nsu = resultado.nsuHost,
                        codAutorizacao = resultado.codAutorizacao,
                        bandeira = resultado.binCartao,
                        modoEntrada = "CHIP/CONTACTLESS",
                        dataHora = agora,
                        viaCliente = true
                    )
                }

                // 5. Exibe resultado final
                setEstado(
                    if (resultado.aprovado) Estado.APROVADO else Estado.NEGADO,
                    resultado.mensagem
                )

            } catch (e: Exception) {
                setEstado(Estado.ERRO, "Erro ao finalizar transação: ${e.message}")
            }
        }
    }

    private fun setEstado(estado: Estado, mensagem: String? = null) {
        runOnUiThread {
            binding.progressBar.visibility =
                if (estado.loading) View.VISIBLE else View.GONE

            binding.tvStatus.text = mensagem ?: estado.label

            binding.ivStatus.setImageResource(
                when (estado) {
                    Estado.APROVADO -> android.R.drawable.ic_dialog_info
                    Estado.NEGADO, Estado.ERRO -> android.R.drawable.ic_dialog_alert
                    else -> android.R.drawable.ic_menu_rotate
                }
            )
            binding.ivStatus.visibility =
                if (estado == Estado.APROVADO || estado == Estado.NEGADO || estado == Estado.ERRO)
                    View.VISIBLE else View.GONE

            binding.btnCancelar.isEnabled = !estado.loading

            // Fecha a tela automaticamente após resultado final
            if (estado == Estado.APROVADO || estado == Estado.NEGADO) {
                binding.root.postDelayed({ finish() }, 3000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        printer.desconectar()
    }

    enum class Estado(val label: String, val loading: Boolean) {
        CRIANDO("Registrando transação...", true),
        AGUARDANDO_CARTAO("Aproxime ou insira o cartão", false),
        APROVANDO("Aprovando...", true),
        NEGANDO("Processando resultado...", true),
        APROVADO("Pagamento aprovado!", false),
        NEGADO("Pagamento não autorizado.", false),
        ERRO("Erro no pagamento.", false)
    }
}
