package com.loresuelvo.serviceprovider.di

import com.loresuelvo.serviceprovider.platform.paymentaccount.ExternalPaymentAccountBrowserLauncher
import com.loresuelvo.serviceprovider.platform.paymentaccount.PaymentAccountBrowserLauncher
import com.loresuelvo.serviceprovider.platform.paymentaccount.PaymentAccountConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PaymentAccountModule {

    @Binds
    @Singleton
    abstract fun bindPaymentAccountBrowserLauncher(
        impl: ExternalPaymentAccountBrowserLauncher,
    ): PaymentAccountBrowserLauncher

    companion object {
        @Provides
        @Singleton
        fun providePaymentAccountConfig(): PaymentAccountConfig = PaymentAccountConfig()
    }
}
