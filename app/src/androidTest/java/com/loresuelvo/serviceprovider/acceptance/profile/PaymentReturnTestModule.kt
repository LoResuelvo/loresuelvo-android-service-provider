package com.loresuelvo.serviceprovider.acceptance.profile

import android.content.Context
import com.loresuelvo.serviceprovider.di.PaymentAccountModule
import com.loresuelvo.serviceprovider.platform.paymentaccount.PaymentAccountBrowserLauncher
import com.loresuelvo.serviceprovider.platform.paymentaccount.PaymentAccountConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

class TestPaymentAccountBrowserLauncher : PaymentAccountBrowserLauncher {
    var launchCount = 0
    var lastUrl: String? = null

    override fun launch(context: Context, url: String): Boolean {
        launchCount += 1
        lastUrl = url
        return true
    }
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [PaymentAccountModule::class],
)
object PaymentReturnTestModule {
    @Provides
    @Singleton
    fun providePaymentAccountConfig(): PaymentAccountConfig = PaymentAccountConfig(
        returnHost = "return.example.test",
    )

    @Provides
    @Singleton
    fun provideTestBrowserLauncher(): TestPaymentAccountBrowserLauncher =
        TestPaymentAccountBrowserLauncher()

    @Provides
    @Singleton
    fun provideBrowserLauncher(
        launcher: TestPaymentAccountBrowserLauncher,
    ): PaymentAccountBrowserLauncher = launcher
}
