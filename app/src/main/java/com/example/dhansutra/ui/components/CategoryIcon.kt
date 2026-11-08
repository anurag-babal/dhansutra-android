package com.example.dhansutra.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** Maps the persisted `iconName` of a category to a Material icon. Unknown names fall back to a generic icon. */
object CategoryIcons {
    val all: Map<String, ImageVector> = linkedMapOf(
        "home" to Icons.Rounded.Home,
        "shopping_cart" to Icons.Rounded.ShoppingCart,
        "bolt" to Icons.Rounded.Bolt,
        "restaurant" to Icons.Rounded.Restaurant,
        "shopping_bag" to Icons.Rounded.ShoppingBag,
        "directions_car" to Icons.Rounded.DirectionsCar,
        "health" to Icons.Rounded.LocalHospital,
        "school" to Icons.Rounded.School,
        "movie" to Icons.Rounded.Movie,
        "flight" to Icons.Rounded.Flight,
        "phone" to Icons.Rounded.PhoneAndroid,
        "savings" to Icons.Rounded.Savings,
        "pets" to Icons.Rounded.Pets,
        "fitness" to Icons.Rounded.FitnessCenter,
        "receipt" to Icons.Rounded.Receipt,
        "trending_up" to Icons.Rounded.TrendingUp,
        "account_balance" to Icons.Rounded.AccountBalance,
        "handshake" to Icons.Rounded.Handshake,
        "category" to Icons.Rounded.Category,
    )

    fun get(iconName: String): ImageVector = all[iconName] ?: Icons.Rounded.Category
}

@Composable
fun CategoryIcon(iconName: String, modifier: Modifier = Modifier) {
    Icon(CategoryIcons.get(iconName), contentDescription = null, modifier = modifier)
}
