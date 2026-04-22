package br.com.jetinopay

import android.app.Application
import br.com.jetinopay.network.JetinoPayApi

class JetinoPayApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Inicializa o cliente HTTP ao subir o app
        JetinoPayApi.init(BuildConfig.JETINOPAY_BASE_URL)
    }
}
