package br.com.jetinopay

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import br.com.jetinopay.config.SitefConfig
import br.com.jetinopay.databinding.ActivityMainBinding
import br.com.jetinopay.ui.ConfiguracoesActivity
import br.com.jetinopay.ui.PaymentActivity
import java.text.NumberFormat
import java.util.Locale

/**
 * Tela principal — modo idle do terminal.
 *
 * Em um setup real de vending machine, a cafeteira Jetinno envia o valor
 * do produto via MDB ou serial e esta tela recebe via Intent/BroadcastReceiver.
 * Para testes manuais, o botão de pagamento inicia o fluxo diretamente.
 *
 * Acesso às configurações: toque longo no título "JetinoPay".
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Mantém a tela sempre ligada (terminal de vending)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        configurarBotoes()
        atualizarStatusConfig()
    }

    override fun onResume() {
        super.onResume()
        atualizarStatusConfig()
    }

    private fun atualizarStatusConfig() {
        val configurado = SitefConfig.isConfigurado(this)
        if (!configurado) {
            binding.tvStatus.text = "⚠ Credenciais SiTef não configuradas. Toque longo no título para configurar."
        } else {
            val ip = SitefConfig.getSitefIp(this)
            binding.tvStatus.text = "Pronto — SiTef: $ip"
        }
    }

    private fun configurarBotoes() {
        // Toque longo no título abre as configurações (acesso do instalador/técnico)
        binding.tvTitulo.setOnLongClickListener {
            Toast.makeText(this, "Abrindo configurações...", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ConfiguracoesActivity::class.java))
            true
        }

        // Botão de teste — inicia pagamento com valor fixo para homologação
        binding.btnPagar.setOnClickListener {
            val valorStr = binding.etValor.text.toString()
            val valor = valorStr.toDoubleOrNull()

            if (valor == null || valor <= 0.0) {
                binding.tvStatus.text = "Informe um valor válido."
                return@setOnClickListener
            }

            iniciarPagamento(valor)
        }

        // Botão de crédito direto
        binding.btnCredito.setOnClickListener {
            val valor = binding.etValor.text.toString().toDoubleOrNull() ?: 5.50
            iniciarPagamento(valor, tipo = "credito")
        }

        // Botão de débito direto
        binding.btnDebito.setOnClickListener {
            val valor = binding.etValor.text.toString().toDoubleOrNull() ?: 5.50
            iniciarPagamento(valor, tipo = "debito")
        }
    }

    private fun iniciarPagamento(valor: Double, tipo: String = "credito") {
        if (!SitefConfig.isConfigurado(this)) {
            Toast.makeText(this, "Configure as credenciais SiTef primeiro (toque longo no título)", Toast.LENGTH_LONG).show()
            return
        }

        val valorFormatado = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
            .format(valor)
        binding.tvStatus.text = "Iniciando pagamento de $valorFormatado..."

        val intent = Intent(this, PaymentActivity::class.java).apply {
            putExtra(PaymentActivity.EXTRA_VALOR, valor)
            putExtra(PaymentActivity.EXTRA_TIPO, tipo)
        }
        startActivity(intent)
    }

    /**
     * Em produção: receber o sinal da cafeteira via MDB ou serial.
     * Exemplo: registrar BroadcastReceiver para intent da integração MDB.
     *
     *   val filter = IntentFilter("br.com.jetinopay.MDB_PRODUTO_SELECIONADO")
     *   registerReceiver(mdbReceiver, filter)
     *
     * O receiver extrai o valor e chama iniciarPagamento().
     */
}
