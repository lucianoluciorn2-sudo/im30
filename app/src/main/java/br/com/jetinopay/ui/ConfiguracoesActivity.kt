package br.com.jetinopay.ui

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import br.com.jetinopay.config.SitefConfig
import br.com.jetinopay.databinding.ActivityConfiguracoesBinding

/**
 * Tela de configuração das credenciais SiTef / Fiserv.
 *
 * Acessada pelo técnico/instalador via toque longo no logo da MainActivity.
 * As credenciais são persistidas em SharedPreferences e lidas pelo MSitefManager.
 */
class ConfiguracoesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfiguracoesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfiguracoesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = "Configurações SiTef"
            setDisplayHomeAsUpEnabled(true)
        }

        carregarValoresSalvos()
        configurarBotoes()
    }

    private fun carregarValoresSalvos() {
        binding.etEmpresaId.setText(SitefConfig.getEmpresaId(this))
        binding.etSitefIp.setText(SitefConfig.getSitefIp(this))
        binding.etCnpj.setText(SitefConfig.getCnpj(this))
        binding.etApiUrl.setText(SitefConfig.getApiUrl(this))

        if (SitefConfig.isConfigurado(this)) {
            binding.tvStatusConfig.text = "✓ Configuração salva"
        } else {
            binding.tvStatusConfig.text = "Usando valores padrão (não configurado)"
        }
    }

    private fun configurarBotoes() {
        binding.btnSalvar.setOnClickListener {
            salvarConfiguracoes()
        }

        binding.btnLimpar.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Restaurar padrões")
                .setMessage("Deseja apagar as configurações salvas e voltar para os valores padrão?")
                .setPositiveButton("Restaurar") { _, _ ->
                    SitefConfig.limpar(this)
                    carregarValoresSalvos()
                    Toast.makeText(this, "Configurações restauradas", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        binding.btnTestarConexao.setOnClickListener {
            testarConexao()
        }
    }

    private fun salvarConfiguracoes() {
        val empresaId = binding.etEmpresaId.text.toString().trim()
        val sitefIp = binding.etSitefIp.text.toString().trim()
        val cnpj = binding.etCnpj.text.toString().trim()
        val apiUrl = binding.etApiUrl.text.toString().trim()

        if (empresaId.isEmpty()) {
            binding.tilEmpresaId.error = "Campo obrigatório"
            return
        }
        if (sitefIp.isEmpty()) {
            binding.tilSitefIp.error = "Campo obrigatório"
            return
        }
        if (cnpj.isEmpty() || cnpj.replace(Regex("[./-]"), "").length != 14) {
            binding.tilCnpj.error = "CNPJ inválido (14 dígitos)"
            return
        }
        if (apiUrl.isEmpty()) {
            binding.tilApiUrl.error = "Campo obrigatório"
            return
        }

        binding.tilEmpresaId.error = null
        binding.tilSitefIp.error = null
        binding.tilCnpj.error = null
        binding.tilApiUrl.error = null

        SitefConfig.salvar(this, empresaId, sitefIp, cnpj, apiUrl)

        binding.tvStatusConfig.text = "✓ Configuração salva com sucesso"
        Toast.makeText(this, "Configurações salvas!", Toast.LENGTH_LONG).show()
    }

    private fun testarConexao() {
        val ip = binding.etSitefIp.text.toString().trim()
        if (ip.isEmpty()) {
            Toast.makeText(this, "Informe o IP do servidor SiTef", Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvStatusConfig.text = "Testando conexão com $ip..."
        binding.btnTestarConexao.isEnabled = false

        Thread {
            val conectado = try {
                val socket = java.net.Socket()
                socket.connect(
                    java.net.InetSocketAddress(ip.split(":")[0], 4096),
                    3000
                )
                socket.close()
                true
            } catch (e: Exception) {
                false
            }

            runOnUiThread {
                binding.btnTestarConexao.isEnabled = true
                if (conectado) {
                    binding.tvStatusConfig.text = "✓ Servidor SiTef acessível em $ip:4096"
                    Toast.makeText(this, "Conexão OK!", Toast.LENGTH_SHORT).show()
                } else {
                    binding.tvStatusConfig.text = "✗ Servidor SiTef inacessível em $ip:4096"
                    Toast.makeText(this, "Sem conexão com o servidor SiTef", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
