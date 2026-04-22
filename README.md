# JetinoPay — APK Android para PAX IM30

App Android para o terminal de pagamento PAX IM30 integrado ao SiTef (Software Express).

---

## Arquitetura

```
Cafeteira Jetinno (MDB)
         ↓
   PAX IM30 [este APK]
         ↓ TCP/IP (porta 4096)
   Servidor SiTef (Software Express)
         ↓
   Adquirentes: Cielo / Rede / Stone / Getnet
         ↕ webhook
   JetinoPay API (Node.js)
```

---

## Pré-requisitos

### Da Software Express (solicitar mediante contrato):
- `msitef.aar` — biblioteca m-SiTef para Android
- `smartpos-lib.aar` — biblioteca SmartPOS (Software Express)
- Manual de integração SmartPOS
- Credenciais SiTef:
  - IP do servidor SiTef
  - Código da empresa (`empresaId`)
  - CNPJ do estabelecimento
  - Terminal ID

### Da PAX (via PAXSTORE Developer Center):
- `NeptuneLiteApi.jar` — SDK nativo do hardware PAX (leitores, impressora)
- Conta no PAXSTORE para publicação do APK

---

## Configuração

### 1. Adicionar os SDKs

Coloque os arquivos na pasta `app/libs/`:
```
app/libs/
  msitef.aar
  smartpos-lib.aar
  NeptuneLiteApi.jar
```

Descomente a linha no `app/build.gradle.kts`:
```kotlin
implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
```

### 2. Configurar endereços

Edite `app/build.gradle.kts`:
```kotlin
buildConfigField("String", "JETINOPAY_BASE_URL", "\"http://SEU_IP_SERVIDOR:3000/\"")
buildConfigField("String", "SITEF_EMPRESA_ID", "\"CODIGO_DA_EMPRESA\"")
buildConfigField("String", "SITEF_IP", "\"IP_SERVIDOR_SITEF\"")
buildConfigField("String", "ESTABELECIMENTO_CNPJ", "\"CNPJ_SEM_MASCARA\"")
```

### 3. Atualizar hosts.xml

Edite `app/src/main/assets/hosts.xml` com os IPs/domínios do seu ambiente:
```xml
<hosts>
    <host>SEU_IP_SERVIDOR_JETINOPAY</host>
    <host>SEU_IP_SERVIDOR_SITEF</host>
</hosts>
```

---

## Fluxo de pagamento

1. **MainActivity** exibe a tela idle com o valor do produto
2. Ao confirmar pagamento, abre **PaymentActivity**
3. `PaymentActivity` registra a transação na **JetinoPay API** (status `PENDENTE`)
4. **MSitefManager** chama o m-SiTef via Intent Android
5. O m-SiTef assume a tela e interage com o portador do cartão
6. Retorna o resultado via `onActivityResult`
7. `PaymentActivity` notifica a **JetinoPay API** via webhook (`APROVADO` ou `NEGADO`)
8. **PrinterHelper** imprime o comprovante (via/estabelecimento e via/cliente)
9. Exibe resultado e fecha automaticamente em 3 segundos

---

## Estrutura do projeto

```
app/src/main/java/br/com/jetinopay/
  JetinoPayApp.kt              — Application, inicializa dependências
  MainActivity.kt              — Tela idle / seleção de produto
  ui/
    PaymentActivity.kt         — Orquestra fluxo de pagamento
    ReceiptActivity.kt         — Exibe comprovante pós-pagamento
  network/
    JetinoPayApi.kt            — Cliente Retrofit → JetinoPay Node.js API
    models/PaymentModels.kt    — Data classes (request/response)
  sitef/
    MSitefManager.kt           — Integração m-SiTef via Intent
  utils/
    PrinterHelper.kt           — Impressão de comprovante PAX
```

---

## Build e publicação

```bash
# Build de debug (para testes)
./gradlew assembleDebug

# Build de release (para PAXSTORE)
./gradlew assembleRelease
```

O APK gerado deve ser assinado e publicado via **PAXSTORE** para instalação remota nos terminais IM30.

---

## Integração com MDB (cafeteira Jetinno)

Em produção, a cafeteira envia o valor do produto selecionado via protocolo MDB.
O IM30 no modo MDB Slave recebe esse sinal. Para acionar o pagamento automaticamente:

1. Registrar um `BroadcastReceiver` em `MainActivity` para o evento MDB
2. Extrair o valor do produto do Intent recebido
3. Chamar `iniciarPagamento(valor, tipo)` automaticamente

Consulte o manual do MDB Master Port do IM30 e o SDK PAX NeptuneLite para a implementação do listener MDB.

---

## Homologação SiTef

Toda nova integração com o m-SiTef requer **homologação** junto à Fiserv antes de ir para produção:

1. Desenvolva o app usando as credenciais de **homologação** fornecidas pela Fiserv
2. Execute o checklist de testes obrigatórios (aprovação, negação, cancelamento, timeout)
3. Solicite a certificação de produção à Fiserv
4. Substitua as credenciais de homologação pelas de produção
5. Publique via PAXSTORE

---

## Variáveis de ambiente relevantes (JetinoPay API)

| Variável | Descrição |
|---|---|
| `FISERV_URL` | URL do servidor SiTef Fiserv (quando integração server-side) |
| `FISERV_TOKEN` | Token de autenticação SiTef Fiserv |
| `IM30_HOST` | IP do terminal (para integração direta via POSLink) |
| `IM30_PORT` | Porta POSLink (padrão: 10009) |
