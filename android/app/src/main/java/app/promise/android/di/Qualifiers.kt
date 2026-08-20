package app.promise.android.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PublicApi

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthedApi

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PublicHttp
