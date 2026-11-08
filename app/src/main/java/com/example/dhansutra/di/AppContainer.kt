package com.example.dhansutra.di

import android.content.Context

/**
 * Manual dependency container. Holds app-wide singletons (database, repositories).
 * Populated progressively as layers are added.
 */
class AppContainer(@Suppress("unused") private val context: Context)
