package com.nanugil.app.ui.settlement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 정산하기 전에 내 차 차종을 고르는 화면. 고른 차종은 정산 화면의 모든 차 기본값이 된다. */
@Composable
fun VehicleTypeScreen(onBack: () -> Unit, onSelect: (VehicleType) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 돌아가기") }
            Text("차종 선택", style = MaterialTheme.typography.headlineSmall)
        }
        TypeGroup("연료차", listOf(VehicleType.DIESEL, VehicleType.GASOLINE, VehicleType.PREMIUM_GASOLINE), onSelect)
        TypeGroup("전기차", listOf(VehicleType.ELECTRIC, VehicleType.HYBRID), onSelect)
    }
}

@Composable
private fun TypeGroup(title: String, types: List<VehicleType>, onSelect: (VehicleType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            types.forEach { type ->
                Button(onClick = { onSelect(type) }, modifier = Modifier.width(200.dp).height(72.dp)) {
                    Text(type.label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun VehicleTypeScreenPreview() {
    MaterialTheme { VehicleTypeScreen(onBack = {}, onSelect = {}) }
}
