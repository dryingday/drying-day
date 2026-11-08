package io.github.dryingday.weather

import io.ktor.client.engine.HttpClientEngine

/** The HTTP engine for the platform the app is running on, to hand to a weather source. */
expect fun platformHttpEngine(): HttpClientEngine
