package br.com.jetinopay.mdb

/**
 * Interface de callbacks do MdbManager para a camada de aplicação.
 *
 * Todos os callbacks são chamados na thread principal (Main/UI).
 * A Activity/ViewModel implementa esta interface para reagir aos
 * eventos MDB da Jetinno e iniciar os fluxos de pagamento.
 */
interface MdbListener {

    /**
     * Chamado quando a Jetinno (VMC) habilita o reader e está pronta
     * para receber pagamentos. A UI deve exibir tela de "Aguardando produto".
     */
    fun onReaderEnabled()

    /**
     * Chamado quando a Jetinno desabilita o reader (ex: sem estoque,
     * fora de serviço). A UI deve exibir tela de "Terminal desabilitado".
     */
    fun onReaderDisabled()

    /**
     * Chamado quando a Jetinno solicita venda de um produto.
     * A Activity deve iniciar o pagamento via m-SiTef com o valor recebido.
     *
     * @param valorCentavos Valor em centavos (ex: 550 = R$ 5,50)
     * @param itemNumber    Número do produto selecionado na Jetinno
     */
    fun onVendRequest(valorCentavos: Int, itemNumber: Int)

    /**
     * Chamado quando a Jetinno cancela a sessão (ex: tempo esgotado,
     * usuário desistiu). O pagamento em andamento deve ser cancelado.
     */
    fun onSessionCancelled()

    /**
     * Chamado quando a sessão MDB é encerrada normalmente após o pagamento.
     * A UI retorna para o estado idle.
     */
    fun onSessionComplete()

    /**
     * Chamado quando o estado MDB muda. Útil para debug e logs.
     *
     * @param anterior Estado anterior
     * @param novo     Novo estado
     */
    fun onStateChanged(anterior: MdbState, novo: MdbState)

    /**
     * Chamado quando ocorre um erro de comunicação MDB.
     *
     * @param motivo Descrição do erro
     */
    fun onErro(motivo: String)

    /**
     * Chamado periodicamente com o estado atual para atualizar a UI
     * de status/diagnóstico.
     *
     * @param estado Estado MDB atual
     */
    fun onStatusAtualizado(estado: MdbState)
}
