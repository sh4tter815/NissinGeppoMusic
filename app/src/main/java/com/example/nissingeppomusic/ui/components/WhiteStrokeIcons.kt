package com.example.nissingeppomusic.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun WhiteHomeIcon(size: Dp = 22.dp, color: Color = Color.White) {
    Icon(
        imageVector = Icons.Default.Home,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size)
    )
}

@Composable
fun WhiteSearchIcon(size: Dp = 22.dp, color: Color = Color.White) {
    Icon(
        imageVector = Icons.Default.Search,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size)
    )
}

@Composable
fun WhiteGearIcon(size: Dp = 22.dp, color: Color = Color.White) {
    Icon(
        imageVector = Icons.Default.Settings,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size)
    )
}

@Composable
fun WhiteHamburgerIcon(size: Dp = 22.dp, color: Color = Color.White) {
    Icon(
        imageVector = Icons.Default.Menu,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size)
    )
}

@Composable
fun WhiteFolderIcon(size: Dp = 22.dp, color: Color = Color.White) {
    Icon(
        imageVector = Icons.Default.Folder,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size)
    )
}

@Composable
fun WhitePlusIcon(size: Dp = 22.dp, color: Color = Color.White) {
    Icon(
        imageVector = Icons.Default.Add,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size)
    )
}

@Composable
fun WhiteEditIcon(size: Dp = 16.dp, color: Color = Color(0xFF88C0FF)) {
    Icon(
        imageVector = Icons.Default.Edit,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size)
    )
}

@Composable
fun WhiteDeleteIcon(size: Dp = 16.dp, color: Color = Color(0xFFFF5252)) {
    Icon(
        imageVector = Icons.Default.Delete,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size)
    )
}

@Composable
fun WhiteCheckIcon(size: Dp = 16.dp, color: Color = Color(0xFF4CAF50)) {
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size)
    )
}

@Composable
fun WhiteCloseIcon(size: Dp = 16.dp, color: Color = Color.Gray) {
    Icon(
        imageVector = Icons.Default.Close,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size)
    )
}
