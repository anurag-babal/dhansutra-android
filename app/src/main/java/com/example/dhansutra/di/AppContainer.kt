package com.example.dhansutra.di

import android.content.Context
import com.example.dhansutra.data.local.AppDatabase

/**
 * Manual dependency container. Holds app-wide singletons (database, repositories).
 * Populated progressively as layers are added.
 */
class AppContainer(context: Context) {
    val database: AppDatabase by lazy { AppDatabase.build(context) }
}
