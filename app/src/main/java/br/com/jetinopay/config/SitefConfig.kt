package br.com.jetinopay.config

import android.content.Context
import br.com.jetinopay.BuildConfig

/**
 * Gerencia as credenciais SiTef / Fiserv persistidas em SharedPreferences.
 * Fallback para os valores padrão do BuildConfig quando não configurado.
 */
object SitefConfig {

    private const val PREFS_NAME = "sitef_config"
    private const val KEY_EMPRESA_ID = "empresa_id"
    private const val KEY_SITEF_IP = "sitef_ip"
    private const val KEY_CNPJ = "cnpj"
    private const val KEY_API_URL = "api_url"
    private const val KEY_CONFIGURADO = "configurado"

    fun getEmpresaId(context: Context): String =
        prefs(context).getString(KEY_EMPRESA_ID, BuildConfig.SITEF_EMPRESA_ID)
            ?: BuildConfig.SITEF_EMPRESA_ID

    fun getSitefIp(context: Context): String =
        prefs(context).getString(KEY_SITEF_IP, BuildConfig.SITEF_IP)
            ?: BuildConfig.SITEF_IP

    fun getCnpj(context: Context): String =
        prefs(context).getString(KEY_CNPJ, BuildConfig.ESTABELECIMENTO_CNPJ)
            ?: BuildConfig.ESTABELECIMENTO_CNPJ

    fun getApiUrl(context: Context): String =
        prefs(context).getString(KEY_API_URL, BuildConfig.JETINOPAY_BASE_URL)
            ?: BuildConfig.JETINOPAY_BASE_URL

    fun isConfigurado(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CONFIGURADO, false)

    fun salvar(
        context: Context,
        empresaId: String,
        sitefIp: String,
        cnpj: String,
        apiUrl: String
    ) {
        prefs(context).edit().apply {
            putString(KEY_EMPRESA_ID, empresaId.trim())
            putString(KEY_SITEF_IP, sitefIp.trim())
            putString(KEY_CNPJ, cnpj.trim().replace(Regex("[./-]"), ""))
            putString(KEY_API_URL, apiUrl.trim().let {
                if (it.endsWith("/")) it else "$it/"
            })
            putBoolean(KEY_CONFIGURADO, true)
            apply()
        }
    }

    fun limpar(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
