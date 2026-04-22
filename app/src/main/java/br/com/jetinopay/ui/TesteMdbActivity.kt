package br.com.jetinopay.ui

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import br.com.jetinopay.R
import br.com.jetinopay.mdb.MdbListener
import br.com.jetinopay.mdb.MdbManager
import br.com.jetinopay.mdb.MdbState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tela de teste MDB — sem integração TEF.
 *
 * Permite testar a comunicação com a cafeteira Jetinno via MDB:
 *   1. Aguarda VEND REQUEST da Jetinno
 *   2. Exibe o valor do produto na tela
 *   3. Operador pressiona APROVAR ou NEGAR
 *   4. App envia VEND APPROVED / VEND DENIED para a Jetinno
 *
 * Use esta Activity para validar o hardware MDB antes de habilitar o TEF.
 *
 * Acesso: configurado como launcher no AndroidManifest para builds de teste.
 */
class TesteMdbActivity : AppCompatActivity(), MdbListener {

    private lateinit var mdbManager: MdbManager

    // Views
    private lateinit var tvEstadoMdb: TextView
    private lateinit var tvSubtituloMdb: TextView
    private lateinit var tvSimulacao: TextView
    private lateinit var viewStatusDot: android.view.View
    private lateinit var layoutIdle: android.view.View
    private lateinit var layoutVenda: android.view.View
    private lateinit var layoutResultado: android.view.View
    private lateinit var tvValorVenda: TextView
    private lateinit var tvItemVenda: TextView
    private lateinit var tvResultadoIcone: TextView
    private lateinit var tvResultadoTitulo: TextView
    private lateinit var tvResultadoValor: TextView
    private lateinit var btnAprovar: Button
    private lateinit var btnNegar: Button
    private lateinit var btnNovaVenda: Button
    private lateinit var btnSimular: Button
    private lateinit var etValorSimulado: EditText
    private lateinit var tvLog: TextView
    private lateinit var scrollLog: ScrollView

    private var vendaAtual: Pair<Int, Int>? = null // (valorCentavos, itemNumber)
    private val brl = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teste_mdb)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        bindViews()
        configurarBotoes()

        mdbManager = MdbManager(listener = this)
        mdbManager.iniciar()

        log("JetinoPay iniciado — modo TESTE (sem TEF)")
        log("Aguardando MDB da Jetinno...")
    }

    override fun onDestroy() {
        super.onDestroy()
        mdbManager.parar()
    }

    // ─── MdbListener ────────────────────────────────────────────────────────

    override fun onReaderEnabled() {
        log("✅ Reader ENABLED pela Jetinno")
        atualizarDot(Color.parseColor("#4CAF50"))
        tvEstadoMdb.text = "Reader Habilitado"
        tvSubtituloMdb.text = "Selecione um produto na Jetinno"
        mostrarIdle("Aguardando seleção de produto...")
    }

    override fun onReaderDisabled() {
        log("🔴 Reader DISABLED pela Jetinno")
        atualizarDot(Color.parseColor("#9E9E9E"))
        tvEstadoMdb.text = "Reader Desabilitado"
        tvSubtituloMdb.text = "Jetinno desabilitou o terminal"
        mostrarIdle("Terminal desabilitado pela Jetinno")
    }

    override fun onVendRequest(valorCentavos: Int, itemNumber: Int) {
        vendaAtual = Pair(valorCentavos, itemNumber)
        val valorStr = brl.format(valorCentavos / 100.0)
        log("💰 VEND REQUEST: $valorStr — Item #$itemNumber")
        atualizarDot(Color.parseColor("#FF9800"))
        tvEstadoMdb.text = "Venda Pendente"
        tvSubtituloMdb.text = "Aguardando aprovação do operador"
        mostrarVenda(valorCentavos, itemNumber)
    }

    override fun onSessionCancelled() {
        log("❌ Sessão CANCELADA pela Jetinno")
        vendaAtual = null
        atualizarDot(Color.parseColor("#9E9E9E"))
        tvEstadoMdb.text = "Sessão Cancelada"
        tvSubtituloMdb.text = "Aguardando próxima seleção"
        mostrarIdle("Sessão cancelada pela Jetinno")
    }

    override fun onSessionComplete() {
        log("✅ SESSION COMPLETE — ciclo encerrado")
        atualizarDot(Color.parseColor("#4CAF50"))
        tvEstadoMdb.text = "Reader Habilitado"
        tvSubtituloMdb.text = "Pronto para próxima venda"
    }

    override fun onStateChanged(anterior: MdbState, novo: MdbState) {
        log("📊 Estado: $anterior → $novo")

        if (novo is MdbState.Inactive) {
            atualizarDot(Color.parseColor("#9E9E9E"))
            tvEstadoMdb.text = "Inicializando"
            tvSubtituloMdb.text = "Aguardando RESET do VMC"
        }

        tvSimulacao.visibility = if (mdbManager.emSimulacao)
            android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun onErro(motivo: String) {
        log("⚠ ERRO MDB: $motivo")
        atualizarDot(Color.parseColor("#F44336"))
        tvEstadoMdb.text = "Erro MDB"
        tvSubtituloMdb.text = motivo
    }

    override fun onStatusAtualizado(estado: MdbState) {
        // Status periódico — não loga para não poluir
    }

    // ─── Ações dos botões ────────────────────────────────────────────────────

    private fun configurarBotoes() {
        btnAprovar.setOnClickListener {
            val venda = vendaAtual ?: return@setOnClickListener
            val (valorCentavos, _) = venda
            log("👆 Operador: APROVAR ${brl.format(valorCentavos / 100.0)}")
            mdbManager.aprovarVenda(valorCentavos)
            mostrarResultado(aprovado = true, valorCentavos = valorCentavos)
            vendaAtual = null
        }

        btnNegar.setOnClickListener {
            val venda = vendaAtual ?: return@setOnClickListener
            val (valorCentavos, _) = venda
            log("👆 Operador: NEGAR ${brl.format(valorCentavos / 100.0)}")
            mdbManager.negarVenda()
            mostrarResultado(aprovado = false, valorCentavos = valorCentavos)
            vendaAtual = null
        }

        btnNovaVenda.setOnClickListener {
            mostrarIdle()
        }

        btnSimular.setOnClickListener {
            val valorStr = etValorSimulado.text.toString()
            val valor = (valorStr.toDoubleOrNull() ?: 5.50)
            val valorCentavos = (valor * 100).toInt()
            log("🔧 Simulando VEND REQUEST: ${brl.format(valor)}")
            mdbManager.simularVendRequest(valorCentavos = valorCentavos, itemNumber = 1)
        }
    }

    // ─── Navegação entre telas ───────────────────────────────────────────────

    private fun mostrarIdle(subtitulo: String? = null) {
        layoutIdle.visibility = android.view.View.VISIBLE
        layoutVenda.visibility = android.view.View.GONE
        layoutResultado.visibility = android.view.View.GONE
        subtitulo?.let {
            (layoutIdle.findViewWithTag<TextView>("tvIdleSubtitulo")
                ?: findViewById(R.id.tvIdleSubtitulo))?.text = it
        }
    }

    private fun mostrarVenda(valorCentavos: Int, itemNumber: Int) {
        layoutIdle.visibility = android.view.View.GONE
        layoutVenda.visibility = android.view.View.VISIBLE
        layoutResultado.visibility = android.view.View.GONE
        tvValorVenda.text = brl.format(valorCentavos / 100.0)
        tvItemVenda.text = "Item #$itemNumber"
    }

    private fun mostrarResultado(aprovado: Boolean, valorCentavos: Int) {
        layoutIdle.visibility = android.view.View.GONE
        layoutVenda.visibility = android.view.View.GONE
        layoutResultado.visibility = android.view.View.VISIBLE

        if (aprovado) {
            tvResultadoIcone.text = "✓"
            tvResultadoIcone.setTextColor(Color.parseColor("#2E7D32"))
            tvResultadoTitulo.text = "APROVADO"
            tvResultadoTitulo.setTextColor(Color.parseColor("#2E7D32"))
            atualizarDot(Color.parseColor("#4CAF50"))
        } else {
            tvResultadoIcone.text = "✗"
            tvResultadoIcone.setTextColor(Color.parseColor("#C62828"))
            tvResultadoTitulo.text = "NEGADO"
            tvResultadoTitulo.setTextColor(Color.parseColor("#C62828"))
            atualizarDot(Color.parseColor("#F44336"))
        }

        tvResultadoValor.text = brl.format(valorCentavos / 100.0)
        tvEstadoMdb.text = if (aprovado) "Venda Aprovada" else "Venda Negada"
        tvSubtituloMdb.text = "Resposta enviada para Jetinno"
    }

    // ─── Utilitários ─────────────────────────────────────────────────────────

    private fun atualizarDot(cor: Int) {
        viewStatusDot.background?.setTint(cor)
    }

    private fun log(mensagem: String) {
        val hora = sdf.format(Date())
        val linha = "[$hora] $mensagem\n"
        tvLog.append(linha)
        scrollLog.post { scrollLog.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun bindViews() {
        tvEstadoMdb = findViewById(R.id.tvEstadoMdb)
        tvSubtituloMdb = findViewById(R.id.tvSubtituloMdb)
        tvSimulacao = findViewById(R.id.tvSimulacao)
        viewStatusDot = findViewById(R.id.viewStatusDot)
        layoutIdle = findViewById(R.id.layoutIdle)
        layoutVenda = findViewById(R.id.layoutVenda)
        layoutResultado = findViewById(R.id.layoutResultado)
        tvValorVenda = findViewById(R.id.tvValorVenda)
        tvItemVenda = findViewById(R.id.tvItemVenda)
        tvResultadoIcone = findViewById(R.id.tvResultadoIcone)
        tvResultadoTitulo = findViewById(R.id.tvResultadoTitulo)
        tvResultadoValor = findViewById(R.id.tvResultadoValor)
        btnAprovar = findViewById(R.id.btnAprovar)
        btnNegar = findViewById(R.id.btnNegar)
        btnNovaVenda = findViewById(R.id.btnNovaVenda)
        btnSimular = findViewById(R.id.btnSimular)
        etValorSimulado = findViewById(R.id.etValorSimulado)
        tvLog = findViewById(R.id.tvLog)
        scrollLog = findViewById(R.id.scrollLog)
    }
}
