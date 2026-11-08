package com.example.dhansutra.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.dhansutra.DhanSutraApp
import com.example.dhansutra.di.AppContainer

/** Creates (or retrieves) a ViewModel whose dependencies come from the app's [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as DhanSutraApp).container
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
