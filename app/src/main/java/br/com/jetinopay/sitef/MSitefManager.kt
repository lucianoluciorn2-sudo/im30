package br.com.jetinopay.sitef

import android.app.Activity
import android.content.Intent
import br.com.jetinopay.config.SitefConfig

/**
 * Gerenciador de integração com o m-SiTef (Fiserv).
 *
 * Utiliza o mecanismo de Intent Android para chamar o app m-SiTef instalado
 * no terminal PAX IM30. O m-SiTef assume o controle da tela, interage com
 * o portador do cartão e retorna o resultado via onActivityResult.
 *
 * Pré-requisito: o app m-SiTef deve estar instalado no terminal IM30
 * (fornecido pela Fiserv junto ao pacote de integração SmartPOS).
 * Portal: https://www.fiserv.com.br/
 */
object MSitefManager {

    const val REQUEST_CODE_PAYMENT = 1001
    const val REQUEST_CODE_CANCEL = 1002

    /** Ação Intent do m-SiTef (conforme manual da Fiserv — dev.softwareexpress.com.br/docs/m-sitef) */
    private const val MSITEF_ACTION =
        "br.com.softwareexpress.sitef.msitef.ACTIVITY_CLISITEF"

    /**
     * Modalidades de pagamento (campo `modalidade` do m-SiTef).
     * 0 = Apresenta menu ao portador (crédito/débito)
     * 2 = Débito direto
     * 3 = Crédito direto
     */
    object Modalidade {
        const val MENU = "0"
        const val DEBITO = "2"
        const val CREDITO = "3"
        const val PIX = "122"
    }

    /**
     * Códigos de retorno do m-SiTef.
     * 0 = Sucesso/Aprovado
     * Qualquer outro = erro ou negado (verificar mensagem)
     */
    object CodRetorno {
        const val APROVADO = "0"
        const val CANCELADO_OPERADOR = "-1"
        const val CANCELADO_PORTADOR = "-2"
    }

    /**
     * Inicia uma transação de pagamento via m-SiTef.
     *
     * @param activity    Activity que receberá o resultado em onActivityResult
     * @param valorCentavos   Valor em centavos (ex: R$ 5,50 → 550)
     * @param modalidade  Modalidade de pagamento (@see Modalidade)
     * @param numeroCupom Número sequencial do cupom/pedido (gerado pela JetinoPay API)
     * @param dataFiscal  Data no formato AAAAMMDD
     * @param horaFiscal  Hora no formato HHMMSS
     */
    fun iniciarPagamento(
        activity: Activity,
        valorCentavos: Long,
        modalidade: String = Modalidade.MENU,
        numeroCupom: String,
        dataFiscal: String,
        horaFiscal: String
    ) {
        val intent = Intent(MSITEF_ACTION).apply {
            putExtra("empresaSitef", SitefConfig.getEmpresaId(activity))
            putExtra("enderecoSitef", SitefConfig.getSitefIp(activity))
            putExtra("CNPJ_CPF", SitefConfig.getCnpj(activity))
            putExtra("modalidade", modalidade)
            putExtra("valor", valorCentavos.toString())
            putExtra("cupomFiscal", numeroCupom)
            putExtra("dataFiscal", dataFiscal)
            putExtra("horaFiscal", horaFiscal)
            putExtra("operador", "JETINOPAY")
        }

        activity.startActivityForResult(intent, REQUEST_CODE_PAYMENT)
    }

    /**
     * Inicia o cancelamento de uma transação aprovada.
     *
     * @param nsuSitef   NSU do SiTef da transação a cancelar
     * @param dataFiscal Data da transação original (AAAAMMDD)
     */
    fun iniciarCancelamento(
        activity: Activity,
        nsuSitef: String,
        dataFiscal: String,
        horaFiscal: String
    ) {
        val intent = Intent(MSITEF_ACTION).apply {
            putExtra("empresaSitef", SitefConfig.getEmpresaId(activity))
            putExtra("enderecoSitef", SitefConfig.getSitefIp(activity))
            putExtra("CNPJ_CPF", SitefConfig.getCnpj(activity))
            putExtra("modalidade", "200") // Cancelamento
            putExtra("valor", "0")
            putExtra("cupomFiscal", nsuSitef)
            putExtra("dataFiscal", dataFiscal)
            putExtra("horaFiscal", horaFiscal)
            putExtra("nsuCancelamento", nsuSitef)
        }

        activity.startActivityForResult(intent, REQUEST_CODE_CANCEL)
    }

    /**
     * Extrai os dados relevantes do Intent de retorno do m-SiTef.
     */
    fun parsearRetorno(data: Intent?): SitefResult {
        if (data == null) return SitefResult.erro("Sem dados de retorno do m-SiTef")

        val codRet = data.getStringExtra("codRet") ?: "-99"
        val mensagem = data.getStringExtra("mensagem") ?: "Sem mensagem"
        val nsuHost = data.getStringExtra("nsuHost") ?: ""
        val nsuSitef = data.getStringExtra("nsuSitef") ?: ""
        val codAutorizacao = data.getStringExtra("codAutorizacao") ?: ""
        val viaCliente = data.getStringExtra("viaCliente") ?: ""
        val viaEstabelecimento = data.getStringExtra("viaEstabelecimento") ?: ""
        val nomePortador = data.getStringExtra("nomePortador") ?: ""
        val binCartao = data.getStringExtra("binCartao") ?: ""

        return SitefResult(
            aprovado = codRet == CodRetorno.APROVADO,
            codRetorno = codRet,
            mensagem = mensagem,
            nsuHost = nsuHost,
            nsuSitef = nsuSitef,
            codAutorizacao = codAutorizacao,
            viaCliente = viaCliente,
            viaEstabelecimento = viaEstabelecimento,
            nomePortador = nomePortador,
            binCartao = binCartao
        )
    }
}

data class SitefResult(
    val aprovado: Boolean,
    val codRetorno: String,
    val mensagem: String,
    val nsuHost: String = "",
    val nsuSitef: String = "",
    val codAutorizacao: String = "",
    val viaCliente: String = "",
    val viaEstabelecimento: String = "",
    val nomePortador: String = "",
    val binCartao: String = ""
) {
    companion object {
        fun erro(mensagem: String) = SitefResult(
            aprovado = false,
            codRetorno = "-99",
            mensagem = mensagem
        )
    }
}
