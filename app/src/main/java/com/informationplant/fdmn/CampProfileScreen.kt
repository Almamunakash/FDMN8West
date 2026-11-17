package com.informationplant.fdmn

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CampBlue = ToneBlue.copy(light = androidx.compose.ui.graphics.Color(0xFF4F74E0), dark = androidx.compose.ui.graphics.Color(0xFF3354BD))

@Composable
fun CampProfileScreen(app: AppCtl) {
    Scaffold(
        topBar = { BrandTopBar("Camp Profile", "About the Camp, FDMN and Activities", onBack = { app.back() }) },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painterResource(R.drawable.logo_full), contentDescription = "Office of the Camp-in-Charge, Camp-8 West",
                modifier = Modifier.fillMaxWidth(),
            )
            WaveCard(CampBlue, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconTile(Icons.Filled.Info, CampBlue)
                    Spacer(Modifier.height(12.dp))
                    Text("Camp- 8 West", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CampBlue.dark)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Camp information will be added soon.",
                        fontSize = 16.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
