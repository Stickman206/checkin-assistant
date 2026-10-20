package com.checkin.assistant.di

import android.content.Context
import androidx.room.Room
import com.checkin.assistant.core.checkin.CookieStore
import com.checkin.assistant.data.AppDatabase
import com.checkin.assistant.data.CheckInDao
import com.checkin.assistant.data.PrefsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "checkin.db").build()

    @Provides @Singleton
    fun provideDao(db: AppDatabase): CheckInDao = db.checkInDao()

    @Provides @Singleton
    fun provideOkHttp(cookieStore: CookieStore): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(cookieStore) // 复用登录后 Cookie
            // 学校服务器在校园网外访问较慢，默认 10s 读超时容易误判失败
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
}
