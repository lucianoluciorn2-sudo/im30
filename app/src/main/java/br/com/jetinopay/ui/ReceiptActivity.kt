package br.com.jetinopay.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import br.com.jetinopay.databinding.ActivityReceiptBinding

/**
 * Tela de comprovante pós-pagamento.
 * Exibe os dados da transação aprovada e fecha automaticamente.
 */
class ReceiptActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReceiptBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiptBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val aprovado = intent.getBooleanExtra("aprovado", false)
        val valor = intent.getStringExtra("valor") ?: ""
        val nsu = intent.getStringExtra("nsu") ?: ""
        val codAutorizacao = intent.getStringExtra("codAutorizacao") ?: ""
        val bandeira = intent.getStringExtra("bandeira") ?: ""
        val mensagem = intent.getStringExtra("mensagem") ?: ""

        binding.tvTitulo.text = if (aprovado) "Pagamento Aprovado" else "Pagamento Recusado"
        binding.tvValor.text = valor
        binding.tvNsu.text = "NSU: $nsu"
        binding.tvAutorizacao.text = "Autorização: $codAutorizacao"
        binding.tvBandeira.text = bandeira
        binding.tvMensagem.text = mensagem

        // Fecha automaticamente após 5 segundos
        binding.root.postDelayed({ finish() }, 5000)

        binding.btnFechar.setOnClickListener { finish() }
    }
}
