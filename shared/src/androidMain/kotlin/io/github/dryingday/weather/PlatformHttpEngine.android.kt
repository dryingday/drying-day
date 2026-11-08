package io.github.dryingday.weather

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android

actual fun platformHttpEngine(): HttpClientEngine = Android.create()
