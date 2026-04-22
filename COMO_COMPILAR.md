# Como compilar e instalar o JetinoPay no PAX IM30

## Versão atual: TESTE MDB (sem TEF)

Esta versão testa a comunicação MDB com a Jetinno sem usar o SiTef/TEF.

---

## 1. Pré-requisitos

- [Android Studio](https://developer.android.com/studio) instalado
- JDK 17 (incluído no Android Studio)
- Android SDK 35 (instala automaticamente)
- ADB configurado (incluso no Android Studio)

---

## 2. Compilar o APK

```bash
# No terminal, dentro da pasta im30-app/
./gradlew assembleDebug
```

Ou pelo Android Studio:
- **Build → Build Bundle(s)/APK(s) → Build APK(s)**

**APK gerado em:**
```
im30-app/app/build/outputs/apk/debug/app-debug.apk
```

---

## 3. Instalar no IM30 via USB

### Ativar modo desenvolvedor no IM30:
1. Vá em **Configurações → Sobre o dispositivo**
2. Toque **7 vezes** em "Número de compilação"
3. Volte às Configurações → **Opções do desenvolvedor**
4. Ative **"Depuração USB"**

### Instalar:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Se já instalado (atualizar):
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 4. O que a versão TESTE faz

Ao abrir o app no IM30:

| Situação | O que acontece |
|----------|---------------|
| Com MDB conectado | App aguarda VEND REQUEST da Jetinno automaticamente |
| Sem MDB (teste) | Use o botão "Simular Produto Selecionado" |
| Produto selecionado | Aparece valor + botões APROVAR / NEGAR |
| APROVAR | Envia VEND APPROVED → Jetinno dispensa o produto |
| NEGAR | Envia VEND DENIED → Jetinno não dispensa |

**Sem nenhuma integração TEF/SiTef** — pagamento real não é processado.

---

## 5. Conexão MDB

O app usa a porta `/dev/ttyS4` do PAX IM30 (9600 baud).

A Jetinno deve estar conectada ao IM30 via cabo MDB (conector de 6 pinos).

Se o hardware MDB não for encontrado, o badge **SIM** aparece na toolbar
e o app entra em modo simulação — você pode usar o botão de simulação
para testar o fluxo sem o cabo.

---

## 6. Próximos passos (versão produção)

Quando quiser habilitar o TEF real:
1. Edite `AndroidManifest.xml`
2. Troque o launcher de `.ui.TesteMdbActivity` para `.MainActivity`
3. Configure as credenciais Fiserv via toque longo no título
4. Instale o app m-SiTef (fornecido pela Fiserv)
